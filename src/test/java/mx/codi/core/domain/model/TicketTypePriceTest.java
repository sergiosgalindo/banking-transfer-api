package mx.codi.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketTypePriceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-14T18:00:00Z");
    private static final OffsetDateTime PRESALE_ENDS = OffsetDateTime.parse("2026-11-16T00:00:00Z");

    @Test
    void presaleAppliesBeforeItEndsWhileSeatsRemain() {
        TicketType ticket = ticket(new BigDecimal("250.00"), new BigDecimal("180.00"), PRESALE_ENDS, 10, 0, 0);

        assertThat(ticket.unitPrice(NOW)).isEqualByComparingTo("180.00");
        assertThat(ticket.total(NOW, 2)).isEqualByComparingTo("360.00");
    }

    @Test
    void regularPriceAtTheExactEndAndWhenPresaleDataIsMissing() {
        TicketType ending = ticket(new BigDecimal("250.00"), new BigDecimal("180.00"), NOW, 10, 0, 0);
        TicketType after = ticket(new BigDecimal("250.00"), new BigDecimal("180.00"), NOW.minusSeconds(1), 10, 0, 0);
        TicketType noEnd = ticket(new BigDecimal("250.00"), new BigDecimal("180.00"), null, 10, 0, 0);
        TicketType noPresale = ticket(new BigDecimal("250.00"), null, PRESALE_ENDS, 10, 0, 0);

        assertThat(ending.total(NOW, 2)).isEqualByComparingTo("500.00");
        assertThat(after.unitPrice(NOW)).isEqualByComparingTo("250.00");
        assertThat(noEnd.unitPrice(NOW)).isEqualByComparingTo("250.00");
        assertThat(noPresale.unitPrice(NOW)).isEqualByComparingTo("250.00");
    }

    @Test
    void soldOutPresaleUsesTheRegularPrice() {
        TicketType soldOut = ticket(new BigDecimal("250.00"), new BigDecimal("180.00"), PRESALE_ENDS, 4, 4, 0);

        assertThat(soldOut.available()).isZero();
        assertThat(soldOut.unitPrice(NOW)).isEqualByComparingTo("250.00");
    }

    @Test
    void reservingTheLastSeatsDropsThePresaleForTheNextBuyer() {
        TicketType ticket = ticket(new BigDecimal("250.00"), new BigDecimal("180.00"), PRESALE_ENDS, 2, 0, 0);

        assertThat(ticket.total(NOW, 2)).isEqualByComparingTo("360.00");

        TicketType reserved = ticket.reserve(2);
        assertThat(reserved.available()).isZero();
        assertThat(reserved.unitPrice(NOW)).isEqualByComparingTo("250.00");
        assertThat(reserved.release(2).available()).isEqualTo(2);

        assertThatThrownBy(() -> ticket.reserve(3)).isInstanceOf(IllegalArgumentException.class);
    }

    private static TicketType ticket(
            BigDecimal regularPrice,
            BigDecimal presalePrice,
            OffsetDateTime presaleEndsAt,
            int capacity,
            int sold,
            int reserved
    ) {
        return new TicketType(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "General",
                regularPrice,
                presalePrice,
                presaleEndsAt,
                capacity,
                sold,
                reserved);
    }
}
