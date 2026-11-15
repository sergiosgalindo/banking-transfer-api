package mx.codi.core.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookJpaRepository extends JpaRepository<ProcessedWebhookJpaEntity, String> {
}
