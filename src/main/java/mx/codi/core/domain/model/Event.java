package mx.codi.core.domain.model;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Event(
        UUID id,
        String name,
        String venue,
        OffsetDateTime startsAt,
        String category,
        String description,
        String flyerImage,
        List<TicketType> ticketTypes
) {
    public Event(
            UUID id,
            String name,
            String venue,
            OffsetDateTime startsAt,
            String category,
            String description,
            List<TicketType> ticketTypes
    ) {
        this(id, name, venue, startsAt, category, description, null, ticketTypes);
    }

    public Event {
        Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (venue == null || venue.isBlank()) {
            throw new IllegalArgumentException("venue is required");
        }
        Objects.requireNonNull(startsAt, "startsAt");
        startsAt = startsAt.withOffsetSameInstant(ZoneOffset.UTC);
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        Objects.requireNonNull(description, "description");
        ticketTypes = List.copyOf(ticketTypes);
    }
}
