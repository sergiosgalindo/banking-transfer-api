package mx.codi.core.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import mx.codi.core.domain.port.in.ProcessPaymentWebhookUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PeyWebhookSignatureTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);
    private static final String SECRET = "whsec_dGVzdC1zZWNyZXQ=";

    @Test
    void anInvalidSignatureIsRejectedBeforeTheOrderChanges() {
        AtomicInteger calls = new AtomicInteger();
        PeyWebhookController controller = controller(calls);

        var response = controller.receive("{\"type\":\"codi.qr.payment_received\"}", "msg-1", timestamp(), "v1,aaaa");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(calls).hasValue(0);
    }

    @Test
    void aValidSignatureReachesTheUseCaseAndARetryIs503() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PeyWebhookController controller = new PeyWebhookController(
                (webhookId, rawBody) -> {
                    calls.incrementAndGet();
                    return new ProcessPaymentWebhookUseCase.WebhookResult(true);
                },
                SECRET,
                300,
                CLOCK);
        String body = "{\"type\":\"codi.qr.payment_received\"}";

        var response = controller.receive(body, "msg-ok", timestamp(), signature("msg-ok", timestamp(), body));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(calls).hasValue(1);
    }

    private static PeyWebhookController controller(AtomicInteger calls) {
        return new PeyWebhookController(
                (webhookId, rawBody) -> {
                    calls.incrementAndGet();
                    return new ProcessPaymentWebhookUseCase.WebhookResult(false);
                },
                SECRET,
                300,
                CLOCK);
    }

    private static String timestamp() {
        return Long.toString(CLOCK.instant().getEpochSecond());
    }

    private static String signature(String id, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode("dGVzdC1zZWNyZXQ="), "HmacSHA256"));
        byte[] raw = mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(raw);
    }
}
