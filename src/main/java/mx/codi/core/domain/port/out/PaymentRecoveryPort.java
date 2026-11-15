package mx.codi.core.domain.port.out;

import mx.codi.core.domain.model.Order;

public interface PaymentRecoveryPort {

    boolean trySettle(Order order);
}
