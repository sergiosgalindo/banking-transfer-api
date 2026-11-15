package mx.codi.core.infrastructure.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository orders;

    public OrderRepositoryAdapter(OrderJpaRepository orders) {
        this.orders = orders;
    }

    @Override
    public Order save(Order order) {
        return orders.save(OrderJpaEntity.from(order)).toDomain();
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return orders.findById(id).map(OrderJpaEntity::toDomain);
    }

    @Override
    public Optional<Order> findByRefNumber(int refNumber) {
        return orders.findByRefNumber(refNumber).map(OrderJpaEntity::toDomain);
    }

    @Override
    public List<Order> findAll() {
        return orders.findAll().stream().map(OrderJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<Order> findByAccessCode(String accessCode) {
        return orders.findByAccessCode(accessCode).map(OrderJpaEntity::toDomain);
    }

    @Override
    public Optional<Order> findByCodiQrId(String codiQrId) {
        return orders.findByCodiQrId(codiQrId).map(OrderJpaEntity::toDomain);
    }

    @Override
    public Optional<Order> findByPaymentLinkId(String paymentLinkId) {
        return orders.findByPaymentLinkId(paymentLinkId).map(OrderJpaEntity::toDomain);
    }

    @Override
    public List<Order> findPendingExpired(OffsetDateTime now) {
        return orders.findPendingExpired(now).stream().map(OrderJpaEntity::toDomain).toList();
    }

    @Override
    public int nextRefNumber() {
        long next = orders.nextRefNumber();
        if (next > 9_999_999) {
            throw new IllegalStateException("refNumber exceeded 7 digits");
        }
        return (int) next;
    }
}
