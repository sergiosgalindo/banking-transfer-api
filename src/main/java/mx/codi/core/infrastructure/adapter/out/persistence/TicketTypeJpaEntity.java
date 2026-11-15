package mx.codi.core.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.codi.core.domain.model.TicketType;

@Entity
@Table(name = "ticket_type")
public class TicketTypeJpaEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "regular_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal regularPrice;

    @Column(name = "presale_price", precision = 12, scale = 2)
    private BigDecimal presalePrice;

    @Column(name = "presale_ends_at")
    private OffsetDateTime presaleEndsAt;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int sold;

    @Column(nullable = false)
    private int reserved;

    protected TicketTypeJpaEntity() {
    }

    public static TicketTypeJpaEntity from(TicketType ticketType) {
        TicketTypeJpaEntity entity = new TicketTypeJpaEntity();
        entity.id = ticketType.id();
        entity.eventId = ticketType.eventId();
        entity.name = ticketType.name();
        entity.regularPrice = ticketType.regularPrice();
        entity.presalePrice = ticketType.presalePrice();
        entity.presaleEndsAt = ticketType.presaleEndsAt();
        entity.capacity = ticketType.capacity();
        entity.sold = ticketType.sold();
        entity.reserved = ticketType.reserved();
        return entity;
    }

    public UUID getEventId() {
        return eventId;
    }

    public TicketType toDomain() {
        return new TicketType(
                id,
                eventId,
                name,
                regularPrice,
                presalePrice,
                presaleEndsAt,
                capacity,
                sold,
                reserved);
    }
}
