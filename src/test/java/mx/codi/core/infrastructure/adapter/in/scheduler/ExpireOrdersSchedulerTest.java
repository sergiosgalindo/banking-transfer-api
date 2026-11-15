package mx.codi.core.infrastructure.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import mx.codi.core.domain.port.in.ExpireOrdersUseCase;
import org.junit.jupiter.api.Test;

class ExpireOrdersSchedulerTest {

    @Test
    void tickDelegatesToTheUseCase() {
        int[] calls = {0};
        ExpireOrdersUseCase expireOrders = () -> {
            calls[0]++;
            return 2;
        };

        new ExpireOrdersScheduler(expireOrders).expireDueOrders();

        assertThat(calls[0]).isEqualTo(1);
    }
}
