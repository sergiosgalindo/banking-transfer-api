package mx.codi.core.domain.port.out;

import java.util.Optional;
import mx.codi.core.domain.model.ProcessedWebhook;

public interface ProcessedWebhookRepositoryPort {

    ProcessedWebhook save(ProcessedWebhook webhook);

    Optional<ProcessedWebhook> findByWebhookId(String webhookId);

    boolean existsByWebhookId(String webhookId);
}
