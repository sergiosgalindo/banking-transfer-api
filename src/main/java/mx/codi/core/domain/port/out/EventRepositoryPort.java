package mx.codi.core.domain.port.out;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.codi.core.domain.model.Event;

public interface EventRepositoryPort {

    Event save(Event event);

    Optional<Event> findById(UUID id);

    List<LocalDate> findUpcomingDatesWithAvailability(LocalDate today, ZoneId zone);

    List<Event> findByDate(LocalDate date, ZoneId zone);

    List<Event> findAll();

    long count();
}
