package mx.codi.core.infrastructure.adapter.out.whatsapp;

import java.util.LinkedHashMap;
import java.util.Map;
import mx.codi.core.domain.port.out.TicketNotificationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "codi.notifications.mode", havingValue = "live")
public class WhatsAppBridgeNotificationAdapter implements TicketNotificationPort {

    private final RestClient client;
    private final String token;

    public WhatsAppBridgeNotificationAdapter(
            @Value("${codi.notifications.bot-url}") String botUrl,
            @Value("${codi.internal-token:}") String token
    ) {
        this.client = RestClient.builder().baseUrl(botUrl).build();
        this.token = token;
    }

    @Override
    public void sendTicket(TicketMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", message.orderId());
        body.put("customerPhone", message.customerPhone());
        body.put("customerName", message.customerName());
        body.put("quantity", message.quantity());
        body.put("accessCode", message.accessCode());
        body.put("ticketQrPayload", message.ticketQrPayload());
        body.put("ticketMessage", message.ticketMessage());
        try {
            client.post()
                    .uri("/internal/send-ticket")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", token == null ? "" : token)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("ticket notification failed", exception);
        }
    }
}
