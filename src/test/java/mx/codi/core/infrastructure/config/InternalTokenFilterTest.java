package mx.codi.core.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import mx.codi.core.infrastructure.config.InternalTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalTokenFilterTest {

    @Test
    void aBlankTokenLeavesBrowserAndPeyCallsOpen() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new InternalTokenFilter("").doFilter(post("/api/v1/orders"), response, mark(called));

        assertThat(called).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void aConfiguredTokenProtectsOrderWritesAndLeavesReadsAndTheWebhookOpen() throws Exception {
        InternalTokenFilter filter = new InternalTokenFilter("secret");

        MockHttpServletResponse missing = new MockHttpServletResponse();
        AtomicBoolean missingCalled = new AtomicBoolean();
        filter.doFilter(post("/api/v1/orders"), missing, mark(missingCalled));
        assertThat(missing.getStatus()).isEqualTo(401);
        assertThat(missingCalled).isFalse();

        MockHttpServletResponse present = new MockHttpServletResponse();
        AtomicBoolean presentCalled = new AtomicBoolean();
        MockHttpServletRequest allowed = post("/api/v1/orders/11111111-1111-4111-8111-111111111111/cancel");
        allowed.addHeader("X-Internal-Token", "secret");
        filter.doFilter(allowed, present, mark(presentCalled));
        assertThat(presentCalled).isTrue();

        AtomicBoolean read = new AtomicBoolean();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/orders"), new MockHttpServletResponse(), mark(read));
        AtomicBoolean webhook = new AtomicBoolean();
        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/v1/payments/webhooks/pey"),
                new MockHttpServletResponse(),
                mark(webhook));
        AtomicBoolean checkIn = new AtomicBoolean();
        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/v1/tickets/check-in"),
                new MockHttpServletResponse(),
                mark(checkIn));
        assertThat(read).isTrue();
        assertThat(webhook).isTrue();
        assertThat(checkIn).isTrue();
    }

    private static MockHttpServletRequest post(String path) {
        return new MockHttpServletRequest("POST", path);
    }

    private static FilterChain mark(AtomicBoolean called) {
        return (request, response) -> called.set(true);
    }
}
