package mx.codi.core.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.codi.core.domain.model.PaymentAlert;

@Entity
@Table(name = "payment_alert")
public class PaymentAlertJpaEntity {

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "webhook_id", length = 200)
    private String webhookId;

    @Column(nullable = false, length = 80)
    private String reason;

    @Column(name = "expected_amount", precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "received_amount", precision = 12, scale = 2)
    private BigDecimal receivedAmount;

    @Column(name = "tracking_key", length = 200)
    private String trackingKey;

    @Column(nullable = false, length = 2000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PaymentAlertJpaEntity() {
    }

    public static PaymentAlertJpaEntity from(PaymentAlert alert) {
        PaymentAlertJpaEntity entity = new PaymentAlertJpaEntity();
        entity.id = alert.id();
        entity.orderId = alert.orderId();
        entity.webhookId = alert.webhookId();
        entity.reason = alert.reason();
        entity.expectedAmount = alert.expectedAmount();
        entity.receivedAmount = alert.receivedAmount();
        entity.trackingKey = alert.trackingKey();
        entity.detail = alert.detail();
        entity.createdAt = alert.createdAt();
        return entity;
    }

    public PaymentAlert toDomain() {
        return new PaymentAlert(
                id,
                orderId,
                webhookId,
                reason,
                expectedAmount,
                receivedAmount,
                trackingKey,
                detail,
                createdAt);
    }
}
