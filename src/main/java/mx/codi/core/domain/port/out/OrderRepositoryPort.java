package mx.codi.core.domain.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.codi.core.domain.model.Order;

public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    Optional<Order> findByRefNumber(int refNumber);

    Optional<Order> findByAccessCode(String accessCode);

    Optional<Order> findByCodiQrId(String codiQrId);

    Optional<Order> findByPaymentLinkId(String paymentLinkId);

    List<Order> findPendingExpired(OffsetDateTime now);

    List<Order> findAll();

    int nextRefNumber();
}
