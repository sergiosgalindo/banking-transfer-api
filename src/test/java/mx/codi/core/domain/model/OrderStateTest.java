package mx.codi.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.codi.core.domain.AlreadyCheckedInException;
import mx.codi.core.domain.InvalidOrderTransitionException;
import org.junit.jupiter.api.Test;

class OrderStateTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-14T18:00:00Z");

    @Test
    void paymentHoldLastsTenMinutes() {
        assertThat(Order.PAYMENT_HOLD).hasSeconds(600);
    }

    @Test
    void onlyAPendingOrderCanExpireOrBeCancelled() {
        Order pending = pending(NOW.plus(Order.PAYMENT_HOLD));

        assertThat(pending.expire().status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(pending.cancel().status()).isEqualTo(OrderStatus.CANCELLED);

        Order paid = pending.markPaid(NOW);
        assertThatThrownBy(paid::expire).isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(paid::cancel).isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    void anOrderCannotBePaidOnceTheHoldHasElapsed() {
        Order pending = pending(NOW);

        assertThatThrownBy(() -> pending.markPaid(NOW))
                .isInstanceOf(InvalidOrderTransitionException.class)
                .hasMessage("expired order cannot be paid");
        assertThatThrownBy(() -> pending.markPaid(NOW.plusSeconds(1)))
                .isInstanceOf(InvalidOrderTransitionException.class)
                .hasMessage("expired order cannot be paid");

        Order paid = pending.markPaid(NOW.minusNanos(1));
        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.paidAt()).isEqualTo(NOW.minusNanos(1));
        assertThat(paid.markTicketSent().status()).isEqualTo(OrderStatus.TICKET_SENT);
    }

    @Test
    void anExpiredOrderCannotBePaidEvenIfTheTimestampIsStillAhead() {
        Order expired = pending(NOW.plusMinutes(5)).expire();

        assertThatThrownBy(() -> expired.markPaid(NOW))
                .isInstanceOf(InvalidOrderTransitionException.class)
                .hasMessage("only a pending order can be paid");
    }

    @Test
    void checkInIsStoredOnceForAPaidOrSentTicket() {
        Order paid = pending(NOW.plusMinutes(10)).markPaid(NOW);
        Order admitted = paid.checkIn(NOW);
        Order sent = paid.markTicketSent().checkIn(NOW.plusMinutes(1));

        assertThat(admitted.status()).isEqualTo(OrderStatus.PAID);
        assertThat(admitted.checkedInAt()).isEqualTo(NOW);
        assertThat(sent.status()).isEqualTo(OrderStatus.TICKET_SENT);
        assertThat(sent.checkedInAt()).isEqualTo(NOW.plusMinutes(1));

        AlreadyCheckedInException secondScan = catchThrowableOfType(
                AlreadyCheckedInException.class,
                () -> admitted.checkIn(NOW.plusMinutes(5)));
        assertThat(secondScan.checkedInAt()).isEqualTo(NOW);

        assertThatThrownBy(() -> pending(NOW.plusMinutes(10)).checkIn(NOW))
                .isInstanceOf(InvalidOrderTransitionException.class)
                .hasMessage("only a paid order can be checked in");
    }

    private static Order pending(OffsetDateTime expiresAt) {
        return new Order(
                UUID.randomUUID(),
                7,
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                "Ana López",
                "525512345678",
                new BigDecimal("360.00"),
                OrderStatus.PENDING_PAYMENT,
                null,
                null,
                null,
                null,
                expiresAt,
                null,
                null,
                NOW,
                null);
    }
}
