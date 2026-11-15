package mx.codi.core.domain.port.in;

import java.time.OffsetDateTime;

public interface CheckInTicketUseCase {

    CheckInResult checkIn(String accessCode);

    record CheckInResult(
            boolean admitted,
            String reason,
            String customerName,
            String customerPhone,
            Integer quantity,
            OffsetDateTime checkedInAt
    ) {
    }
}
