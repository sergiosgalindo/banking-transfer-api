package mx.codi.core.domain.port.out;

import java.util.List;
import mx.codi.core.domain.model.PaymentAlert;

public interface PaymentAlertRepositoryPort {

    PaymentAlert save(PaymentAlert alert);

    List<PaymentAlert> findAll();
}
