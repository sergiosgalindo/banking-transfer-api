package mx.codi.core.application.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.port.in.ExpireOrdersUseCase;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import mx.codi.core.domain.port.out.PaymentRecoveryPort;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExpireOrdersService implements ExpireOrdersUseCase {

    private final OrderRepositoryPort orders;
    private final TicketTypeRepositoryPort ticketTypes;
    private final Clock clock;
    private final PaymentRecoveryPort recovery;

    public ExpireOrdersService(
            OrderRepositoryPort orders,
            TicketTypeRepositoryPort ticketTypes,
            Clock clock,
            PaymentRecoveryPort recovery
    ) {
        this.orders = orders;
        this.ticketTypes = ticketTypes;
        this.clock = clock;
        this.recovery = recovery;
    }

    @Override
    public int expireDueOrders() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        int expired = 0;
        for (Order order : orders.findPendingExpired(now)) {
            if (recovery.trySettle(order)) {
                continue;
            }
            orders.save(order.expire());
            if (!ticketTypes.releaseReserved(order.ticketTypeId(), order.quantity())) {
                throw new IllegalStateException("reserved seats missing for order " + order.id());
            }
            expired++;
        }
        return expired;
    }
}
