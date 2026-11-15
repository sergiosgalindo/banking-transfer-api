package mx.codi.core.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.codi.core.domain.model.Event;
import mx.codi.core.domain.model.MexicoCity;
import mx.codi.core.domain.model.TicketType;
import mx.codi.core.domain.port.out.EventRepositoryPort;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoEventSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoEventSeeder.class);

    private final EventRepositoryPort events;
    private final TicketTypeRepositoryPort ticketTypes;
    private final Clock clock;
    private final boolean enabled;

    public DemoEventSeeder(
            EventRepositoryPort events,
            TicketTypeRepositoryPort ticketTypes,
            Clock clock,
            @Value("${codi.seed.enabled:true}") boolean enabled
    ) {
        this.events = events;
        this.ticketTypes = ticketTypes;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            seedIfEmpty();
        }
    }

    public void seedIfEmpty() {
        if (events.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(MexicoCity.ZONE));
        saveEvent(
                UUID.fromString("10000000-0000-4000-8000-000000000001"),
                UUID.fromString("10000000-0000-4000-8000-000000000011"),
                "Social de salsa",
                "social",
                "Salón Reforma",
                today.plusDays(1).atTime(21, 0),
                "Noche de salsa social.",
                "General",
                new BigDecimal("250.00"),
                new BigDecimal("180.00"),
                today.plusDays(1).atTime(18, 0),
                120);
        saveEvent(
                UUID.fromString("10000000-0000-4000-8000-000000000002"),
                UUID.fromString("10000000-0000-4000-8000-000000000012"),
                "Taller de ritmos",
                "taller",
                "Estudio Centro",
                today.plusDays(3).atTime(11, 0),
                "Taller práctico de ritmos.",
                "Participante",
                new BigDecimal("400.00"),
                null,
                null,
                30);
        saveEvent(
                UUID.fromString("10000000-0000-4000-8000-000000000003"),
                UUID.fromString("10000000-0000-4000-8000-000000000013"),
                "Concierto en vivo",
                "concierto",
                "Teatro de la Ciudad",
                today.plusDays(7).atTime(20, 0),
                "Concierto con cupo limitado.",
                "General",
                new BigDecimal("800.00"),
                new BigDecimal("640.00"),
                today.plusDays(5).atTime(23, 59),
                500);
        log.info("Seeded demo events: social, taller, concierto");
    }

    private void saveEvent(
            UUID eventId,
            UUID ticketTypeId,
            String name,
            String category,
            String venue,
            LocalDateTime startsAt,
            String description,
            String ticketName,
            BigDecimal regularPrice,
            BigDecimal presalePrice,
            LocalDateTime presaleEndsAt,
            int capacity
    ) {
        events.save(new Event(
                eventId,
                name,
                venue,
                at(startsAt),
                category,
                description,
                List.of()));
        ticketTypes.save(new TicketType(
                ticketTypeId,
                eventId,
                ticketName,
                regularPrice,
                presalePrice,
                presaleEndsAt == null ? null : at(presaleEndsAt),
                capacity,
                0,
                0));
    }

    private static OffsetDateTime at(LocalDateTime localDateTime) {
        return localDateTime.atZone(MexicoCity.ZONE).toOffsetDateTime();
    }
}
