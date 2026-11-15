package mx.codi.core.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.codi.core.domain.model.TicketType;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class TicketTypeRepositoryAdapter implements TicketTypeRepositoryPort {

    private final TicketTypeJpaRepository ticketTypes;

    public TicketTypeRepositoryAdapter(TicketTypeJpaRepository ticketTypes) {
        this.ticketTypes = ticketTypes;
    }

    @Override
    public TicketType save(TicketType ticketType) {
        return ticketTypes.save(TicketTypeJpaEntity.from(ticketType)).toDomain();
    }

    @Override
    public Optional<TicketType> findById(UUID id) {
        return ticketTypes.findById(id).map(TicketTypeJpaEntity::toDomain);
    }

    @Override
    public List<TicketType> findByEventId(UUID eventId) {
        return ticketTypes.findByEventId(eventId).stream().map(TicketTypeJpaEntity::toDomain).toList();
    }

    @Override
    public boolean tryReserve(UUID ticketTypeId, int quantity) {
        return ticketTypes.reserve(ticketTypeId, quantity) == 1;
    }

    @Override
    public boolean releaseReserved(UUID ticketTypeId, int quantity) {
        return ticketTypes.release(ticketTypeId, quantity) == 1;
    }

    @Override
    public boolean confirmSale(UUID ticketTypeId, int quantity) {
        return ticketTypes.confirmSale(ticketTypeId, quantity) == 1;
    }
}
