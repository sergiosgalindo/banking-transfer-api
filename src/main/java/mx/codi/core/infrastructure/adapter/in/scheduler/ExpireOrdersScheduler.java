package mx.codi.core.infrastructure.adapter.in.scheduler;

import mx.codi.core.domain.port.in.ExpireOrdersUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpireOrdersScheduler {

    private final ExpireOrdersUseCase expireOrders;

    public ExpireOrdersScheduler(ExpireOrdersUseCase expireOrders) {
        this.expireOrders = expireOrders;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void expireDueOrders() {
        expireOrders.expireDueOrders();
    }
}
