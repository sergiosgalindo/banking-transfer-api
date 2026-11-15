package mx.codi.core.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.model.OrderStatus;
import mx.codi.core.domain.model.PaymentAlert;
import mx.codi.core.domain.model.ProcessedWebhook;
import mx.codi.core.domain.port.in.ProcessPaymentWebhookUseCase;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import mx.codi.core.domain.port.out.PaymentAlertRepositoryPort;
import mx.codi.core.domain.port.out.ProcessedWebhookRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessPaymentWebhookService implements ProcessPaymentWebhookUseCase {

    private final ObjectMapper json;
    private final OrderRepositoryPort orders;
    private final ProcessedWebhookRepositoryPort webhooks;
    private final PaymentAlertRepositoryPort alerts;
    private final SettleOrderService settle;
    private final Clock clock;

    public ProcessPaymentWebhookService(
            ObjectMapper json,
            OrderRepositoryPort orders,
            ProcessedWebhookRepositoryPort webhooks,
            PaymentAlertRepositoryPort alerts,
            SettleOrderService settle,
            Clock clock
    ) {
        this.json = json;
        this.orders = orders;
        this.webhooks = webhooks;
        this.alerts = alerts;
        this.settle = settle;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WebhookResult process(String webhookId, String rawBody) {
        if (webhooks.existsByWebhookId(webhookId)) {
            return new WebhookResult(false);
        }
        JsonNode root;
        try {
            root = json.readTree(rawBody);
        } catch (Exception exception) {
            remember(webhookId, webhookId, null, "unknown_payload", null, null, null, "payload is not json");
            return new WebhookResult(false);
        }
        JsonNode data = root.hasNonNull("data") ? root.get("data") : root;
        String type = text(root, "type");
        if ("payment_link.status_updated".equals(type) && !"paid".equalsIgnoreCase(text(data, "status"))) {
            remember(webhookId, webhookId, null, null, null, null, null, null);
            return new WebhookResult(false);
        }
        if (!"codi.qr.payment_received".equals(type) && !"payment_link.status_updated".equals(type)) {
            remember(webhookId, webhookId, null, null, null, null, null, null);
            return new WebhookResult(false);
        }
        String transactionId = firstText(data, "transactionId", "id");
        if (transactionId == null) {
            JsonNode transactions = data.get("transactions");
            if (transactions != null && transactions.isArray() && !transactions.isEmpty()) {
                transactionId = text(transactions.get(0), "id");
            }
        }
        if (transactionId == null || transactionId.isBlank()) {
            remember(webhookId, webhookId, null, "unknown_payload", null, null, null, "transaction id is missing");
            return new WebhookResult(false);
        }
        BigDecimal received = receivedAmount(data);
        String codiQrId = text(data, "codiQrId");
        String paymentLinkId = text(data, "paymentLinkId");
        Integer numRef = number(data.get("numRef"));
        Optional<Order> found = locate(data, codiQrId, paymentLinkId, numRef);
        if (found.isEmpty()) {
            remember(webhookId, transactionId, null, "unknown_order", null, received, transactionId, "order was not found");
            return new WebhookResult(false);
        }
        Order order = found.get();
        if (mismatched(order, codiQrId, paymentLinkId, numRef)) {
            remember(webhookId, transactionId, order, "reference_mismatch", order.amount(), received, transactionId, "reference does not match the order");
            return new WebhookResult(false);
        }
        if (received == null || order.amount().compareTo(received) != 0) {
            remember(webhookId, transactionId, order, "amount_mismatch", order.amount(), received, transactionId, "amount does not match the order");
            return new WebhookResult(false);
        }
        if (order.status() == OrderStatus.PAID || order.status() == OrderStatus.TICKET_SENT) {
            if (transactionId.equals(order.trackingKey())) {
                return finish(webhookId, transactionId, order, false);
            }
            remember(
                    webhookId,
                    transactionId,
                    order,
                    "duplicate_payment",
                    order.amount(),
                    received,
                    transactionId,
                    "Pago duplicado detectado (folio " + transactionId + ") — necesita revisión en backoffice");
            return new WebhookResult(false);
        }
        if (order.status() != OrderStatus.PENDING_PAYMENT || holdElapsed(order)) {
            remember(webhookId, transactionId, order, "late_payment", order.amount(), received, transactionId, "payment arrived after the order expired");
            return new WebhookResult(false);
        }
        return finish(webhookId, transactionId, order, false);
    }

    private WebhookResult finish(String webhookId, String transactionId, Order order, boolean ignoreHold) {
        if (!settle.settle(order, transactionId, ignoreHold)) {
            return new WebhookResult(true);
        }
        webhooks.save(new ProcessedWebhook(webhookId, transactionId));
        return new WebhookResult(false);
    }

    private boolean holdElapsed(Order order) {
        return order.expiresAt() != null && !OffsetDateTime.now(clock).isBefore(order.expiresAt());
    }

    private Optional<Order> locate(JsonNode data, String codiQrId, String paymentLinkId, Integer numRef) {
        if (codiQrId != null) {
            Optional<Order> byQr = orders.findByCodiQrId(codiQrId);
            if (byQr.isPresent()) {
                return byQr;
            }
        }
        if (paymentLinkId != null) {
            Optional<Order> byLink = orders.findByPaymentLinkId(paymentLinkId);
            if (byLink.isPresent()) {
                return byLink;
            }
        }
        if (numRef != null) {
            Optional<Order> byRef = orders.findByRefNumber(numRef);
            if (byRef.isPresent()) {
                return byRef;
            }
        }
        String orderId = firstText(data, "orderId", "clientReferenceId");
        JsonNode metadata = data.get("metadata");
        if (orderId == null && metadata != null) {
            orderId = text(metadata, "orderId");
        }
        if (orderId == null) {
            return Optional.empty();
        }
        try {
            return orders.findById(UUID.fromString(orderId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static boolean mismatched(Order order, String codiQrId, String paymentLinkId, Integer numRef) {
        if (codiQrId != null && order.codiQrId() != null && !codiQrId.equals(order.codiQrId())) {
            return true;
        }
        if (paymentLinkId != null && order.paymentLinkId() != null && !paymentLinkId.equals(order.paymentLinkId())) {
            return true;
        }
        return numRef != null && numRef != order.refNumber();
    }

    private static BigDecimal receivedAmount(JsonNode data) {
        JsonNode transactions = data.get("transactions");
        if (transactions != null && transactions.isArray() && !transactions.isEmpty()) {
            JsonNode first = transactions.get(0);
            JsonNode breakdown = first.get("amountBreakdown");
            if (breakdown != null && breakdown.hasNonNull("base")) {
                return new BigDecimal(breakdown.get("base").asText());
            }
            if (first.hasNonNull("amount")) {
                return new BigDecimal(first.get("amount").asText());
            }
        }
        if (data.hasNonNull("amount")) {
            return new BigDecimal(data.get("amount").asText());
        }
        return null;
    }

    private void remember(
            String webhookId,
            String transactionId,
            Order order,
            String reason,
            BigDecimal expected,
            BigDecimal received,
            String trackingKey,
            String detail
    ) {
        if (reason != null) {
            alerts.save(new PaymentAlert(
                    UUID.randomUUID(),
                    order == null ? null : order.id(),
                    webhookId,
                    reason,
                    expected,
                    received,
                    trackingKey,
                    detail,
                    OffsetDateTime.now(clock)));
        }
        webhooks.save(new ProcessedWebhook(webhookId, transactionId));
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static Integer number(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String raw = node.asText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Integer.valueOf(raw);
    }
}
