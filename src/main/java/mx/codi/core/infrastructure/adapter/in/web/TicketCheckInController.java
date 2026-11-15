package mx.codi.core.infrastructure.adapter.in.web;

import java.time.OffsetDateTime;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.port.in.CheckInTicketUseCase;
import mx.codi.core.domain.port.in.CheckInTicketUseCase.CheckInResult;
import mx.codi.core.domain.port.out.EventRepositoryPort;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketCheckInController {

    private final CheckInTicketUseCase checkIn;
    private final OrderRepositoryPort orders;
    private final EventRepositoryPort events;
    private final TicketTypeRepositoryPort ticketTypes;

    public TicketCheckInController(
            CheckInTicketUseCase checkIn,
            OrderRepositoryPort orders,
            EventRepositoryPort events,
            TicketTypeRepositoryPort ticketTypes
    ) {
        this.checkIn = checkIn;
        this.orders = orders;
        this.events = events;
        this.ticketTypes = ticketTypes;
    }

    @PostMapping("/check-in")
    public CheckInResponse checkIn(@RequestBody CheckInRequest request) {
        CheckInResult result = checkIn.checkIn(request.accessCode());
        String eventName = null;
        String ticketTypeName = null;
        var stored = orders.findByAccessCode(request.accessCode());
        if (stored.isPresent()) {
            Order order = stored.get();
            eventName = events.findById(order.eventId()).map(event -> event.name()).orElse(null);
            ticketTypeName = ticketTypes.findById(order.ticketTypeId()).map(ticket -> ticket.name()).orElse(null);
        }
        return new CheckInResponse(
                result.admitted(),
                result.reason(),
                result.customerName(),
                result.customerPhone(),
                result.quantity(),
                result.checkedInAt(),
                eventName,
                ticketTypeName);
    }

    public record CheckInRequest(String accessCode) {
    }

    public record CheckInResponse(
            boolean admitted,
            String reason,
            String customerName,
            String customerPhone,
            Integer quantity,
            OffsetDateTime checkedInAt,
            String eventName,
            String ticketTypeName
    ) {
    }
}
