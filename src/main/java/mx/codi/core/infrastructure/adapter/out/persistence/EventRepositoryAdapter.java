package mx.codi.core.infrastructure.adapter.out.persistence;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.codi.core.domain.model.Event;
import mx.codi.core.domain.model.TicketType;
import mx.codi.core.domain.port.out.EventRepositoryPort;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepositoryAdapter implements EventRepositoryPort {

    private final EventJpaRepository events;
    private final TicketTypeJpaRepository ticketTypes;

    public EventRepositoryAdapter(EventJpaRepository events, TicketTypeJpaRepository ticketTypes) {
        this.events = events;
        this.ticketTypes = ticketTypes;
    }

    @Override
    public Event save(Event event) {
        return events.save(EventJpaEntity.from(event)).toDomain(List.of());
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return events.findById(id).map(entity -> entity.toDomain(ticketTypesOf(List.of(id)).getOrDefault(id, List.of())));
    }

    @Override
    public List<LocalDate> findUpcomingDatesWithAvailability(LocalDate today, ZoneId zone) {
        return events.findUpcomingDates(today, zone.getId()).stream().map(LocalDate::parse).toList();
    }

    @Override
    public List<Event> findByDate(LocalDate date, ZoneId zone) {
        List<EventJpaEntity> rows = events.findByLocalDate(date, zone.getId());
        Map<UUID, List<TicketType>> ticketsByEvent = ticketTypesOf(rows.stream().map(EventJpaEntity::getId).toList());
        return rows.stream()
                .map(row -> row.toDomain(ticketsByEvent.getOrDefault(row.getId(), List.of())))
                .toList();
    }

    @Override
    public List<Event> findAll() {
        List<EventJpaEntity> rows = events.findAll();
        Map<UUID, List<TicketType>> ticketsByEvent = ticketTypesOf(rows.stream().map(EventJpaEntity::getId).toList());
        return rows.stream()
                .map(row -> row.toDomain(ticketsByEvent.getOrDefault(row.getId(), List.of())))
                .toList();
    }

    @Override
    public long count() {
        return events.count();
    }

    private Map<UUID, List<TicketType>> ticketTypesOf(List<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return ticketTypes.findByEventIdIn(eventIds).stream()
                .map(TicketTypeJpaEntity::toDomain)
                .collect(Collectors.groupingBy(TicketType::eventId));
    }
}
