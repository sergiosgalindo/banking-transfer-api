package mx.codi.core.domain.model;

public record ProcessedWebhook(String webhookId, String transactionId) {
    public ProcessedWebhook {
        if (webhookId == null || webhookId.isBlank()) {
            throw new IllegalArgumentException("webhookId is required");
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId is required");
        }
    }
}
