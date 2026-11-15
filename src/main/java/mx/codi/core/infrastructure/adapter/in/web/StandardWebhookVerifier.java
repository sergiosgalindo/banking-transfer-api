package mx.codi.core.infrastructure.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class StandardWebhookVerifier {

    private final String secret;
    private final Duration tolerance;

    public StandardWebhookVerifier(String secret, Duration tolerance) {
        this.secret = secret;
        this.tolerance = tolerance;
    }

    public boolean isValid(String webhookId, String timestamp, String signatureHeader, String body, Instant now) {
        if (webhookId == null || webhookId.isBlank() || timestamp == null || signatureHeader == null || body == null) {
            return false;
        }
        if (secret == null || secret.isBlank()) {
            return false;
        }
        long sent;
        try {
            sent = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - sent) > tolerance.getSeconds()) {
            return false;
        }
        byte[] expected = sign(webhookId + "." + timestamp + "." + body);
        for (String part : signatureHeader.trim().split("\\s+")) {
            int comma = part.indexOf(',');
            if (comma < 0 || !"v1".equals(part.substring(0, comma))) {
                continue;
            }
            try {
                byte[] actual = Base64.getDecoder().decode(part.substring(comma + 1));
                if (MessageDigest.isEqual(expected, actual)) {
                    return true;
                }
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return false;
    }

    private byte[] sign(String content) {
        try {
            String encoded = secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret;
            byte[] key = Base64.getDecoder().decode(encoded);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return new byte[0];
        }
    }
}
