package mx.codi.core.domain.port.in;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.codi.core.domain.model.Event;
import mx.codi.core.domain.model.TicketType;

public interface ManageEventUseCase {

    Event createEvent(CreateEvent command);

    Event updateFlyer(UpdateEventFlyer command);

    TicketType addTicketType(AddTicketType command);

    TicketType updateTicketType(UpdateTicketType command);

    record CreateEvent(
            String name,
            String venue,
            OffsetDateTime startsAt,
            String category,
            String description,
            String flyerImage
    ) {
        public CreateEvent(
                String name,
                String venue,
                OffsetDateTime startsAt,
                String category,
                String description
        ) {
            this(name, venue, startsAt, category, description, null);
        }
    }

    record UpdateEventFlyer(
            UUID eventId,
            String description,
            String flyerImage
    ) {
    }

    record AddTicketType(
            UUID eventId,
            String name,
            BigDecimal regularPrice,
            BigDecimal presalePrice,
            OffsetDateTime presaleEndsAt,
            int capacity
    ) {
    }

    record UpdateTicketType(
            UUID eventId,
            UUID ticketTypeId,
            String name,
            BigDecimal regularPrice,
            BigDecimal presalePrice,
            OffsetDateTime presaleEndsAt,
            int capacity
    ) {
    }
}
