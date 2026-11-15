package mx.codi.core.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketTypeJpaRepository extends JpaRepository<TicketTypeJpaEntity, UUID> {

    List<TicketTypeJpaEntity> findByEventId(UUID eventId);

    List<TicketTypeJpaEntity> findByEventIdIn(Collection<UUID> eventIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TicketTypeJpaEntity ticket
            SET ticket.reserved = ticket.reserved + :quantity
            WHERE ticket.id = :id
              AND (ticket.capacity - ticket.sold - ticket.reserved) >= :quantity
            """)
    int reserve(@Param("id") UUID id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TicketTypeJpaEntity ticket
            SET ticket.reserved = ticket.reserved - :quantity
            WHERE ticket.id = :id
              AND ticket.reserved >= :quantity
            """)
    int release(@Param("id") UUID id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TicketTypeJpaEntity ticket
            SET ticket.sold = ticket.sold + :quantity,
                ticket.reserved = ticket.reserved - :quantity
            WHERE ticket.id = :id
              AND ticket.reserved >= :quantity
            """)
    int confirmSale(@Param("id") UUID id, @Param("quantity") int quantity);
}
