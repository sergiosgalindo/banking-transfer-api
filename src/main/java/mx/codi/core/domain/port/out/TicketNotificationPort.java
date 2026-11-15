package mx.codi.core.domain.port.out;

public interface TicketNotificationPort {

    void sendTicket(TicketMessage message);

    record TicketMessage(
            String orderId,
            String customerPhone,
            String customerName,
            int quantity,
            String accessCode,
            String ticketQrPayload,
            String ticketMessage
    ) {
    }
}
