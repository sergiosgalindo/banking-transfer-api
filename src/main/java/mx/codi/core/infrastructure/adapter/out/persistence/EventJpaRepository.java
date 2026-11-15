package mx.codi.core.infrastructure.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventJpaRepository extends JpaRepository<EventJpaEntity, UUID> {

    @Query(value = """
            SELECT DISTINCT to_char(CAST(e.starts_at AT TIME ZONE :zone AS date), 'YYYY-MM-DD') AS event_date
            FROM event e
            WHERE CAST(e.starts_at AT TIME ZONE :zone AS date) >= :today
              AND EXISTS (
                  SELECT 1
                  FROM ticket_type t
                  WHERE t.event_id = e.id
                    AND (t.capacity - t.sold - t.reserved) > 0
              )
            ORDER BY event_date
            """, nativeQuery = true)
    List<String> findUpcomingDates(@Param("today") LocalDate today, @Param("zone") String zone);

    @Query(value = """
            SELECT *
            FROM event e
            WHERE CAST(e.starts_at AT TIME ZONE :zone AS date) = :date
            ORDER BY e.starts_at
            """, nativeQuery = true)
    List<EventJpaEntity> findByLocalDate(@Param("date") LocalDate date, @Param("zone") String zone);
}
