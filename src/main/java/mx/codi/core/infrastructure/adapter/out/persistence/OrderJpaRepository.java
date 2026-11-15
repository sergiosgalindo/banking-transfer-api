package mx.codi.core.infrastructure.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    Optional<OrderJpaEntity> findByRefNumber(int refNumber);

    Optional<OrderJpaEntity> findByAccessCode(String accessCode);

    Optional<OrderJpaEntity> findByCodiQrId(String codiQrId);

    Optional<OrderJpaEntity> findByPaymentLinkId(String paymentLinkId);

    @Query(value = """
            SELECT *
            FROM orders
            WHERE status = 'pendiente_de_pago'
              AND expires_at <= :now
            """, nativeQuery = true)
    List<OrderJpaEntity> findPendingExpired(@Param("now") OffsetDateTime now);

    @Query(value = "SELECT COALESCE(MAX(ref_number), 0) + 1 FROM orders", nativeQuery = true)
    long nextRefNumber();
}
