package mx.codi.core.infrastructure.adapter.out.pey;

import java.util.List;
import mx.codi.core.domain.port.out.PaymentGatewayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "codi.pey.mode", havingValue = "fake", matchIfMissing = true)
public class FakePaymentGatewayAdapter implements PaymentGatewayPort {

    @Override
    public CodiQr createCodiQr(CodiQrRequest request) {
        return new CodiQr(
                "qr-" + request.numRef(),
                "CODI|" + request.amount().toPlainString() + "|" + request.numRef());
    }

    @Override
    public PaymentLink createPaymentLink(PaymentLinkRequest request) {
        return new PaymentLink(
                "link-" + request.clientReferenceId(),
                "https://pay.example/link/" + request.clientReferenceId());
    }

    @Override
    public List<CodiPayment> findCodiQrPayments(String codiQrId) {
        return List.of();
    }

    @Override
    public List<CodiPayment> findPaymentLinkPayments(String paymentLinkId) {
        return List.of();
    }
}
