package mx.codi.core.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import mx.codi.core.domain.model.Event;
import mx.codi.core.domain.model.TicketType;
import mx.codi.core.domain.port.in.ListEventsUseCase;
import mx.codi.core.domain.port.in.ListEventsUseCase.EventOffer;
import mx.codi.core.domain.port.in.ListEventsUseCase.TicketOffer;
import mx.codi.core.domain.port.in.ManageEventUseCase;
import mx.codi.core.domain.port.in.ManageEventUseCase.AddTicketType;
import mx.codi.core.domain.port.in.ManageEventUseCase.CreateEvent;
import mx.codi.core.domain.port.in.ManageEventUseCase.UpdateEventFlyer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final ListEventsUseCase events;
    private final ManageEventUseCase manageEvents;
    private final Clock clock;

    public EventController(ListEventsUseCase events, ManageEventUseCase manageEvents, Clock clock) {
        this.events = events;
        this.manageEvents = manageEvents;
        this.clock = clock;
    }

    @GetMapping("/upcoming-dates")
    public UpcomingDatesResponse upcomingDates() {
        return new UpcomingDatesResponse(events.upcomingDates());
    }

    @GetMapping
    public List<EventResponse> list(@RequestParam(required = false) LocalDate date) {
        List<EventOffer> found = date == null ? events.all() : events.eventsOn(date);
        return found.stream().map(EventResponse::from).toList();
    }

    @PostMapping
    public EventResponse create(@RequestBody CreateEventRequest request) {
        Event created = manageEvents.createEvent(new CreateEvent(
                request.name(),
                request.venue(),
                request.startsAt(),
                request.category(),
                request.description(),
                request.flyerImage()));
        return EventResponse.from(new EventOffer(created, List.of()));
    }

    @PutMapping("/{eventId}/flyer")
    public EventResponse updateFlyer(@PathVariable UUID eventId, @RequestBody UpdateFlyerRequest request) {
        Event updated = manageEvents.updateFlyer(new UpdateEventFlyer(
                eventId,
                request.description(),
                request.flyerImage()));
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<TicketOffer> offers = updated.ticketTypes().stream()
                .map(tt -> new TicketOffer(tt, tt.unitPrice(now)))
                .toList();
        return EventResponse.from(new EventOffer(updated, offers));
    }

    @PostMapping("/{eventId}/ticket-types")
    public TicketTypeResponse addTicketType(@PathVariable UUID eventId, @RequestBody AddTicketTypeRequest request) {
        TicketType created = manageEvents.addTicketType(new AddTicketType(
                eventId,
                request.name(),
                request.regularPrice(),
                request.presalePrice(),
                request.presaleEndsAt(),
                request.capacity()));
        return TicketTypeResponse.from(new TicketOffer(created, created.unitPrice(OffsetDateTime.now(clock))));
    }

    @PutMapping("/{eventId}/ticket-types/{ticketTypeId}")
    public TicketTypeResponse updateTicketType(
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId,
            @RequestBody AddTicketTypeRequest request
    ) {
        TicketType updated = manageEvents.updateTicketType(new ManageEventUseCase.UpdateTicketType(
                eventId,
                ticketTypeId,
                request.name(),
                request.regularPrice(),
                request.presalePrice(),
                request.presaleEndsAt(),
                request.capacity()));
        return TicketTypeResponse.from(new TicketOffer(updated, updated.unitPrice(OffsetDateTime.now(clock))));
    }

    public record CreateEventRequest(
            String name,
            String venue,
            OffsetDateTime startsAt,
            String category,
            String description,
            String flyerImage
    ) {
    }

    public record UpdateFlyerRequest(
            String description,
            String flyerImage
    ) {
    }

    public record AddTicketTypeRequest(
            String name,
            BigDecimal regularPrice,
            BigDecimal presalePrice,
            OffsetDateTime presaleEndsAt,
            int capacity
    ) {
    }

    public record UpcomingDatesResponse(List<LocalDate> dates) {
    }

    public record EventResponse(
            UUID id,
            String name,
            String venue,
            OffsetDateTime startsAt,
            String category,
            String description,
            String flyerImage,
            List<TicketTypeResponse> ticketTypes
    ) {
        static EventResponse from(ListEventsUseCase.EventOffer offer) {
            Event event = offer.event();
            List<TicketTypeResponse> tickets = offer.tickets().stream()
                    .sorted(Comparator.comparing(ticket -> ticket.ticketType().name()))
                    .map(TicketTypeResponse::from)
                    .toList();
            return new EventResponse(
                    event.id(),
                    event.name(),
                    event.venue(),
                    event.startsAt(),
                    event.category(),
                    event.description(),
                    event.flyerImage(),
                    tickets);
        }
    }

    public record TicketTypeResponse(
            UUID id,
            String name,
            BigDecimal regularPrice,
            BigDecimal presalePrice,
            OffsetDateTime presaleEndsAt,
            BigDecimal currentPrice,
            int capacity,
            int sold,
            int reserved,
            int available
    ) {
        static TicketTypeResponse from(ListEventsUseCase.TicketOffer offer) {
            TicketType ticketType = offer.ticketType();
            return new TicketTypeResponse(
                    ticketType.id(),
                    ticketType.name(),
                    ticketType.regularPrice(),
                    ticketType.presalePrice(),
                    ticketType.presaleEndsAt(),
                    offer.currentPrice(),
                    ticketType.capacity(),
                    ticketType.sold(),
                    ticketType.reserved(),
                    ticketType.available());
        }
    }
}
