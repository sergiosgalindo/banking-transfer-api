package mx.codi.core.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import mx.codi.core.domain.InvalidOrderTransitionException;
import mx.codi.core.domain.NotFoundException;
import mx.codi.core.domain.SoldOutException;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.model.OrderStatus;
import mx.codi.core.domain.model.TicketType;
import mx.codi.core.domain.port.in.ChargedOrder;
import mx.codi.core.domain.port.in.CheckInTicketUseCase.CheckInResult;
import mx.codi.core.domain.port.in.CreateOrderUseCase.CreateOrderCommand;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import mx.codi.core.domain.port.out.PaymentGatewayPort;
import mx.codi.core.domain.port.out.PaymentGatewayPort.CodiPayment;
import mx.codi.core.domain.port.out.PaymentGatewayPort.CodiQr;
import mx.codi.core.domain.port.out.PaymentGatewayPort.CodiQrRequest;
import mx.codi.core.domain.port.out.PaymentGatewayPort.PaymentLink;
import mx.codi.core.domain.port.out.PaymentGatewayPort.PaymentLinkRequest;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import org.junit.jupiter.api.Test;

class OrderLifecycleTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-14T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

    @Test
    void createLocksThePresaleTotalBeforeTakingTheLastSeats() {
        TicketType ticket = presaleTicket(2, 0, 0);
        InMemoryTickets tickets = new InMemoryTickets(ticket);
        InMemoryOrders orders = new InMemoryOrders();
        CreateOrderService service = new CreateOrderService(tickets, orders, CLOCK, charger(orders));

        ChargedOrder charged = service.create(command(ticket.id(), 2, "Ana López", "525512345678"));
        Order created = charged.order();

        assertThat(created.amount()).isEqualByComparingTo("360.00");
        assertThat(created.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(created.quantity()).isEqualTo(2);
        assertThat(created.customerName()).isEqualTo("Ana López");
        assertThat(created.customerPhone()).isEqualTo("525512345678");
        assertThat(created.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(created.codiQrId()).isEqualTo("qr-" + created.refNumber());
        assertThat(created.paymentLinkId()).isEqualTo("link-" + created.id());
        assertThat(charged.qrString()).isEqualTo("CODI|360.00|" + created.refNumber());
        assertThat(charged.paymentLinkUrl()).isEqualTo("https://pay.example/link/" + created.id());
        assertThat(created.accessCode()).isNull();
        assertThat(tickets.findById(ticket.id()).orElseThrow().reserved()).isEqualTo(2);
    }

    @Test
    void createRejectsAMissingTicketAndAQuantityTheCupoCannotCover() {
        InMemoryTickets tickets = new InMemoryTickets(presaleTicket(1, 0, 0));
        CreateOrderService service = new CreateOrderService(tickets, new InMemoryOrders(), CLOCK, charger(new InMemoryOrders()));

        assertThatThrownBy(() -> service.create(command(UUID.randomUUID(), 1, "Ana López", "525512345678")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.create(command(tickets.only().id(), 2, "Ana López", "525512345678")))
                .isInstanceOf(SoldOutException.class);
        assertThatThrownBy(() -> service.create(command(tickets.only().id(), 0, "Ana López", "525512345678")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(tickets.only().reserved()).isZero();
    }

    @Test
    void onlyOneBuyerGetsTheLastSeat() throws Exception {
        TicketType ticket = presaleTicket(1, 0, 0);
        InMemoryTickets tickets = new InMemoryTickets(ticket);
        InMemoryOrders orders = new InMemoryOrders();
        CreateOrderService service = new CreateOrderService(tickets, orders, CLOCK, charger(orders));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Order>> attempts = List.of(
                    pool.submit(() -> buy(service, start, ticket.id(), "Ana López", "525500000001")),
                    pool.submit(() -> buy(service, start, ticket.id(), "Luis Pérez", "525500000002")));
            start.countDown();

            int reserved = 0;
            int soldOut = 0;
            for (Future<Order> attempt : attempts) {
                try {
                    assertThat(attempt.get(5, TimeUnit.SECONDS).amount()).isEqualByComparingTo("180.00");
                    reserved++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(SoldOutException.class);
                    soldOut++;
                }
            }

            assertThat(reserved).isEqualTo(1);
            assertThat(soldOut).isEqualTo(1);
            assertThat(tickets.only().reserved()).isEqualTo(1);
            assertThat(orders.findAll()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aFailedSaveReleasesTheReservation() {
        TicketType ticket = presaleTicket(4, 0, 0);
        InMemoryTickets tickets = new InMemoryTickets(ticket);
        InMemoryOrders orders = new InMemoryOrders() {
            @Override
            public Order save(Order order) {
                throw new IllegalStateException("database unavailable");
            }
        };
        CreateOrderService service = new CreateOrderService(tickets, orders, CLOCK, charger(orders));

        assertThatThrownBy(() -> service.create(command(ticket.id(), 2, "Ana López", "525512345678")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        assertThat(tickets.only().reserved()).isZero();
    }

    @Test
    void expiringADueOrderReleasesItsSeatsOnce() {
        TicketType ticket = presaleTicket(5, 0, 3);
        InMemoryTickets tickets = new InMemoryTickets(ticket);
        InMemoryOrders orders = new InMemoryOrders();
        orders.save(order(ticket, 2, new BigDecimal("360.00"), OrderStatus.PENDING_PAYMENT, NOW.minusSeconds(1), null, null));
        orders.save(order(ticket, 1, new BigDecimal("180.00"), OrderStatus.PENDING_PAYMENT, NOW.plusSeconds(600), null, null));
        ExpireOrdersService service = new ExpireOrdersService(orders, tickets, CLOCK, order -> false);

        assertThat(service.expireDueOrders()).isEqualTo(1);
        assertThat(service.expireDueOrders()).isZero();
        assertThat(orders.findAll())
                .filteredOn(order -> order.quantity() == 2)
                .singleElement()
                .extracting(Order::status)
                .isEqualTo(OrderStatus.EXPIRED);
        assertThat(tickets.only().reserved()).isEqualTo(1);
    }

    @Test
    void renewRecomputesTheTotalAndKeepsASingleHold() {
        TicketType ticket = new TicketType(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "General",
                new BigDecimal("250.00"),
                new BigDecimal("180.00"),
                NOW,
                4,
                0,
                2);
        InMemoryTickets tickets = new InMemoryTickets(ticket);
        InMemoryOrders orders = new InMemoryOrders();
        Order original = orders.save(order(
                ticket, 2, new BigDecimal("360.00"), OrderStatus.PENDING_PAYMENT, NOW.plusSeconds(30), null, null));
        RenewOrderService service = new RenewOrderService(orders, tickets, CLOCK, charger(orders));

        Order renewed = service.renew(original.id()).order();

        assertThat(orders.findById(original.id()).orElseThrow().status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(renewed.id()).isNotEqualTo(original.id());
        assertThat(renewed.refNumber()).isNotEqualTo(original.refNumber());
        assertThat(renewed.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(renewed.amount()).isEqualByComparingTo("500.00");
        assertThat(renewed.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(tickets.only().reserved()).isEqualTo(2);
    }

    @Test
    void renewOfASoldOutOrderCreatesNothing() {
        TicketType ticket = presaleTicket(2, 2, 0);
        InMemoryTickets tickets = new InMemoryTickets(ticket);
        InMemoryOrders orders = new InMemoryOrders();
        Order expired = orders.save(order(
                ticket, 2, new BigDecimal("360.00"), OrderStatus.EXPIRED, NOW.minusMinutes(1), null, null));
        RenewOrderService service = new RenewOrderService(orders, tickets, CLOCK, charger(orders));

        assertThatThrownBy(() -> service.renew(expired.id())).isInstanceOf(SoldOutException.class);

        assertThat(orders.findAll()).singleElement().extracting(Order::status).isEqualTo(OrderStatus.EXPIRED);
        assertThat(tickets.only().reserved()).isZero();
    }

    @Test
    void paidOrdersCannotBeRenewedOrCancelled() {
        TicketType ticket = presaleTicket(4, 0, 2);
        InMemoryTickets tickets = new InMemoryTickets(ticket);
        InMemoryOrders orders = new InMemoryOrders();
        Order paid = orders.save(order(
                ticket, 2, new BigDecimal("360.00"), OrderStatus.PAID, NOW.plusMinutes(5), null, NOW));

        assertThatThrownBy(() -> new RenewOrderService(orders, tickets, CLOCK, charger(orders)).renew(paid.id()))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> new CancelOrderService(orders, tickets).cancel(paid.id()))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThat(tickets.only().reserved()).isEqualTo(2);
        assertThat(orders.findById(paid.id()).orElseThrow().status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void cancelReleasesTheReservedSeats() {
        TicketType ticket = presaleTicket(4, 0, 2);
        InMemoryTickets tickets = new InMemoryTickets(ticket);
        InMemoryOrders orders = new InMemoryOrders();
        Order pending = orders.save(order(
                ticket, 2, new BigDecimal("360.00"), OrderStatus.PENDING_PAYMENT, NOW.plusSeconds(600), null, null));

        Order cancelled = new CancelOrderService(orders, tickets).cancel(pending.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(tickets.only().reserved()).isZero();
    }

    @Test
    void checkInAdmitsOnceAndRejectsASecondScan() {
        MutableClock clock = new MutableClock(NOW.toInstant());
        TicketType ticket = presaleTicket(4, 2, 0);
        InMemoryOrders orders = new InMemoryOrders();
        Order paid = orders.save(order(
                ticket, 2, new BigDecimal("360.00"), OrderStatus.PAID, NOW.plusMinutes(5), "qr-ana", NOW));
        CheckInTicketService service = new CheckInTicketService(orders, clock);

        CheckInResult first = service.checkIn("qr-ana");
        clock.advanceSeconds(30);
        CheckInResult second = service.checkIn("qr-ana");

        assertThat(first.admitted()).isTrue();
        assertThat(first.reason()).isNull();
        assertThat(first.customerName()).isEqualTo("Ana López");
        assertThat(first.quantity()).isEqualTo(2);
        assertThat(first.checkedInAt()).isEqualTo(NOW);
        assertThat(second.admitted()).isFalse();
        assertThat(second.reason()).isEqualTo("already_checked_in");
        assertThat(second.customerName()).isEqualTo("Ana López");
        assertThat(second.customerPhone()).isEqualTo("525512345678");
        assertThat(second.quantity()).isEqualTo(2);
        assertThat(second.checkedInAt()).isEqualTo(NOW);
        assertThat(orders.findById(paid.id()).orElseThrow().checkedInAt()).isEqualTo(NOW);
    }

    @Test
    void checkInRejectsUnpaidAndUnknownCodes() {
        TicketType ticket = presaleTicket(4, 0, 2);
        InMemoryOrders orders = new InMemoryOrders();
        orders.save(order(
                ticket, 2, new BigDecimal("360.00"), OrderStatus.PENDING_PAYMENT, NOW.plusSeconds(600), "qr-pending", null));
        CheckInTicketService service = new CheckInTicketService(orders, CLOCK);

        CheckInResult unpaid = service.checkIn("qr-pending");
        CheckInResult missing = service.checkIn("missing");

        assertThat(unpaid.admitted()).isFalse();
        assertThat(unpaid.reason()).isEqualTo("not_paid");
        assertThat(unpaid.customerName()).isEqualTo("Ana López");
        assertThat(unpaid.quantity()).isEqualTo(2);
        assertThat(unpaid.checkedInAt()).isNull();
        assertThat(missing).isEqualTo(new CheckInResult(false, "not_found", null, null, null, null));
    }

    private static Order buy(
            CreateOrderService service,
            CountDownLatch start,
            UUID ticketTypeId,
            String name,
            String phone
    ) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        return service.create(command(ticketTypeId, 1, name, phone)).order();
    }

    private static PaymentCharger charger(InMemoryOrders orders) {
        PaymentGatewayPort payments = new PaymentGatewayPort() {
            @Override
            public CodiQr createCodiQr(CodiQrRequest request) {
                return new CodiQr("qr-" + request.numRef(), "CODI|" + request.amount().toPlainString() + "|" + request.numRef());
            }

            @Override
            public PaymentLink createPaymentLink(PaymentLinkRequest request) {
                return new PaymentLink("link-" + request.clientReferenceId(), "https://pay.example/link/" + request.clientReferenceId());
            }

            @Override
            public List<CodiPayment> findCodiQrPayments(String codiQrId) {
                return List.of();
            }

            @Override
            public List<CodiPayment> findPaymentLinkPayments(String paymentLinkId) {
                return List.of();
            }
        };
        return new PaymentCharger(orders, payments, "https://pay.example/success", "https://pay.example/back");
    }

    private static CreateOrderCommand command(UUID ticketTypeId, int quantity, String name, String phone) {
        return new CreateOrderCommand(ticketTypeId, quantity, name, phone);
    }

    private static TicketType presaleTicket(int capacity, int sold, int reserved) {
        return new TicketType(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "General",
                new BigDecimal("250.00"),
                new BigDecimal("180.00"),
                NOW.plusDays(1),
                capacity,
                sold,
                reserved);
    }

    private static Order order(
            TicketType ticket,
            int quantity,
            BigDecimal amount,
            OrderStatus status,
            OffsetDateTime expiresAt,
            String accessCode,
            OffsetDateTime paidAt
    ) {
        return new Order(
                UUID.randomUUID(),
                1000 + quantity,
                ticket.eventId(),
                ticket.id(),
                quantity,
                "Ana López",
                "525512345678",
                amount,
                status,
                null,
                null,
                null,
                null,
                expiresAt,
                accessCode,
                null,
                NOW.minusMinutes(5),
                paidAt);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static class InMemoryTickets implements TicketTypeRepositoryPort {

        private final ConcurrentHashMap<UUID, TicketType> byId = new ConcurrentHashMap<>();

        private InMemoryTickets(TicketType ticket) {
            byId.put(ticket.id(), ticket);
        }

        private TicketType only() {
            return byId.values().iterator().next();
        }

        @Override
        public synchronized TicketType save(TicketType ticketType) {
            byId.put(ticketType.id(), ticketType);
            return ticketType;
        }

        @Override
        public synchronized Optional<TicketType> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public synchronized List<TicketType> findByEventId(UUID eventId) {
            return byId.values().stream().filter(ticket -> ticket.eventId().equals(eventId)).toList();
        }

        @Override
        public synchronized boolean tryReserve(UUID ticketTypeId, int quantity) {
            TicketType ticket = byId.get(ticketTypeId);
            if (ticket == null || ticket.available() < quantity) {
                return false;
            }
            byId.put(ticketTypeId, ticket.reserve(quantity));
            return true;
        }

        @Override
        public synchronized boolean releaseReserved(UUID ticketTypeId, int quantity) {
            TicketType ticket = byId.get(ticketTypeId);
            if (ticket == null || ticket.reserved() < quantity) {
                return false;
            }
            byId.put(ticketTypeId, ticket.release(quantity));
            return true;
        }

        @Override
        public synchronized boolean confirmSale(UUID ticketTypeId, int quantity) {
            TicketType ticket = byId.get(ticketTypeId);
            if (ticket == null || ticket.reserved() < quantity) {
                return false;
            }
            byId.put(ticketTypeId, new TicketType(
                    ticket.id(),
                    ticket.eventId(),
                    ticket.name(),
                    ticket.regularPrice(),
                    ticket.presalePrice(),
                    ticket.presaleEndsAt(),
                    ticket.capacity(),
                    ticket.sold() + quantity,
                    ticket.reserved() - quantity));
            return true;
        }
    }

    private static class InMemoryOrders implements OrderRepositoryPort {

        private final ConcurrentHashMap<UUID, Order> byId = new ConcurrentHashMap<>();
        private final AtomicInteger nextRef = new AtomicInteger(1);

        @Override
        public synchronized Order save(Order order) {
            byId.put(order.id(), order);
            return order;
        }

        @Override
        public synchronized Optional<Order> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public synchronized Optional<Order> findByRefNumber(int refNumber) {
            return byId.values().stream().filter(order -> order.refNumber() == refNumber).findFirst();
        }

        @Override
        public synchronized Optional<Order> findByAccessCode(String accessCode) {
            return byId.values().stream().filter(order -> accessCode.equals(order.accessCode())).findFirst();
        }

        @Override
        public synchronized Optional<Order> findByCodiQrId(String codiQrId) {
            return byId.values().stream().filter(order -> codiQrId.equals(order.codiQrId())).findFirst();
        }

        @Override
        public synchronized Optional<Order> findByPaymentLinkId(String paymentLinkId) {
            return byId.values().stream().filter(order -> paymentLinkId.equals(order.paymentLinkId())).findFirst();
        }

        @Override
        public synchronized List<Order> findPendingExpired(OffsetDateTime now) {
            return byId.values().stream()
                    .filter(order -> order.status() == OrderStatus.PENDING_PAYMENT)
                    .filter(order -> order.expiresAt() != null && !order.expiresAt().isAfter(now))
                    .toList();
        }

        @Override
        public synchronized List<Order> findAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public synchronized int nextRefNumber() {
            return nextRef.getAndIncrement();
        }
    }
}
