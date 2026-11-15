package mx.codi.core.application.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import mx.codi.core.domain.AlreadyCheckedInException;
import mx.codi.core.domain.InvalidOrderTransitionException;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.port.in.CheckInTicketUseCase;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CheckInTicketService implements CheckInTicketUseCase {

    private final OrderRepositoryPort orders;
    private final Clock clock;

    public CheckInTicketService(OrderRepositoryPort orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    @Override
    public CheckInResult checkIn(String accessCode) {
        return orders.findByAccessCode(accessCode)
                .map(this::admit)
                .orElseGet(() -> new CheckInResult(false, "not_found", null, null, null, null));
    }

    private CheckInResult admit(Order order) {
        try {
            Order admitted = orders.save(order.checkIn(OffsetDateTime.now(clock)));
            return new CheckInResult(
                    true,
                    null,
                    admitted.customerName(),
                    admitted.customerPhone(),
                    admitted.quantity(),
                    admitted.checkedInAt());
        } catch (AlreadyCheckedInException exception) {
            return new CheckInResult(
                    false,
                    "already_checked_in",
                    order.customerName(),
                    order.customerPhone(),
                    order.quantity(),
                    exception.checkedInAt());
        } catch (InvalidOrderTransitionException exception) {
            return new CheckInResult(
                    false,
                    "not_paid",
                    order.customerName(),
                    order.customerPhone(),
                    order.quantity(),
                    null);
        }
    }
}
