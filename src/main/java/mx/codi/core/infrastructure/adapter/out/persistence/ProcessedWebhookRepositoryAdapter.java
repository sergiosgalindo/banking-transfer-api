package mx.codi.core.infrastructure.adapter.out.persistence;

import java.util.Optional;
import mx.codi.core.domain.model.ProcessedWebhook;
import mx.codi.core.domain.port.out.ProcessedWebhookRepositoryPort;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedWebhookRepositoryAdapter implements ProcessedWebhookRepositoryPort {

    private final ProcessedWebhookJpaRepository webhooks;

    public ProcessedWebhookRepositoryAdapter(ProcessedWebhookJpaRepository webhooks) {
        this.webhooks = webhooks;
    }

    @Override
    public ProcessedWebhook save(ProcessedWebhook webhook) {
        return webhooks.save(ProcessedWebhookJpaEntity.from(webhook)).toDomain();
    }

    @Override
    public Optional<ProcessedWebhook> findByWebhookId(String webhookId) {
        return webhooks.findById(webhookId).map(ProcessedWebhookJpaEntity::toDomain);
    }

    @Override
    public boolean existsByWebhookId(String webhookId) {
        return webhooks.existsById(webhookId);
    }
}
