package mx.codi.core.application.service;

import java.util.Map;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.port.in.ChargedOrder;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import mx.codi.core.domain.port.out.PaymentGatewayPort;
import mx.codi.core.domain.port.out.PaymentGatewayPort.CodiQr;
import mx.codi.core.domain.port.out.PaymentGatewayPort.CodiQrRequest;
import mx.codi.core.domain.port.out.PaymentGatewayPort.PaymentLink;
import mx.codi.core.domain.port.out.PaymentGatewayPort.PaymentLinkRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentCharger {

    private final OrderRepositoryPort orders;
    private final PaymentGatewayPort payments;
    private final String successUrl;
    private final String backUrl;

    public PaymentCharger(
            OrderRepositoryPort orders,
            PaymentGatewayPort payments,
            @Value("${codi.pey.success-url}") String successUrl,
            @Value("${codi.pey.back-url}") String backUrl
    ) {
        this.orders = orders;
        this.payments = payments;
        this.successUrl = successUrl;
        this.backUrl = backUrl;
    }

    public ChargedOrder charge(Order pending) {
        Map<String, String> metadata = Map.of(
                "orderId", pending.id().toString(),
                "quantity", Integer.toString(pending.quantity()),
                "customerName", pending.customerName(),
                "customerPhone", pending.customerPhone());
        CodiQr qr = payments.createCodiQr(new CodiQrRequest(
                pending.amount(),
                "Boletos x" + pending.quantity(),
                600,
                pending.refNumber(),
                metadata));
        PaymentLink link = payments.createPaymentLink(new PaymentLinkRequest(
                pending.amount(),
                pending.id().toString(),
                successUrl,
                backUrl,
                metadata));
        Order stored = orders.save(pending.attachInstruments(qr.id(), link.id(), link.url()));
        return new ChargedOrder(stored, qr.qrString(), link.url());
    }
}
