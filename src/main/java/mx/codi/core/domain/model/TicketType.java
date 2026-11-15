package mx.codi.core.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

public record TicketType(
        UUID id,
        UUID eventId,
        String name,
        BigDecimal regularPrice,
        BigDecimal presalePrice,
        OffsetDateTime presaleEndsAt,
        int capacity,
        int sold,
        int reserved
) {
    public TicketType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(eventId, "eventId");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Objects.requireNonNull(regularPrice, "regularPrice");
        if (presaleEndsAt != null) {
            presaleEndsAt = presaleEndsAt.withOffsetSameInstant(ZoneOffset.UTC);
        }
        if (capacity < 0 || sold < 0 || reserved < 0 || sold + reserved > capacity) {
            throw new IllegalArgumentException("ticket capacity is inconsistent");
        }
    }

    public int available() {
        return capacity - sold - reserved;
    }

    public BigDecimal unitPrice(OffsetDateTime now) {
        if (presalePrice != null && presaleEndsAt != null && now.isBefore(presaleEndsAt) && available() > 0) {
            return presalePrice;
        }
        return regularPrice;
    }

    public BigDecimal total(OffsetDateTime now, int quantity) {
        return unitPrice(now).multiply(BigDecimal.valueOf(quantity));
    }

    public TicketType reserve(int quantity) {
        return new TicketType(id, eventId, name, regularPrice, presalePrice, presaleEndsAt, capacity, sold, reserved + quantity);
    }

    public TicketType release(int quantity) {
        return new TicketType(id, eventId, name, regularPrice, presalePrice, presaleEndsAt, capacity, sold, reserved - quantity);
    }
}
