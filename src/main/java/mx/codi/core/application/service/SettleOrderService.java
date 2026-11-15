package mx.codi.core.application.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import mx.codi.core.domain.model.Event;
import mx.codi.core.domain.model.MexicoCity;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.model.OrderStatus;
import mx.codi.core.domain.model.PaymentAlert;
import mx.codi.core.domain.port.out.EventRepositoryPort;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import mx.codi.core.domain.port.out.PaymentAlertRepositoryPort;
import mx.codi.core.domain.port.out.TicketNotificationPort;
import mx.codi.core.domain.port.out.TicketNotificationPort.TicketMessage;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettleOrderService {

    private final OrderRepositoryPort orders;
    private final TicketTypeRepositoryPort ticketTypes;
    private final EventRepositoryPort events;
    private final TicketNotificationPort notifications;
    private final PaymentAlertRepositoryPort alerts;
    private final Clock clock;

    public SettleOrderService(
            OrderRepositoryPort orders,
            TicketTypeRepositoryPort ticketTypes,
            EventRepositoryPort events,
            TicketNotificationPort notifications,
            PaymentAlertRepositoryPort alerts,
            Clock clock
    ) {
        this.orders = orders;
        this.ticketTypes = ticketTypes;
        this.events = events;
        this.notifications = notifications;
        this.alerts = alerts;
        this.clock = clock;
    }

    @Transactional
    public boolean settle(Order order, String trackingKey, boolean ignoreHold) {
        if (order.status() == OrderStatus.TICKET_SENT && trackingKey.equals(order.trackingKey())) {
            return true;
        }
        Order paid = order;
        if (order.status() == OrderStatus.PENDING_PAYMENT) {
            OffsetDateTime now = OffsetDateTime.now(clock);
            Order marked = ignoreHold ? order.settleHold(now, trackingKey) : order.markPaid(now, trackingKey);
            String accessCode = newAccessCode();
            paid = orders.save(marked.issueAccess(accessCode));
            ticketTypes.confirmSale(paid.ticketTypeId(), paid.quantity());
        } else if (order.status() != OrderStatus.PAID || !trackingKey.equals(order.trackingKey())) {
            return false;
        }
        if (!notify(paid)) {
            return false;
        }
        orders.save(paid.markTicketSent());
        return true;
    }

    private static final DateTimeFormatter CDMX_FMT = DateTimeFormatter.ofPattern(
            "EEE dd MMM yyyy · HH:mm 'hrs'", Locale.forLanguageTag("es-MX"));

    private boolean notify(Order paid) {
        Event event = events.findById(paid.eventId()).orElse(null);
        String eventName = event == null ? "Evento" : event.name();
        String venue = event == null ? "" : event.venue();
        String when = event == null
                ? ""
                : event.startsAt().atZoneSameInstant(MexicoCity.ZONE).format(CDMX_FMT);
        String payload = "{\"accessCode\":\"" + paid.accessCode()
                + "\",\"orderId\":\"" + paid.id()
                + "\",\"eventId\":\"" + paid.eventId()
                + "\",\"quantity\":" + paid.quantity() + "}";
        String message = "✅ *¡Pago CoDi® confirmado! Aquí está tu boleto*\n\n"
                + "🎶 *Evento:* " + eventName + "\n"
                + (venue.isBlank() ? "" : "📍 *Lugar:* " + venue + "\n")
                + (when.isBlank() ? "" : "📅 *Fecha y hora:* " + when + "\n")
                + "👤 *Titular:* " + paid.customerName() + " (" + paid.customerPhone() + ")\n"
                + "🎟️ *Acceso válido por:* *" + paid.quantity() + " entrada(s)*\n"
                + "🧾 *Orden:* #" + paid.refNumber() + " · *Código:* `" + paid.accessCode() + "`\n\n"
                + "🚪 _Presenta este código QR en la puerta del evento (válido para un solo ingreso)._";
        try {
            notifications.sendTicket(new TicketMessage(
                    paid.id().toString(),
                    paid.customerPhone(),
                    paid.customerName(),
                    paid.quantity(),
                    paid.accessCode(),
                    payload,
                    message));
            return true;
        } catch (RuntimeException exception) {
            alerts.save(new PaymentAlert(
                    UUID.randomUUID(),
                    paid.id(),
                    null,
                    "notification_failed",
                    paid.amount(),
                    paid.amount(),
                    paid.trackingKey(),
                    "the ticket could not be delivered",
                    OffsetDateTime.now(clock)));
            return false;
        }
    }

    private static String newAccessCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }
}
