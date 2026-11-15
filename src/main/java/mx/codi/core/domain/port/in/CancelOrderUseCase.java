package mx.codi.core.domain.port.in;

import java.util.UUID;
import mx.codi.core.domain.model.Order;

public interface CancelOrderUseCase {

    Order cancel(UUID orderId);
}
