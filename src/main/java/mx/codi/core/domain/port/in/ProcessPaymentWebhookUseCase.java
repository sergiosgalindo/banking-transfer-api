package mx.codi.core.domain.port.in;

public interface ProcessPaymentWebhookUseCase {

    WebhookResult process(String webhookId, String rawBody);

    record WebhookResult(boolean retryable) {
    }
}
