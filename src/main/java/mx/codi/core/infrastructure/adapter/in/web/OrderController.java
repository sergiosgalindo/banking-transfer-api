package mx.codi.core.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.model.OrderStatus;
import mx.codi.core.domain.model.PaymentAlert;
import mx.codi.core.domain.port.in.CancelOrderUseCase;
import mx.codi.core.domain.port.in.ChargedOrder;
import mx.codi.core.domain.port.in.CreateOrderUseCase;
import mx.codi.core.domain.port.in.CreateOrderUseCase.CreateOrderCommand;
import mx.codi.core.domain.port.in.RenewOrderUseCase;
import mx.codi.core.domain.port.out.EventRepositoryPort;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import mx.codi.core.domain.port.out.PaymentAlertRepositoryPort;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderRepositoryPort orders;
    private final EventRepositoryPort events;
    private final TicketTypeRepositoryPort ticketTypes;
    private final PaymentAlertRepositoryPort alerts;
    private final CreateOrderUseCase createOrders;
    private final RenewOrderUseCase renewOrders;
    private final CancelOrderUseCase cancelOrders;

    public OrderController(
            OrderRepositoryPort orders,
            EventRepositoryPort events,
            TicketTypeRepositoryPort ticketTypes,
            PaymentAlertRepositoryPort alerts,
            CreateOrderUseCase createOrders,
            RenewOrderUseCase renewOrders,
            CancelOrderUseCase cancelOrders
    ) {
        this.orders = orders;
        this.events = events;
        this.ticketTypes = ticketTypes;
        this.alerts = alerts;
        this.createOrders = createOrders;
        this.renewOrders = renewOrders;
        this.cancelOrders = cancelOrders;
    }

    @PostMapping
    public ChargedOrderResponse create(@RequestBody CreateOrderRequest request) {
        return ChargedOrderResponse.from(createOrders.create(new CreateOrderCommand(
                request.ticketTypeId(),
                request.quantity(),
                request.customerName(),
                request.customerPhone())));
    }

    @PostMapping("/{id}/renew")
    public ChargedOrderResponse renew(@PathVariable UUID id) {
        return ChargedOrderResponse.from(renewOrders.renew(id));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return response(cancelOrders.cancel(id));
    }

    @GetMapping
    public List<OrderResponse> list(
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String phone
    ) {
        return orders.findAll().stream()
                .filter(order -> eventId == null || eventId.equals(order.eventId()))
                .filter(order -> phone == null || phone.equals(order.customerPhone()))
                .filter(order -> status == null || wire(order.status()).equals(status))
                .sorted(Comparator.comparingInt(Order::refNumber))
                .map(this::response)
                .toList();
    }

    private OrderResponse response(Order order) {
        String eventName = events.findById(order.eventId()).map(event -> event.name()).orElse(null);
        String ticketTypeName = ticketTypes.findById(order.ticketTypeId()).map(ticket -> ticket.name()).orElse(null);
        List<PaymentAlert> dupes = alerts.findAll().stream()
                .filter(alert -> order.id().equals(alert.orderId()) && "duplicate_payment".equals(alert.reason()))
                .toList();
        String reviewNote = null;
        if (!dupes.isEmpty()) {
            String folios = dupes.stream()
                    .map(PaymentAlert::trackingKey)
                    .filter(key -> key != null && !key.isBlank())
                    .collect(Collectors.joining(", "));
            reviewNote = "Pago duplicado detectado"
                    + (folios.isEmpty() ? "" : " (folio " + folios + ")")
                    + " — necesita revisión en backoffice";
        }
        return OrderResponse.from(order, eventName, ticketTypeName, reviewNote);
    }

    private static String wire(OrderStatus status) {
        return status.wireValue();
    }

    public record CreateOrderRequest(UUID ticketTypeId, int quantity, String customerName, String customerPhone) {
    }

    public record ChargedOrderResponse(
            UUID orderId,
            int quantity,
            BigDecimal amount,
            String qrString,
            String paymentLinkUrl,
            OffsetDateTime expiresAt
    ) {
        static ChargedOrderResponse from(ChargedOrder charged) {
            Order order = charged.order();
            return new ChargedOrderResponse(
                    order.id(),
                    order.quantity(),
                    order.amount(),
                    charged.qrString(),
                    charged.paymentLinkUrl(),
                    order.expiresAt());
        }
    }

    public record OrderResponse(
            UUID id,
            int refNumber,
            UUID eventId,
            UUID ticketTypeId,
            int quantity,
            String customerName,
            String customerPhone,
            BigDecimal amount,
            String status,
            String codiQrId,
            String paymentLinkId,
            String paymentLinkUrl,
            String trackingKey,
            OffsetDateTime expiresAt,
            String accessCode,
            OffsetDateTime checkedInAt,
            OffsetDateTime createdAt,
            OffsetDateTime paidAt,
            String eventName,
            String ticketTypeName,
            String reviewNote
    ) {
        static OrderResponse from(Order order, String eventName, String ticketTypeName, String reviewNote) {
            return new OrderResponse(
                    order.id(),
                    order.refNumber(),
                    order.eventId(),
                    order.ticketTypeId(),
                    order.quantity(),
                    order.customerName(),
                    order.customerPhone(),
                    order.amount(),
                    wire(order.status()),
                    order.codiQrId(),
                    order.paymentLinkId(),
                    order.paymentLinkUrl(),
                    order.trackingKey(),
                    order.expiresAt(),
                    order.accessCode(),
                    order.checkedInAt(),
                    order.createdAt(),
                    order.paidAt(),
                    eventName,
                    ticketTypeName,
                    reviewNote);
        }
    }
}
