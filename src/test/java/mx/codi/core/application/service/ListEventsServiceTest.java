package mx.codi.core.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.codi.core.domain.model.Event;
import mx.codi.core.domain.model.TicketType;
import mx.codi.core.domain.port.in.ListEventsUseCase.TicketOffer;
import mx.codi.core.domain.port.out.EventRepositoryPort;
import org.junit.jupiter.api.Test;

class ListEventsServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-14T18:00:00Z");

    @Test
    void eventsOnPublishTheCurrentPriceForEachTicket() {
        TicketType open = ticket("General", 10, 0);
        TicketType soldOut = ticket("Agotado", 4, 4);
        Event event = new Event(
                UUID.randomUUID(),
                "Social de salsa",
                "Salón Reforma",
                OffsetDateTime.parse("2026-11-16T03:00:00Z"),
                "social",
                "Noche de salsa social.",
                List.of(open, soldOut));
        ListEventsService service = new ListEventsService(
                new FixedEvents(List.of(event)),
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

        List<TicketOffer> offers = service.eventsOn(LocalDate.parse("2026-11-15")).getFirst().tickets();

        assertThat(price(offers, open.id())).isEqualByComparingTo("180.00");
        assertThat(price(offers, soldOut.id())).isEqualByComparingTo("250.00");
    }

    private static BigDecimal price(List<TicketOffer> offers, UUID ticketTypeId) {
        return offers.stream()
                .filter(offer -> offer.ticketType().id().equals(ticketTypeId))
                .findFirst()
                .orElseThrow()
                .currentPrice();
    }

    private static TicketType ticket(String name, int capacity, int sold) {
        return new TicketType(
                UUID.randomUUID(),
                UUID.randomUUID(),
                name,
                new BigDecimal("250.00"),
                new BigDecimal("180.00"),
                OffsetDateTime.parse("2026-11-16T00:00:00Z"),
                capacity,
                sold,
                0);
    }

    private static final class FixedEvents implements EventRepositoryPort {

        private final List<Event> found;

        private FixedEvents(List<Event> found) {
            this.found = found;
        }

        @Override
        public Event save(Event event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Event> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public List<LocalDate> findUpcomingDatesWithAvailability(LocalDate today, ZoneId zone) {
            return List.of();
        }

        @Override
        public List<Event> findByDate(LocalDate date, ZoneId zone) {
            return found;
        }

        @Override
        public List<Event> findAll() {
            return found;
        }

        @Override
        public long count() {
            return found.size();
        }
    }
}
