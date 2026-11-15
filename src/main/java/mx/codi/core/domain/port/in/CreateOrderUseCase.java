package mx.codi.core.domain.port.in;

import java.util.UUID;
public interface CreateOrderUseCase {

    ChargedOrder create(CreateOrderCommand command);

    record CreateOrderCommand(UUID ticketTypeId, int quantity, String customerName, String customerPhone) {
    }
}
