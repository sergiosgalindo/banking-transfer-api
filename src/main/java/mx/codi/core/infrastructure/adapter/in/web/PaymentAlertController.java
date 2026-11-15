package mx.codi.core.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import mx.codi.core.domain.model.PaymentAlert;
import mx.codi.core.domain.port.out.PaymentAlertRepositoryPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-alerts")
public class PaymentAlertController {

    private final PaymentAlertRepositoryPort alerts;

    public PaymentAlertController(PaymentAlertRepositoryPort alerts) {
        this.alerts = alerts;
    }

    @GetMapping
    public List<PaymentAlertResponse> list() {
        return alerts.findAll().stream()
                .sorted(Comparator.comparing(PaymentAlert::createdAt).reversed())
                .map(PaymentAlertResponse::from)
                .toList();
    }

    public record PaymentAlertResponse(
            UUID id,
            UUID orderId,
            String webhookId,
            String reason,
            BigDecimal expectedAmount,
            BigDecimal receivedAmount,
            String trackingKey,
            String detail,
            OffsetDateTime createdAt
    ) {
        static PaymentAlertResponse from(PaymentAlert alert) {
            return new PaymentAlertResponse(
                    alert.id(),
                    alert.orderId(),
                    alert.webhookId(),
                    alert.reason(),
                    alert.expectedAmount(),
                    alert.receivedAmount(),
                    alert.trackingKey(),
                    alert.detail(),
                    alert.createdAt());
        }
    }
}
