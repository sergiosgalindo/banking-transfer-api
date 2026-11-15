package mx.codi.core.application.service;

import java.util.List;
import java.util.UUID;
import mx.codi.core.domain.NotFoundException;
import mx.codi.core.domain.model.Event;
import mx.codi.core.domain.model.TicketType;
import mx.codi.core.domain.port.in.ManageEventUseCase;
import mx.codi.core.domain.port.out.EventRepositoryPort;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ManageEventService implements ManageEventUseCase {

    private final EventRepositoryPort events;
    private final TicketTypeRepositoryPort ticketTypes;

    public ManageEventService(EventRepositoryPort events, TicketTypeRepositoryPort ticketTypes) {
        this.events = events;
        this.ticketTypes = ticketTypes;
    }

    @Override
    public Event createEvent(CreateEvent command) {
        return events.save(new Event(
                UUID.randomUUID(),
                command.name(),
                command.venue(),
                command.startsAt(),
                command.category(),
                command.description(),
                command.flyerImage(),
                List.of()));
    }

    @Override
    public Event updateEvent(UpdateEvent command) {
        Event existing = events.findById(command.eventId())
                .orElseThrow(() -> new NotFoundException("event not found"));
        String updatedName = command.name() != null && !command.name().isBlank() ? command.name().trim() : existing.name();
        String updatedVenue = command.venue() != null && !command.venue().isBlank() ? command.venue().trim() : existing.venue();
        java.time.OffsetDateTime updatedStartsAt = command.startsAt() != null ? command.startsAt() : existing.startsAt();
        String updatedCategory = command.category() != null && !command.category().isBlank() ? command.category().trim() : existing.category();
        String updatedDescription = command.description() != null ? command.description() : existing.description();
        String updatedFlyer = command.flyerImage() != null ? command.flyerImage() : existing.flyerImage();
        return events.save(new Event(
                existing.id(),
                updatedName,
                updatedVenue,
                updatedStartsAt,
                updatedCategory,
                updatedDescription,
                updatedFlyer,
                existing.ticketTypes()));
    }

    @Override
    public Event updateFlyer(UpdateEventFlyer command) {
        Event existing = events.findById(command.eventId())
                .orElseThrow(() -> new NotFoundException("event not found"));
        String updatedName = command.name() != null && !command.name().isBlank() ? command.name().trim() : existing.name();
        String updatedDescription = command.description() != null ? command.description() : existing.description();
        String updatedFlyer = command.flyerImage() != null ? command.flyerImage() : existing.flyerImage();
        return events.save(new Event(
                existing.id(),
                updatedName,
                existing.venue(),
                existing.startsAt(),
                existing.category(),
                updatedDescription,
                updatedFlyer,
                existing.ticketTypes()));
    }

    @Override
    public TicketType addTicketType(AddTicketType command) {
        events.findById(command.eventId()).orElseThrow(() -> new NotFoundException("event not found"));
        if (command.capacity() < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        if (command.regularPrice() == null || command.regularPrice().signum() <= 0) {
            throw new IllegalArgumentException("regularPrice must be positive");
        }
        return ticketTypes.save(new TicketType(
                UUID.randomUUID(),
                command.eventId(),
                command.name(),
                command.regularPrice(),
                command.presalePrice(),
                command.presaleEndsAt(),
                command.capacity(),
                0,
                0));
    }

    @Override
    public TicketType updateTicketType(UpdateTicketType command) {
        events.findById(command.eventId()).orElseThrow(() -> new NotFoundException("event not found"));
        TicketType existing = ticketTypes.findById(command.ticketTypeId())
                .orElseThrow(() -> new NotFoundException("ticket type not found"));
        if (command.capacity() < existing.sold() + existing.reserved()) {
            throw new IllegalArgumentException("capacity cannot be lower than sold + reserved");
        }
        if (command.regularPrice() == null || command.regularPrice().signum() <= 0) {
            throw new IllegalArgumentException("regularPrice must be positive");
        }
        return ticketTypes.save(new TicketType(
                existing.id(),
                existing.eventId(),
                command.name() != null && !command.name().isBlank() ? command.name() : existing.name(),
                command.regularPrice(),
                command.presalePrice(),
                command.presaleEndsAt() != null ? command.presaleEndsAt() : existing.presaleEndsAt(),
                command.capacity(),
                existing.sold(),
                existing.reserved()));
    }
}
