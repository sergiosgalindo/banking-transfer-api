package mx.codi.core.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

public record PaymentAlert(
        UUID id,
        UUID orderId,
        String webhookId,
        String reason,
        BigDecimal expectedAmount,
        BigDecimal receivedAmount,
        String trackingKey,
        String detail,
        OffsetDateTime createdAt
) {
    public PaymentAlert {
        Objects.requireNonNull(id, "id");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail is required");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        createdAt = createdAt.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
