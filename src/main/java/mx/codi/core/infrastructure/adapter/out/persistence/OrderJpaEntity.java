package mx.codi.core.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.model.OrderStatus;

@Entity
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    private UUID id;

    @Column(name = "ref_number", nullable = false, unique = true)
    private int refNumber;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 32)
    private String customerPhone;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 40)
    private OrderStatus status;

    @Column(name = "codi_qr_id", length = 120)
    private String codiQrId;

    @Column(name = "payment_link_id", length = 120)
    private String paymentLinkId;

    @Column(name = "payment_link_url", length = 1000)
    private String paymentLinkUrl;

    @Column(name = "tracking_key", length = 120)
    private String trackingKey;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "access_code", length = 120)
    private String accessCode;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    protected OrderJpaEntity() {
    }

    public static OrderJpaEntity from(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.id = order.id();
        entity.refNumber = order.refNumber();
        entity.eventId = order.eventId();
        entity.ticketTypeId = order.ticketTypeId();
        entity.quantity = order.quantity();
        entity.customerName = order.customerName();
        entity.customerPhone = order.customerPhone();
        entity.amount = order.amount();
        entity.status = order.status();
        entity.codiQrId = order.codiQrId();
        entity.paymentLinkId = order.paymentLinkId();
        entity.paymentLinkUrl = order.paymentLinkUrl();
        entity.trackingKey = order.trackingKey();
        entity.expiresAt = order.expiresAt();
        entity.accessCode = order.accessCode();
        entity.checkedInAt = order.checkedInAt();
        entity.createdAt = order.createdAt();
        entity.paidAt = order.paidAt();
        return entity;
    }

    public Order toDomain() {
        return new Order(
                id,
                refNumber,
                eventId,
                ticketTypeId,
                quantity,
                customerName,
                customerPhone,
                amount,
                status,
                codiQrId,
                paymentLinkId,
                paymentLinkUrl,
                trackingKey,
                expiresAt,
                accessCode,
                checkedInAt,
                createdAt,
                paidAt);
    }
}
