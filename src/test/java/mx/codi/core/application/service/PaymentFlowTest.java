package mx.codi.core.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import mx.codi.core.domain.PaymentGatewayException;
import mx.codi.core.domain.model.Event;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.model.OrderStatus;
import mx.codi.core.domain.model.PaymentAlert;
import mx.codi.core.domain.model.ProcessedWebhook;
import mx.codi.core.domain.model.TicketType;
import mx.codi.core.domain.port.in.ChargedOrder;
import mx.codi.core.domain.port.in.CreateOrderUseCase.CreateOrderCommand;
import mx.codi.core.domain.port.in.ProcessPaymentWebhookUseCase.WebhookResult;
import mx.codi.core.domain.port.out.EventRepositoryPort;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import mx.codi.core.domain.port.out.PaymentAlertRepositoryPort;
import mx.codi.core.domain.port.out.PaymentGatewayPort;
import mx.codi.core.domain.port.out.ProcessedWebhookRepositoryPort;
import mx.codi.core.domain.port.out.TicketNotificationPort;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentFlowTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-14T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

    private final MemoryOrders orders = new MemoryOrders();
    private final MemoryTickets tickets = new MemoryTickets();
    private final MemoryEvents events = new MemoryEvents();
    private final MemoryWebhooks webhooks = new MemoryWebhooks();
    private final MemoryAlerts alerts = new MemoryAlerts();
    private final ScriptedNotifications notifications = new ScriptedNotifications();
    private final ScriptedGateway gateway = new ScriptedGateway();
    private ProcessPaymentWebhookService processor;
    private ReconcileExpiredPaymentService recovery;
    private TicketType ticket;

    @BeforeEach
    void setUp() {
        ticket = new TicketType(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "General",
                new BigDecimal("250.00"),
                new BigDecimal("180.00"),
                NOW.plusDays(1),
                10,
                0,
                0);
        tickets.save(ticket);
        events.save(new Event(
                ticket.eventId(),
                "Social de salsa",
                "Foro",
                NOW.plusDays(1),
                "social",
                "Noche de salsa",
                List.of(ticket)));
        SettleOrderService settle = new SettleOrderService(orders, tickets, events, notifications, alerts, CLOCK);
        processor = new ProcessPaymentWebhookService(new ObjectMapper(), orders, webhooks, alerts, settle, CLOCK);
        recovery = new ReconcileExpiredPaymentService(gateway, alerts, webhooks, settle);
    }

    @Test
    void chargeUsesUnitPriceTimesQuantityOnTheQrAndTheLink() {
        ChargedOrder charged = create(2);
        Order order = charged.order();

        assertThat(gateway.lastQr.amount()).isEqualByComparingTo("360.00");
        assertThat(gateway.lastQr.expirationSeconds()).isEqualTo(600);
        assertThat(gateway.lastQr.numRef()).isEqualTo(order.refNumber());
        assertThat(gateway.lastQr.metadata())
                .containsEntry("orderId", order.id().toString())
                .containsEntry("quantity", "2")
                .containsEntry("customerName", "Ana López")
                .containsEntry("customerPhone", "525512345678");
        assertThat(gateway.lastLink.amount()).isEqualByComparingTo("360.00");
        assertThat(gateway.lastLink.clientReferenceId()).isEqualTo(order.id().toString());
        assertThat(charged.qrString()).isEqualTo("CODI|360.00|" + order.refNumber());
        assertThat(charged.paymentLinkUrl()).isEqualTo("https://pay.example/link/" + order.id());
        assertThat(orders.findById(order.id()).orElseThrow().customerPhone()).isEqualTo("525512345678");
    }

    @Test
    void aPaidWebhookIssuesOneTicketAndADuplicateDoesNotSendItAgain() {
        Order order = create(2).order();

        WebhookResult first = processor.process("msg-1", codiBody(order, "txn-1", order.amount()));
        WebhookResult duplicate = processor.process("msg-1", codiBody(order, "txn-1", order.amount()));
        WebhookResult samePayment = processor.process("msg-2", codiBody(order, "txn-1", order.amount()));

        Order stored = orders.findById(order.id()).orElseThrow();
        assertThat(first.retryable()).isFalse();
        assertThat(duplicate.retryable()).isFalse();
        assertThat(samePayment.retryable()).isFalse();
        assertThat(stored.status()).isEqualTo(OrderStatus.TICKET_SENT);
        assertThat(stored.trackingKey()).isEqualTo("txn-1");
        assertThat(stored.accessCode()).hasSize(12);
        assertThat(stored.quantity()).isEqualTo(2);
        assertThat(notifications.sent).hasSize(1);
        assertThat(notifications.sent.getFirst().ticketQrPayload()).contains("\"quantity\":2");
        assertThat(notifications.sent.getFirst().customerName()).isEqualTo("Ana López");
        assertThat(tickets.findById(ticket.id()).orElseThrow().sold()).isEqualTo(2);
        assertThat(tickets.findById(ticket.id()).orElseThrow().reserved()).isZero();
    }

    @Test
    void amountMismatchExpiredLateAndDoublePaymentDoNotIssueASecondTicket() {
        Order pending = create(2).order();
        processor.process("msg-amount", codiBody(pending, "txn-bad", new BigDecimal("1.00")));
        assertThat(orders.findById(pending.id()).orElseThrow().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(alerts.reasons()).contains("amount_mismatch");
        assertThat(notifications.sent).isEmpty();

        processor.process("msg-ref", codiBody(pending, "txn-ref", pending.amount()).replace(
                "\"numRef\":" + pending.refNumber(), "\"numRef\":9999999"));
        assertThat(alerts.reasons()).contains("reference_mismatch");

        Order expired = orders.save(pending.expire());
        tickets.releaseReserved(ticket.id(), 2);
        processor.process("msg-expired", codiBody(expired, "txn-late", expired.amount()));
        assertThat(orders.findById(expired.id()).orElseThrow().status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(alerts.reasons()).contains("late_payment");

        Order late = create(1).order();
        late = orders.save(new Order(
                late.id(), late.refNumber(), late.eventId(), late.ticketTypeId(), late.quantity(),
                late.customerName(), late.customerPhone(), late.amount(), late.status(),
                late.codiQrId(), late.paymentLinkId(), late.paymentLinkUrl(), late.trackingKey(),
                NOW, late.accessCode(), late.checkedInAt(), late.createdAt(), late.paidAt()));
        processor.process("msg-hold", codiBody(late, "txn-hold", late.amount()));
        assertThat(orders.findById(late.id()).orElseThrow().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(alerts.reasons().stream().filter("late_payment"::equals).count()).isEqualTo(2);

        Order paid = create(1).order();
        processor.process("msg-qr", codiBody(paid, "txn-qr", paid.amount()));
        String linkBody = """
                {"type":"payment_link.status_updated","data":{"status":"paid","paymentLinkId":"%s","transactionId":"txn-link","transactions":[{"id":"txn-link","amount":"370.00","amountBreakdown":{"base":"%s","fee":"10.00"}}]}}
                """.formatted(paid.paymentLinkId(), paid.amount().toPlainString());
        processor.process("msg-link", linkBody);

        assertThat(notifications.sent).hasSize(1);
        assertThat(orders.findById(paid.id()).orElseThrow().status()).isEqualTo(OrderStatus.TICKET_SENT);
        assertThat(orders.findById(paid.id()).orElseThrow().trackingKey()).isEqualTo("txn-qr");
        assertThat(alerts.reasons()).contains("duplicate_payment");
    }

    @Test
    void aFailedBotLeavesTheOrderPaidAndTheRetrySendsTheTicketOnce() {
        Order order = create(2).order();
        notifications.failuresLeft = 1;

        WebhookResult failed = processor.process("msg-retry", codiBody(order, "txn-retry", order.amount()));
        Order parked = orders.findById(order.id()).orElseThrow();

        assertThat(failed.retryable()).isTrue();
        assertThat(parked.status()).isEqualTo(OrderStatus.PAID);
        assertThat(parked.accessCode()).hasSize(12);
        assertThat(webhooks.existsByWebhookId("msg-retry")).isFalse();
        assertThat(alerts.reasons()).contains("notification_failed");
        assertThat(notifications.sent).isEmpty();

        WebhookResult retried = processor.process("msg-retry", codiBody(order, "txn-retry", order.amount()));

        assertThat(retried.retryable()).isFalse();
        assertThat(orders.findById(order.id()).orElseThrow().status()).isEqualTo(OrderStatus.TICKET_SENT);
        assertThat(notifications.sent).hasSize(1);
        assertThat(tickets.findById(ticket.id()).orElseThrow().sold()).isEqualTo(2);
    }

    @Test
    void reconciliationSettlesAPaidHoldAndSkipsARecordedLatePayment() {
        Order lost = create(2).order();
        gateway.paid = new PaymentGatewayPort.CodiPayment("txn-rec", "PAID", new BigDecimal("370.00"), lost.amount());

        assertThat(recovery.trySettle(lost)).isTrue();
        assertThat(orders.findById(lost.id()).orElseThrow().status()).isEqualTo(OrderStatus.TICKET_SENT);
        assertThat(webhooks.existsByWebhookId("reconcile-txn-rec")).isTrue();

        Order blocked = create(1).order();
        alerts.save(new PaymentAlert(
                UUID.randomUUID(), blocked.id(), "msg-old", "late_payment",
                blocked.amount(), blocked.amount(), "txn-old", "already late", NOW));
        assertThat(recovery.trySettle(blocked)).isFalse();
        assertThat(orders.findById(blocked.id()).orElseThrow().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        gateway.fail = true;
        Order unreachable = create(1).order();
        assertThat(recovery.trySettle(unreachable)).isFalse();
        assertThat(orders.findById(unreachable.id()).orElseThrow().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    private ChargedOrder create(int quantity) {
        return new CreateOrderService(tickets, orders, CLOCK, new PaymentCharger(
                orders, gateway, "https://pay.example/success", "https://pay.example/back"))
                .create(new CreateOrderCommand(ticket.id(), quantity, "Ana López", "525512345678"));
    }

    private static String codiBody(Order order, String transactionId, BigDecimal amount) {
        return """
                {"type":"codi.qr.payment_received","amount":"%s","codiQrId":"%s","transactionId":"%s","numRef":%d}
                """.formatted(amount.toPlainString(), order.codiQrId(), transactionId, order.refNumber());
    }

    private static final class ScriptedGateway implements PaymentGatewayPort {
        private CodiQrRequest lastQr;
        private PaymentLinkRequest lastLink;
        private CodiPayment paid;
        private boolean fail;

        @Override
        public CodiQr createCodiQr(CodiQrRequest request) {
            lastQr = request;
            return new CodiQr("qr-" + request.numRef(), "CODI|" + request.amount().toPlainString() + "|" + request.numRef());
        }

        @Override
        public PaymentLink createPaymentLink(PaymentLinkRequest request) {
            lastLink = request;
            return new PaymentLink("link-" + request.clientReferenceId(), "https://pay.example/link/" + request.clientReferenceId());
        }

        @Override
        public List<CodiPayment> findCodiQrPayments(String codiQrId) {
            if (fail) {
                throw new PaymentGatewayException("down", null);
            }
            return paid == null ? List.of() : List.of(paid);
        }

        @Override
        public List<CodiPayment> findPaymentLinkPayments(String paymentLinkId) {
            return List.of();
        }
    }

    private static final class ScriptedNotifications implements TicketNotificationPort {
        private final List<TicketMessage> sent = new ArrayList<>();
        private int failuresLeft;

        @Override
        public void sendTicket(TicketMessage message) {
            if (failuresLeft > 0) {
                failuresLeft--;
                throw new IllegalStateException("bot down");
            }
            sent.add(message);
        }
    }

    private static final class MemoryOrders implements OrderRepositoryPort {
        private final ConcurrentHashMap<UUID, Order> byId = new ConcurrentHashMap<>();
        private final AtomicInteger nextRef = new AtomicInteger(1);

        @Override
        public Order save(Order order) {
            byId.put(order.id(), order);
            return order;
        }

        @Override
        public Optional<Order> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Order> findByRefNumber(int refNumber) {
            return byId.values().stream().filter(order -> order.refNumber() == refNumber).findFirst();
        }

        @Override
        public Optional<Order> findByAccessCode(String accessCode) {
            return byId.values().stream().filter(order -> accessCode.equals(order.accessCode())).findFirst();
        }

        @Override
        public Optional<Order> findByCodiQrId(String codiQrId) {
            return byId.values().stream().filter(order -> codiQrId.equals(order.codiQrId())).findFirst();
        }

        @Override
        public Optional<Order> findByPaymentLinkId(String paymentLinkId) {
            return byId.values().stream().filter(order -> paymentLinkId.equals(order.paymentLinkId())).findFirst();
        }

        @Override
        public List<Order> findPendingExpired(OffsetDateTime now) {
            return List.of();
        }

        @Override
        public List<Order> findAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public int nextRefNumber() {
            return nextRef.getAndIncrement();
        }
    }

    private static final class MemoryTickets implements TicketTypeRepositoryPort {
        private final ConcurrentHashMap<UUID, TicketType> byId = new ConcurrentHashMap<>();

        @Override
        public TicketType save(TicketType ticketType) {
            byId.put(ticketType.id(), ticketType);
            return ticketType;
        }

        @Override
        public Optional<TicketType> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<TicketType> findByEventId(UUID eventId) {
            return byId.values().stream().filter(ticket -> ticket.eventId().equals(eventId)).toList();
        }

        @Override
        public boolean tryReserve(UUID ticketTypeId, int quantity) {
            TicketType ticket = byId.get(ticketTypeId);
            if (ticket == null || ticket.available() < quantity) {
                return false;
            }
            byId.put(ticketTypeId, ticket.reserve(quantity));
            return true;
        }

        @Override
        public boolean releaseReserved(UUID ticketTypeId, int quantity) {
            TicketType ticket = byId.get(ticketTypeId);
            if (ticket == null || ticket.reserved() < quantity) {
                return false;
            }
            byId.put(ticketTypeId, ticket.release(quantity));
            return true;
        }

        @Override
        public boolean confirmSale(UUID ticketTypeId, int quantity) {
            TicketType ticket = byId.get(ticketTypeId);
            if (ticket == null || ticket.reserved() < quantity) {
                return false;
            }
            byId.put(ticketTypeId, new TicketType(
                    ticket.id(), ticket.eventId(), ticket.name(), ticket.regularPrice(), ticket.presalePrice(),
                    ticket.presaleEndsAt(), ticket.capacity(), ticket.sold() + quantity, ticket.reserved() - quantity));
            return true;
        }
    }

    private static final class MemoryEvents implements EventRepositoryPort {
        private final ConcurrentHashMap<UUID, Event> byId = new ConcurrentHashMap<>();

        @Override
        public Event save(Event event) {
            byId.put(event.id(), event);
            return event;
        }

        @Override
        public Optional<Event> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<java.time.LocalDate> findUpcomingDatesWithAvailability(java.time.LocalDate today, java.time.ZoneId zone) {
            return List.of();
        }

        @Override
        public List<Event> findByDate(java.time.LocalDate date, java.time.ZoneId zone) {
            return List.of();
        }

        @Override
        public List<Event> findAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public long count() {
            return byId.size();
        }
    }

    private static final class MemoryWebhooks implements ProcessedWebhookRepositoryPort {
        private final ConcurrentHashMap<String, ProcessedWebhook> byId = new ConcurrentHashMap<>();

        @Override
        public ProcessedWebhook save(ProcessedWebhook webhook) {
            byId.put(webhook.webhookId(), webhook);
            return webhook;
        }

        @Override
        public Optional<ProcessedWebhook> findByWebhookId(String webhookId) {
            return Optional.ofNullable(byId.get(webhookId));
        }

        @Override
        public boolean existsByWebhookId(String webhookId) {
            return byId.containsKey(webhookId);
        }
    }

    private static final class MemoryAlerts implements PaymentAlertRepositoryPort {
        private final List<PaymentAlert> stored = new CopyOnWriteArrayList<>();

        @Override
        public PaymentAlert save(PaymentAlert alert) {
            stored.add(alert);
            return alert;
        }

        @Override
        public List<PaymentAlert> findAll() {
            return List.copyOf(stored);
        }

        private List<String> reasons() {
            return stored.stream().map(PaymentAlert::reason).toList();
        }
    }
}
