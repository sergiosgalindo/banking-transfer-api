package mx.codi.core.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import mx.codi.core.domain.model.MexicoCity;
import mx.codi.core.domain.port.in.ListEventsUseCase;
import mx.codi.core.domain.port.out.EventRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class ListEventsService implements ListEventsUseCase {

    private final EventRepositoryPort events;
    private final Clock clock;

    public ListEventsService(EventRepositoryPort events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Override
    public List<LocalDate> upcomingDates() {
        LocalDate today = LocalDate.now(clock.withZone(MexicoCity.ZONE));
        return events.findUpcomingDatesWithAvailability(today, MexicoCity.ZONE);
    }

    @Override
    public List<EventOffer> eventsOn(LocalDate date) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return offers(events.findByDate(date, MexicoCity.ZONE), now);
    }

    @Override
    public List<EventOffer> all() {
        return offers(events.findAll(), OffsetDateTime.now(clock));
    }

    private static List<EventOffer> offers(List<mx.codi.core.domain.model.Event> found, OffsetDateTime now) {
        return found.stream()
                .map(event -> new EventOffer(
                        event,
                        event.ticketTypes().stream()
                                .map(ticket -> new TicketOffer(ticket, ticket.unitPrice(now)))
                                .toList()))
                .toList();
    }
}
