package mx.codi.core.infrastructure.adapter.out.pey;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mx.codi.core.domain.PaymentGatewayException;
import mx.codi.core.domain.port.out.PaymentGatewayPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "codi.pey.mode", havingValue = "live")
public class PeyPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final RestClient client;

    public PeyPaymentGatewayAdapter(
            @Value("${codi.pey.base-url}") String baseUrl,
            @Value("${codi.pey.api-key}") String apiKey
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(25_000);
        this.client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("User-Agent", "codi-core/1.0")
                .build();
    }

    @Override
    public CodiQr createCodiQr(CodiQrRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", request.amount());
        body.put("concept", request.concept());
        body.put("expiration", request.expirationSeconds());
        body.put("numRef", Integer.toString(request.numRef()));
        body.put("metadata", request.metadata());
        JsonNode response = post("/codi/qr", body);
        return new CodiQr(requiredText(response, "id"), requiredText(response, "qrString"));
    }

    @Override
    public PaymentLink createPaymentLink(PaymentLinkRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", request.amount());
        body.put("successUrl", request.successUrl());
        body.put("backUrl", request.backUrl());
        body.put("clientReferenceId", request.clientReferenceId());
        body.put("metadata", request.metadata());
        JsonNode response = post("/payment-link/create", body);
        return new PaymentLink(requiredText(response, "id"), requiredText(response, "url"));
    }

    @Override
    public List<CodiPayment> findCodiQrPayments(String codiQrId) {
        return payments(get("/transaction/codi/qr/" + codiQrId));
    }

    @Override
    public List<CodiPayment> findPaymentLinkPayments(String paymentLinkId) {
        return payments(get("/payment-link/" + paymentLinkId));
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            String raw = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseJson(raw, path);
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Pey request failed for " + path, exception);
        }
    }

    private JsonNode get(String path) {
        try {
            String raw = client.get().uri(path).retrieve().body(String.class);
            return parseJson(raw, path);
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Pey request failed for " + path, exception);
        }
    }

    private static JsonNode parseJson(String raw, String path) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(raw);
        } catch (Exception exception) {
            throw new PaymentGatewayException("Invalid JSON from Pey for " + path, exception);
        }
    }

    private static List<CodiPayment> payments(JsonNode body) {
        if (body == null) {
            return List.of();
        }
        JsonNode items = body.has("items") ? body.get("items") : body.get("transactions");
        if (items == null || !items.isArray()) {
            return List.of();
        }
        List<CodiPayment> payments = new ArrayList<>();
        for (JsonNode item : items) {
            BigDecimal amount = decimal(item.get("amount"));
            JsonNode breakdown = item.get("amountBreakdown");
            BigDecimal base = breakdown == null ? null : decimal(breakdown.get("base"));
            payments.add(new CodiPayment(
                    text(item, "id"),
                    text(item, "status"),
                    amount,
                    base));
        }
        return payments;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException("Pey response is missing " + field, null);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return new BigDecimal(node.asText());
    }
}
