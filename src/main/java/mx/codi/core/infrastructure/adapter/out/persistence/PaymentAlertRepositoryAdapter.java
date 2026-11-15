package mx.codi.core.infrastructure.adapter.out.persistence;

import java.util.List;
import mx.codi.core.domain.model.PaymentAlert;
import mx.codi.core.domain.port.out.PaymentAlertRepositoryPort;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentAlertRepositoryAdapter implements PaymentAlertRepositoryPort {

    private final PaymentAlertJpaRepository alerts;

    public PaymentAlertRepositoryAdapter(PaymentAlertJpaRepository alerts) {
        this.alerts = alerts;
    }

    @Override
    public PaymentAlert save(PaymentAlert alert) {
        return alerts.save(PaymentAlertJpaEntity.from(alert)).toDomain();
    }

    @Override
    public List<PaymentAlert> findAll() {
        return alerts.findAll().stream().map(PaymentAlertJpaEntity::toDomain).toList();
    }
}
