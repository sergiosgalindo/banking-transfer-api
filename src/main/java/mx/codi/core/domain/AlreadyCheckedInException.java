package mx.codi.core.domain;

import java.time.OffsetDateTime;

public class AlreadyCheckedInException extends RuntimeException {

    private final OffsetDateTime checkedInAt;

    public AlreadyCheckedInException(OffsetDateTime checkedInAt) {
        super("ticket already checked in");
        this.checkedInAt = checkedInAt;
    }

    public OffsetDateTime checkedInAt() {
        return checkedInAt;
    }
}
