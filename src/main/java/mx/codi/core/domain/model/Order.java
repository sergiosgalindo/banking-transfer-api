package mx.codi.core.domain.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import mx.codi.core.domain.AlreadyCheckedInException;
import mx.codi.core.domain.InvalidOrderTransitionException;
import java.util.Objects;
import java.util.UUID;

public record Order(
        UUID id,
        int refNumber,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        String customerName,
        String customerPhone,
        BigDecimal amount,
        OrderStatus status,
        String codiQrId,
        String paymentLinkId,
        String paymentLinkUrl,
        String trackingKey,
        OffsetDateTime expiresAt,
        String accessCode,
        OffsetDateTime checkedInAt,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt
) {
    public Order {
        Objects.requireNonNull(id, "id");
        if (refNumber < 0 || refNumber > 9_999_999) {
            throw new IllegalArgumentException("refNumber must be a number of at most 7 digits");
        }
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(ticketTypeId, "ticketTypeId");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("customerName is required");
        }
        if (customerPhone == null || customerPhone.isBlank()) {
            throw new IllegalArgumentException("customerPhone is required");
        }
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        expiresAt = utc(expiresAt);
        checkedInAt = utc(checkedInAt);
        createdAt = utc(createdAt);
        paidAt = utc(paidAt);
    }

    public static final Duration PAYMENT_HOLD = Duration.ofSeconds(600);

    public Order expire() {
        return transitionTo(OrderStatus.EXPIRED);
    }

    public Order cancel() {
        return transitionTo(OrderStatus.CANCELLED);
    }

    public Order attachInstruments(String codiQrId, String paymentLinkId, String paymentLinkUrl) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderTransitionException("only a pending order can receive payment instruments");
        }
        return new Order(
                id, refNumber, eventId, ticketTypeId, quantity, customerName, customerPhone, amount, status,
                codiQrId, paymentLinkId, paymentLinkUrl, trackingKey, expiresAt, accessCode, checkedInAt, createdAt, paidAt);
    }

    public Order markPaid(OffsetDateTime now) {
        OffsetDateTime instant = utc(now);
        if (expiresAt != null && !instant.isBefore(expiresAt)) {
            throw new InvalidOrderTransitionException("expired order cannot be paid");
        }
        return transitionTo(OrderStatus.PAID, checkedInAt, instant);
    }

    public Order settleHold(OffsetDateTime now, String trackingKey) {
        if (trackingKey == null || trackingKey.isBlank()) {
            throw new IllegalArgumentException("trackingKey is required");
        }
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderTransitionException("only a pending order can be paid");
        }
        OffsetDateTime instant = utc(now);
        return new Order(
                id, refNumber, eventId, ticketTypeId, quantity, customerName, customerPhone, amount,
                OrderStatus.PAID, codiQrId, paymentLinkId, paymentLinkUrl, trackingKey,
                expiresAt, accessCode, checkedInAt, createdAt, instant);
    }

    public Order markPaid(OffsetDateTime now, String trackingKey) {
        if (trackingKey == null || trackingKey.isBlank()) {
            throw new IllegalArgumentException("trackingKey is required");
        }
        Order paid = markPaid(now);
        return new Order(
                paid.id(), paid.refNumber(), paid.eventId(), paid.ticketTypeId(), paid.quantity(),
                paid.customerName(), paid.customerPhone(), paid.amount(), paid.status(),
                paid.codiQrId(), paid.paymentLinkId(), paid.paymentLinkUrl(), trackingKey,
                paid.expiresAt(), paid.accessCode(), paid.checkedInAt(), paid.createdAt(), paid.paidAt());
    }

    public Order issueAccess(String accessCode) {
        if (status != OrderStatus.PAID) {
            throw new InvalidOrderTransitionException("only a paid order can receive an access code");
        }
        if (accessCode == null || accessCode.isBlank()) {
            throw new IllegalArgumentException("accessCode is required");
        }
        if (this.accessCode != null) {
            return this;
        }
        return new Order(
                id, refNumber, eventId, ticketTypeId, quantity, customerName, customerPhone, amount, status,
                codiQrId, paymentLinkId, paymentLinkUrl, trackingKey, expiresAt, accessCode, checkedInAt, createdAt, paidAt);
    }

    public Order markTicketSent() {
        if (status != OrderStatus.PAID) {
            throw new InvalidOrderTransitionException("only a paid order can be sent");
        }
        return copy(OrderStatus.TICKET_SENT, amount, expiresAt, checkedInAt, paidAt);
    }

    public Order checkIn(OffsetDateTime now) {
        if (status != OrderStatus.PAID && status != OrderStatus.TICKET_SENT) {
            throw new InvalidOrderTransitionException("only a paid order can be checked in");
        }
        if (checkedInAt != null) {
            throw new AlreadyCheckedInException(checkedInAt);
        }
        return copy(status, amount, expiresAt, now, paidAt);
    }

    private Order transitionTo(OrderStatus next) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderTransitionException("only a pending order can move to " + next.name().toLowerCase());
        }
        return copy(next, amount, expiresAt, checkedInAt, paidAt);
    }

    private Order transitionTo(OrderStatus next, OffsetDateTime nextCheckedInAt, OffsetDateTime nextPaidAt) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderTransitionException("only a pending order can be paid");
        }
        return copy(next, amount, expiresAt, nextCheckedInAt, nextPaidAt);
    }

    private Order copy(
            OrderStatus nextStatus,
            BigDecimal nextAmount,
            OffsetDateTime nextExpiresAt,
            OffsetDateTime nextCheckedInAt,
            OffsetDateTime nextPaidAt
    ) {
        return new Order(
                id,
                refNumber,
                eventId,
                ticketTypeId,
                quantity,
                customerName,
                customerPhone,
                nextAmount,
                nextStatus,
                codiQrId,
                paymentLinkId,
                paymentLinkUrl,
                trackingKey,
                nextExpiresAt,
                accessCode,
                nextCheckedInAt,
                createdAt,
                nextPaidAt);
    }

    private static OffsetDateTime utc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
