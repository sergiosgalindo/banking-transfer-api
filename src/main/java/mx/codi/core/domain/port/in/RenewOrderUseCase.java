package mx.codi.core.domain.port.in;

import java.util.UUID;

public interface RenewOrderUseCase {

    ChargedOrder renew(UUID orderId);
}
