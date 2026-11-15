package mx.codi.core.application.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.codi.core.domain.InvalidOrderTransitionException;
import mx.codi.core.domain.NotFoundException;
import mx.codi.core.domain.SoldOutException;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.model.OrderStatus;
import mx.codi.core.domain.model.TicketType;
import mx.codi.core.domain.port.in.ChargedOrder;
import mx.codi.core.domain.port.in.RenewOrderUseCase;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RenewOrderService implements RenewOrderUseCase {

    private final OrderRepositoryPort orders;
    private final TicketTypeRepositoryPort ticketTypes;
    private final Clock clock;
    private final PaymentCharger charger;

    public RenewOrderService(
            OrderRepositoryPort orders,
            TicketTypeRepositoryPort ticketTypes,
            Clock clock,
            PaymentCharger charger
    ) {
        this.orders = orders;
        this.ticketTypes = ticketTypes;
        this.clock = clock;
        this.charger = charger;
    }

    @Override
    public ChargedOrder renew(UUID orderId) {
        Order current = orders.findById(orderId).orElseThrow(() -> new NotFoundException("order not found"));
        if (current.status() == OrderStatus.PENDING_PAYMENT) {
            orders.save(current.expire());
            if (!ticketTypes.releaseReserved(current.ticketTypeId(), current.quantity())) {
                throw new IllegalStateException("reserved seats missing for order " + current.id());
            }
        } else if (current.status() != OrderStatus.EXPIRED) {
            throw new InvalidOrderTransitionException("only a pending or expired order can be renewed");
        }
        TicketType ticket = ticketTypes.findById(current.ticketTypeId())
                .orElseThrow(() -> new NotFoundException("ticket type not found"));
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (ticket.available() < current.quantity() || !ticketTypes.tryReserve(ticket.id(), current.quantity())) {
            throw new SoldOutException();
        }
        Order pending = orders.save(CreateOrderService.pendingOrder(
                UUID.randomUUID(),
                orders.nextRefNumber(),
                ticket,
                current.quantity(),
                current.customerName(),
                current.customerPhone(),
                ticket.total(now, current.quantity()),
                now));
        try {
            return charger.charge(pending);
        } catch (RuntimeException exception) {
            orders.save(pending.cancel());
            ticketTypes.releaseReserved(ticket.id(), pending.quantity());
            throw exception;
        }
    }
}
