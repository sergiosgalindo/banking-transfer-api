package mx.codi.core.application.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.codi.core.domain.NotFoundException;
import mx.codi.core.domain.SoldOutException;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.model.OrderStatus;
import mx.codi.core.domain.model.TicketType;
import mx.codi.core.domain.port.in.ChargedOrder;
import mx.codi.core.domain.port.in.CreateOrderUseCase;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {

    private final TicketTypeRepositoryPort ticketTypes;
    private final OrderRepositoryPort orders;
    private final Clock clock;
    private final PaymentCharger charger;

    public CreateOrderService(
            TicketTypeRepositoryPort ticketTypes,
            OrderRepositoryPort orders,
            Clock clock,
            PaymentCharger charger
    ) {
        this.ticketTypes = ticketTypes;
        this.orders = orders;
        this.clock = clock;
        this.charger = charger;
    }

    @Override
    public ChargedOrder create(CreateOrderCommand command) {
        if (command.quantity() < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        TicketType ticket = ticketTypes.findById(command.ticketTypeId())
                .orElseThrow(() -> new NotFoundException("ticket type not found"));
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (ticket.available() < command.quantity() || !ticketTypes.tryReserve(ticket.id(), command.quantity())) {
            throw new SoldOutException();
        }
        Order pending;
        try {
            pending = orders.save(pendingOrder(
                    UUID.randomUUID(),
                    orders.nextRefNumber(),
                    ticket,
                    command.quantity(),
                    command.customerName(),
                    command.customerPhone(),
                    ticket.total(now, command.quantity()),
                    now));
        } catch (RuntimeException exception) {
            ticketTypes.releaseReserved(ticket.id(), command.quantity());
            throw exception;
        }
        try {
            return charger.charge(pending);
        } catch (RuntimeException exception) {
            orders.save(pending.cancel());
            ticketTypes.releaseReserved(ticket.id(), command.quantity());
            throw exception;
        }
    }

    static Order pendingOrder(
            UUID id,
            int refNumber,
            TicketType ticket,
            int quantity,
            String customerName,
            String customerPhone,
            java.math.BigDecimal amount,
            OffsetDateTime now
    ) {
        return new Order(
                id,
                refNumber,
                ticket.eventId(),
                ticket.id(),
                quantity,
                customerName,
                customerPhone,
                amount,
                OrderStatus.PENDING_PAYMENT,
                null,
                null,
                null,
                null,
                now.plus(Order.PAYMENT_HOLD),
                null,
                null,
                now,
                null);
    }
}
