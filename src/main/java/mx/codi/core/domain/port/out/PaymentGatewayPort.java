package mx.codi.core.domain.port.out;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface PaymentGatewayPort {

    CodiQr createCodiQr(CodiQrRequest request);

    PaymentLink createPaymentLink(PaymentLinkRequest request);

    List<CodiPayment> findCodiQrPayments(String codiQrId);

    List<CodiPayment> findPaymentLinkPayments(String paymentLinkId);

    record CodiQrRequest(
            BigDecimal amount,
            String concept,
            int expirationSeconds,
            int numRef,
            Map<String, String> metadata
    ) {
    }

    record CodiQr(String id, String qrString) {
    }

    record PaymentLinkRequest(
            BigDecimal amount,
            String clientReferenceId,
            String successUrl,
            String backUrl,
            Map<String, String> metadata
    ) {
    }

    record PaymentLink(String id, String url) {
    }

    record CodiPayment(String transactionId, String status, BigDecimal amount, BigDecimal baseAmount) {
    }
}
