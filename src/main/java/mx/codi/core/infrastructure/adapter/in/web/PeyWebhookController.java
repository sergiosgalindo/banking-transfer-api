package mx.codi.core.infrastructure.adapter.in.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import mx.codi.core.domain.port.in.ProcessPaymentWebhookUseCase;
import mx.codi.core.domain.port.in.ProcessPaymentWebhookUseCase.WebhookResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PeyWebhookController {

    private final ProcessPaymentWebhookUseCase webhooks;
    private final StandardWebhookVerifier verifier;
    private final Clock clock;

    public PeyWebhookController(
            ProcessPaymentWebhookUseCase webhooks,
            @Value("${codi.webhook.secret:}") String secret,
            @Value("${codi.webhook.tolerance-seconds:300}") long toleranceSeconds,
            Clock clock
    ) {
        this.webhooks = webhooks;
        this.verifier = new StandardWebhookVerifier(secret, Duration.ofSeconds(toleranceSeconds));
        this.clock = clock;
    }

    @PostMapping("/api/v1/payments/webhooks/pey")
    public ResponseEntity<Void> receive(
            @RequestBody String body,
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
            @RequestHeader(value = "webhook-signature", required = false) String signature
    ) {
        if (!verifier.isValid(webhookId, timestamp, signature, body, Instant.now(clock))) {
            return ResponseEntity.badRequest().build();
        }
        WebhookResult result = webhooks.process(webhookId, body);
        if (result.retryable()) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok().build();
    }
}
