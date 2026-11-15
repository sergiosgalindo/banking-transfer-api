package mx.codi.core.infrastructure.adapter.out.whatsapp;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import mx.codi.core.domain.port.out.TicketNotificationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "codi.notifications.mode", havingValue = "fake", matchIfMissing = true)
public class RecordingTicketNotificationAdapter implements TicketNotificationPort {

    private final List<TicketMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendTicket(TicketMessage message) {
        sent.add(message);
    }

    public List<TicketMessage> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }
}
