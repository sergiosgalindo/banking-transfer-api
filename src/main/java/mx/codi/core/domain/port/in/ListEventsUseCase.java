package mx.codi.core.domain.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import mx.codi.core.domain.model.Event;
import mx.codi.core.domain.model.TicketType;

public interface ListEventsUseCase {

    List<LocalDate> upcomingDates();

    List<EventOffer> eventsOn(LocalDate date);

    List<EventOffer> all();

    record EventOffer(Event event, List<TicketOffer> tickets) {
    }

    record TicketOffer(TicketType ticketType, BigDecimal currentPrice) {
    }
}
