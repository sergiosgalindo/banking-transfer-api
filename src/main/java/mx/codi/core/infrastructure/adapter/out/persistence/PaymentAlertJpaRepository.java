package mx.codi.core.infrastructure.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAlertJpaRepository extends JpaRepository<PaymentAlertJpaEntity, UUID> {
}
