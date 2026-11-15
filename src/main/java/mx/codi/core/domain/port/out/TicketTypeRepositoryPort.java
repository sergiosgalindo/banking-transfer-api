package mx.codi.core.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.codi.core.domain.model.TicketType;

public interface TicketTypeRepositoryPort {

    TicketType save(TicketType ticketType);

    Optional<TicketType> findById(UUID id);

    List<TicketType> findByEventId(UUID eventId);

    boolean tryReserve(UUID ticketTypeId, int quantity);

    boolean releaseReserved(UUID ticketTypeId, int quantity);

    boolean confirmSale(UUID ticketTypeId, int quantity);
}
