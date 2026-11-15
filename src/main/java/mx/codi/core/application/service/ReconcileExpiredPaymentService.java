package mx.codi.core.application.service;

import java.math.BigDecimal;
import java.util.List;
import mx.codi.core.domain.PaymentGatewayException;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.model.ProcessedWebhook;
import mx.codi.core.domain.port.out.PaymentAlertRepositoryPort;
import mx.codi.core.domain.port.out.PaymentGatewayPort;
import mx.codi.core.domain.port.out.PaymentGatewayPort.CodiPayment;
import mx.codi.core.domain.port.out.PaymentRecoveryPort;
import mx.codi.core.domain.port.out.ProcessedWebhookRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class ReconcileExpiredPaymentService implements PaymentRecoveryPort {

    private final PaymentGatewayPort payments;
    private final PaymentAlertRepositoryPort alerts;
    private final ProcessedWebhookRepositoryPort webhooks;
    private final SettleOrderService settle;

    public ReconcileExpiredPaymentService(
            PaymentGatewayPort payments,
            PaymentAlertRepositoryPort alerts,
            ProcessedWebhookRepositoryPort webhooks,
            SettleOrderService settle
    ) {
        this.payments = payments;
        this.alerts = alerts;
        this.webhooks = webhooks;
        this.settle = settle;
    }

    @Override
    public boolean trySettle(Order order) {
        boolean alreadyLate = alerts.findAll().stream()
                .anyMatch(alert -> order.id().equals(alert.orderId()) && "late_payment".equals(alert.reason()));
        if (alreadyLate) {
            return false;
        }
        CodiPayment paid = null;
        if (order.codiQrId() != null) {
            paid = matching(order, safeQr(order.codiQrId()));
        }
        if (paid == null && order.paymentLinkId() != null) {
            paid = matching(order, safeLink(order.paymentLinkId()));
        }
        if (paid == null) {
            return false;
        }
        String webhookId = "reconcile-" + paid.transactionId();
        if (webhooks.existsByWebhookId(webhookId)) {
            return true;
        }
        if (!settle.settle(order, paid.transactionId(), true)) {
            return true;
        }
        webhooks.save(new ProcessedWebhook(webhookId, paid.transactionId()));
        return true;
    }

    private List<CodiPayment> safeQr(String id) {
        try {
            return payments.findCodiQrPayments(id);
        } catch (PaymentGatewayException exception) {
            return List.of();
        }
    }

    private List<CodiPayment> safeLink(String id) {
        try {
            return payments.findPaymentLinkPayments(id);
        } catch (PaymentGatewayException exception) {
            return List.of();
        }
    }

    private static CodiPayment matching(Order order, List<CodiPayment> payments) {
        for (CodiPayment payment : payments) {
            if (!"PAID".equalsIgnoreCase(payment.status()) || payment.transactionId() == null) {
                continue;
            }
            BigDecimal comparable = payment.baseAmount() != null ? payment.baseAmount() : payment.amount();
            if (comparable != null && comparable.compareTo(order.amount()) == 0) {
                return payment;
            }
        }
        return null;
    }
}
