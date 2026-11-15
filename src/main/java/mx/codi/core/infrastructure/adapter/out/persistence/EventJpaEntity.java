package mx.codi.core.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.codi.core.domain.model.Event;
import mx.codi.core.domain.model.TicketType;

@Entity
@Table(name = "event")
public class EventJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "flyer_image", length = 5000000)
    private String flyerImage;

    protected EventJpaEntity() {
    }

    public static EventJpaEntity from(Event event) {
        EventJpaEntity entity = new EventJpaEntity();
        entity.id = event.id();
        entity.name = event.name();
        entity.venue = event.venue();
        entity.startsAt = event.startsAt();
        entity.category = event.category();
        entity.description = event.description();
        entity.flyerImage = event.flyerImage();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public Event toDomain(List<TicketType> ticketTypes) {
        return new Event(id, name, venue, startsAt, category, description, flyerImage, ticketTypes);
    }
}
