package mx.codi.core.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import mx.codi.core.domain.model.ProcessedWebhook;

@Entity
@Table(name = "processed_webhook")
public class ProcessedWebhookJpaEntity {

    @Id
    @Column(name = "webhook_id", length = 200)
    private String webhookId;

    @Column(name = "transaction_id", nullable = false, length = 200)
    private String transactionId;

    protected ProcessedWebhookJpaEntity() {
    }

    public static ProcessedWebhookJpaEntity from(ProcessedWebhook webhook) {
        ProcessedWebhookJpaEntity entity = new ProcessedWebhookJpaEntity();
        entity.webhookId = webhook.webhookId();
        entity.transactionId = webhook.transactionId();
        return entity;
    }

    public ProcessedWebhook toDomain() {
        return new ProcessedWebhook(webhookId, transactionId);
    }
}
