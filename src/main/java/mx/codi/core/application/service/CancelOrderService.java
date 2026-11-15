package mx.codi.core.application.service;

import java.util.UUID;
import mx.codi.core.domain.NotFoundException;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.port.in.CancelOrderUseCase;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CancelOrderService implements CancelOrderUseCase {

    private final OrderRepositoryPort orders;
    private final TicketTypeRepositoryPort ticketTypes;

    public CancelOrderService(OrderRepositoryPort orders, TicketTypeRepositoryPort ticketTypes) {
        this.orders = orders;
        this.ticketTypes = ticketTypes;
    }

    @Override
    public Order cancel(UUID orderId) {
        Order current = orders.findById(orderId).orElseThrow(() -> new NotFoundException("order not found"));
        Order cancelled = orders.save(current.cancel());
        if (!ticketTypes.releaseReserved(current.ticketTypeId(), current.quantity())) {
            throw new IllegalStateException("reserved seats missing for order " + current.id());
        }
        return cancelled;
    }
}
