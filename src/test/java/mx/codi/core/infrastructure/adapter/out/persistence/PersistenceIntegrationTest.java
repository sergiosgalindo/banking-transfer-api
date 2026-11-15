package mx.codi.core.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import mx.codi.core.domain.SoldOutException;
import mx.codi.core.domain.model.Event;
import mx.codi.core.domain.model.MexicoCity;
import mx.codi.core.domain.model.Order;
import mx.codi.core.domain.model.OrderStatus;
import mx.codi.core.domain.model.ProcessedWebhook;
import mx.codi.core.domain.model.TicketType;
import mx.codi.core.domain.port.in.CreateOrderUseCase;
import mx.codi.core.domain.port.in.CreateOrderUseCase.CreateOrderCommand;
import mx.codi.core.domain.port.in.ExpireOrdersUseCase;
import mx.codi.core.domain.port.out.EventRepositoryPort;
import mx.codi.core.domain.port.out.OrderRepositoryPort;
import mx.codi.core.domain.port.out.ProcessedWebhookRepositoryPort;
import mx.codi.core.domain.port.out.TicketTypeRepositoryPort;
import mx.codi.core.infrastructure.adapter.out.whatsapp.RecordingTicketNotificationAdapter;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration")
@Import(PersistenceIntegrationTest.FixedClockConfig.class)
@Transactional
class PersistenceIntegrationTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    private static final String HEALTH_JSON = """
            {
              "status": "UP"
            }
            """;

    private static final String UPCOMING_DATES_JSON = """
            {
              "dates": ["2026-11-15", "2026-11-18"]
            }
            """;

    private static final String SEEDED_UPCOMING_DATES_JSON = """
            {
              "dates": ["2026-11-15", "2026-11-17", "2026-11-21"]
            }
            """;

    private static final String MIXED_DAY_JSON = """
            [
              {
                "id": "11111111-1111-4111-8111-111111111111",
                "name": "Social de salsa",
                "venue": "Foro",
                "startsAt": "2026-11-16T03:00:00Z",
                "category": "social",
                "description": "Descripción",
                "flyerImage": null,
                "ticketTypes": [
                  {
                    "id": "22222222-2222-4222-8222-222222222221",
                    "name": "Agotado",
                    "regularPrice": 100.00,
                    "presalePrice": null,
                    "presaleEndsAt": null,
                    "currentPrice": 100.00,
                    "capacity": 10,
                    "sold": 10,
                    "reserved": 0,
                    "available": 0
                  },
                  {
                    "id": "22222222-2222-4222-8222-222222222222",
                    "name": "VIP",
                    "regularPrice": 100.00,
                    "presalePrice": null,
                    "presaleEndsAt": null,
                    "currentPrice": 100.00,
                    "capacity": 5,
                    "sold": 1,
                    "reserved": 1,
                    "available": 3
                  }
                ]
              }
            ]
            """;

    private static final String SHARED_DAY_JSON = """
            [
              {
                "id": "11111111-1111-4111-8111-111111111114",
                "name": "Matiné",
                "venue": "Foro",
                "startsAt": "2026-11-19T00:00:00Z",
                "category": "concierto",
                "description": "Descripción",
                "flyerImage": null,
                "ticketTypes": [
                  {
                    "id": "22222222-2222-4222-8222-222222222225",
                    "name": "General",
                    "regularPrice": 100.00,
                    "presalePrice": null,
                    "presaleEndsAt": null,
                    "currentPrice": 100.00,
                    "capacity": 40,
                    "sold": 0,
                    "reserved": 2,
                    "available": 38
                  }
                ]
              },
              {
                "id": "11111111-1111-4111-8111-111111111115",
                "name": "Noche",
                "venue": "Foro",
                "startsAt": "2026-11-19T04:00:00Z",
                "category": "concierto",
                "description": "Descripción",
                "flyerImage": null,
                "ticketTypes": [
                  {
                    "id": "22222222-2222-4222-8222-222222222226",
                    "name": "General",
                    "regularPrice": 100.00,
                    "presalePrice": null,
                    "presaleEndsAt": null,
                    "currentPrice": 100.00,
                    "capacity": 40,
                    "sold": 3,
                    "reserved": 0,
                    "available": 37
                  }
                ]
              }
            ]
            """;

    private static final String SOLD_OUT_DAY_JSON = """
            [
              {
                "id": "11111111-1111-4111-8111-111111111112",
                "name": "Taller lleno",
                "venue": "Foro",
                "startsAt": "2026-11-16T17:00:00Z",
                "category": "taller",
                "description": "Descripción",
                "flyerImage": null,
                "ticketTypes": [
                  {
                    "id": "22222222-2222-4222-8222-222222222223",
                    "name": "General",
                    "regularPrice": 100.00,
                    "presalePrice": null,
                    "presaleEndsAt": null,
                    "currentPrice": 100.00,
                    "capacity": 4,
                    "sold": 4,
                    "reserved": 0,
                    "available": 0
                  }
                ]
              }
            ]
            """;

    private static final String PAST_DAY_JSON = """
            [
              {
                "id": "11111111-1111-4111-8111-111111111113",
                "name": "Concierto pasado",
                "venue": "Foro",
                "startsAt": "2026-11-13T02:00:00Z",
                "category": "concierto",
                "description": "Descripción",
                "flyerImage": null,
                "ticketTypes": [
                  {
                    "id": "22222222-2222-4222-8222-222222222224",
                    "name": "General",
                    "regularPrice": 100.00,
                    "presalePrice": null,
                    "presaleEndsAt": null,
                    "currentPrice": 100.00,
                    "capacity": 20,
                    "sold": 0,
                    "reserved": 0,
                    "available": 20
                  }
                ]
              }
            ]
            """;

    private static final String SALSA_DAY_JSON = """
            [
              {
                "id": "10000000-0000-4000-8000-000000000001",
                "name": "Social de salsa",
                "venue": "Salón Reforma",
                "startsAt": "2026-11-16T03:00:00Z",
                "category": "social",
                "description": "Noche de salsa social.",
                "flyerImage": null,
                "ticketTypes": [
                  {
                    "id": "10000000-0000-4000-8000-000000000011",
                    "name": "General",
                    "regularPrice": 250.00,
                    "presalePrice": 180.00,
                    "presaleEndsAt": "2026-11-16T00:00:00Z",
                    "currentPrice": 180.00,
                    "capacity": 120,
                    "sold": 0,
                    "reserved": 0,
                    "available": 120
                  }
                ]
              }
            ]
            """;

    private static final String WORKSHOP_DAY_JSON = """
            [
              {
                "id": "10000000-0000-4000-8000-000000000002",
                "name": "Taller de ritmos",
                "venue": "Estudio Centro",
                "startsAt": "2026-11-17T17:00:00Z",
                "category": "taller",
                "description": "Taller práctico de ritmos.",
                "flyerImage": null,
                "ticketTypes": [
                  {
                    "id": "10000000-0000-4000-8000-000000000012",
                    "name": "Participante",
                    "regularPrice": 400.00,
                    "presalePrice": null,
                    "presaleEndsAt": null,
                    "currentPrice": 400.00,
                    "capacity": 30,
                    "sold": 0,
                    "reserved": 0,
                    "available": 30
                  }
                ]
              }
            ]
            """;

    private static final String CONCERT_DAY_JSON = """
            [
              {
                "id": "10000000-0000-4000-8000-000000000003",
                "name": "Concierto en vivo",
                "venue": "Teatro de la Ciudad",
                "startsAt": "2026-11-22T02:00:00Z",
                "category": "concierto",
                "description": "Concierto con cupo limitado.",
                "flyerImage": null,
                "ticketTypes": [
                  {
                    "id": "10000000-0000-4000-8000-000000000013",
                    "name": "General",
                    "regularPrice": 800.00,
                    "presalePrice": 640.00,
                    "presaleEndsAt": "2026-11-20T05:59:00Z",
                    "currentPrice": 640.00,
                    "capacity": 500,
                    "sold": 0,
                    "reserved": 0,
                    "available": 500
                  }
                ]
              }
            ]
            """;

    private static final String ORDER_JSON = """
            [
              {
                "id": "33333333-3333-4333-8333-333333333331",
                "refNumber": 1000001,
                "eventId": "11111111-1111-4111-8111-111111111117",
                "ticketTypeId": "22222222-2222-4222-8222-222222222227",
                "quantity": 2,
                "customerName": "Ana López",
                "customerPhone": "525512345678",
                "amount": 360.00,
                "status": "pendiente_de_pago",
                "codiQrId": null,
                "paymentLinkId": null,
                "paymentLinkUrl": null,
                "trackingKey": null,
                "expiresAt": "2026-11-15T00:10:00Z",
                "accessCode": null,
                "checkedInAt": null,
                "createdAt": "2026-11-15T00:00:00Z",
                "paidAt": null,
                "eventName": "Social de salsa",
                "ticketTypeName": "General",
                "reviewNote": null
              }
            ]
            """;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private EventRepositoryPort events;

    @Autowired
    private TicketTypeRepositoryPort ticketTypes;

    @Autowired
    private OrderRepositoryPort orders;

    @Autowired
    private ProcessedWebhookRepositoryPort webhooks;

    @Autowired
    private DemoEventSeeder seeder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CreateOrderUseCase createOrders;

    @Autowired
    private ExpireOrdersUseCase expireOrders;

    @Autowired
    private RecordingTicketNotificationAdapter notifications;

    @Test
    void healthIsUpAgainstPostgreSQL() throws Exception {
        expectJson(mockMvc.perform(get("/api/health")), HEALTH_JSON);
    }

    @Test
    void upcomingDatesAndEventsByDateMatchResponseJson() throws Exception {
        saveEvent(id("11111111-1111-4111-8111-111111111111"), "Social de salsa", "social", at("2026-11-15", 21), List.of(
                ticket(id("22222222-2222-4222-8222-222222222221"), "Agotado", 10, 10, 0),
                ticket(id("22222222-2222-4222-8222-222222222222"), "VIP", 5, 1, 1)));
        saveEvent(id("11111111-1111-4111-8111-111111111112"), "Taller lleno", "taller", at("2026-11-16", 11), List.of(
                ticket(id("22222222-2222-4222-8222-222222222223"), "General", 4, 4, 0)));
        saveEvent(id("11111111-1111-4111-8111-111111111113"), "Concierto pasado", "concierto", at("2026-11-12", 20), List.of(
                ticket(id("22222222-2222-4222-8222-222222222224"), "General", 20, 0, 0)));
        saveEvent(id("11111111-1111-4111-8111-111111111114"), "Matiné", "concierto", at("2026-11-18", 18), List.of(
                ticket(id("22222222-2222-4222-8222-222222222225"), "General", 40, 0, 2)));
        saveEvent(id("11111111-1111-4111-8111-111111111115"), "Noche", "concierto", at("2026-11-18", 22), List.of(
                ticket(id("22222222-2222-4222-8222-222222222226"), "General", 40, 3, 0)));
        saveEvent(id("11111111-1111-4111-8111-111111111116"), "Sin boletos", "social", at("2026-11-20", 19), List.of());
        flush();

        expectJson(mockMvc.perform(get("/api/v1/events/upcoming-dates")), UPCOMING_DATES_JSON);
        expectJson(mockMvc.perform(get("/api/v1/events").param("date", "2026-11-15")), MIXED_DAY_JSON);
        expectJson(mockMvc.perform(get("/api/v1/events").param("date", "2026-11-18")), SHARED_DAY_JSON);
        expectJson(mockMvc.perform(get("/api/v1/events").param("date", "2026-11-16")), SOLD_OUT_DAY_JSON);
        expectJson(mockMvc.perform(get("/api/v1/events").param("date", "2026-11-12")), PAST_DAY_JSON);
        expectJson(mockMvc.perform(get("/api/v1/events").param("date", "2026-12-01")), "[]");
    }

    @Test
    void seedResponsesMatchJson() throws Exception {
        seeder.seedIfEmpty();
        flush();

        expectJson(mockMvc.perform(get("/api/v1/events/upcoming-dates")), SEEDED_UPCOMING_DATES_JSON);
        expectJson(mockMvc.perform(get("/api/v1/events").param("date", "2026-11-15")), SALSA_DAY_JSON);
        expectJson(mockMvc.perform(get("/api/v1/events").param("date", "2026-11-17")), WORKSHOP_DAY_JSON);
        expectJson(mockMvc.perform(get("/api/v1/events").param("date", "2026-11-21")), CONCERT_DAY_JSON);
    }

    @Test
    void orderResponseMatchesJson() throws Exception {
        Event event = saveEvent(
                id("11111111-1111-4111-8111-111111111117"),
                "Social de salsa",
                "social",
                at("2026-11-15", 21),
                List.of(ticket(id("22222222-2222-4222-8222-222222222227"), "General", 10, 0, 2)));
        TicketType ticketType = event.ticketTypes().getFirst();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-11-14T18:00:00-06:00");
        orders.save(new Order(
                id("33333333-3333-4333-8333-333333333331"),
                1000001,
                event.id(),
                ticketType.id(),
                2,
                "Ana López",
                "525512345678",
                new BigDecimal("360.00"),
                OrderStatus.PENDING_PAYMENT,
                null,
                null,
                null,
                null,
                createdAt.plusMinutes(10),
                null,
                null,
                createdAt,
                null));
        webhooks.save(new ProcessedWebhook("msg_123", "txn_456"));
        flush();

        expectJson(mockMvc.perform(get("/api/v1/orders").param("phone", "525512345678")), ORDER_JSON);
        assertThatThrownBy(() -> webhooks.findByWebhookId("missing").orElseThrow()).isInstanceOf(java.util.NoSuchElementException.class);
        org.assertj.core.api.Assertions.assertThat(webhooks.findByWebhookId("msg_123"))
                .contains(new ProcessedWebhook("msg_123", "txn_456"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void refNumberIsUnique() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Event event = transaction.execute(status -> saveEvent(
                UUID.randomUUID(),
                "Social de salsa",
                "social",
                at("2026-11-15", 21),
                List.of(ticket(UUID.randomUUID(), "General", 10, 0, 0))));
        try {
            transaction.executeWithoutResult(status -> {
                orders.save(order(event, 42));
                entityManager.flush();
            });

            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                orders.save(order(event, 42));
                entityManager.flush();
            })).isInstanceOf(ConstraintViolationException.class);
        } finally {
            transaction.executeWithoutResult(status -> {
                entityManager.createNativeQuery("DELETE FROM orders WHERE ref_number = 42").executeUpdate();
                entityManager.createNativeQuery("DELETE FROM ticket_type WHERE event_id = :id")
                        .setParameter("id", event.id())
                        .executeUpdate();
                entityManager.createNativeQuery("DELETE FROM event WHERE id = :id")
                        .setParameter("id", event.id())
                        .executeUpdate();
            });
        }
    }

    @Test
    void createOrderLocksPresaleTotalForTheQuantity() {
        UUID ticketId = id("22222222-2222-4222-8222-222222222301");
        saveEvent(
                id("11111111-1111-4111-8111-111111111301"),
                "Social de salsa",
                "social",
                at("2026-11-15", 21),
                List.of(new TicketType(
                        ticketId,
                        id("11111111-1111-4111-8111-111111111301"),
                        "General",
                        new BigDecimal("250.00"),
                        new BigDecimal("180.00"),
                        OffsetDateTime.parse("2026-11-16T00:00:00Z"),
                        2,
                        0,
                        0)));
        flush();

        Order created = createOrders.create(new CreateOrderCommand(ticketId, 2, "Ana López", "525500000099")).order();

        assertThat(created.amount()).isEqualByComparingTo("360.00");
        assertThat(created.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(created.customerName()).isEqualTo("Ana López");
        assertThat(created.customerPhone()).isEqualTo("525500000099");
        assertThat(created.quantity()).isEqualTo(2);
        assertThat(created.expiresAt()).isEqualTo(OffsetDateTime.parse("2026-11-14T18:10:00Z"));
        assertThat(created.codiQrId()).isEqualTo("qr-" + created.refNumber());
        assertThat(created.paymentLinkUrl()).isEqualTo("https://pay.example/link/" + created.id());
        assertThat(created.accessCode()).isNull();
        assertThat(ticketTypes.findById(ticketId).orElseThrow().reserved()).isEqualTo(2);
        assertThat(ticketTypes.findById(ticketId).orElseThrow().available()).isZero();
    }

    @Test
    void expiringADueOrderReleasesReservedSeats() {
        UUID ticketId = id("22222222-2222-4222-8222-222222222302");
        saveEvent(
                id("11111111-1111-4111-8111-111111111302"),
                "Taller",
                "taller",
                at("2026-11-16", 11),
                List.of(ticket(ticketId, "General", 5, 0, 0)));
        flush();
        Order created = createOrders.create(new CreateOrderCommand(ticketId, 2, "Ana López", "525500000098")).order();
        flush();
        int updated = entityManager.createNativeQuery(
                        "UPDATE orders SET expires_at = TIMESTAMP WITH TIME ZONE '2026-11-14 17:00:00+00' WHERE id = :id")
                .setParameter("id", created.id())
                .executeUpdate();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(expireOrders.expireDueOrders()).isEqualTo(1);
        assertThat(expireOrders.expireDueOrders()).isZero();
        assertThat(orders.findById(created.id()).orElseThrow().status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(ticketTypes.findById(ticketId).orElseThrow().reserved()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lastSeatIsReservedByOnlyOneBuyer() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID ticketId = UUID.randomUUID();
        Event event = transaction.execute(status -> saveEvent(
                UUID.randomUUID(),
                "Social de salsa",
                "social",
                at("2026-11-15", 21),
                List.of(ticket(ticketId, "General", 1, 0, 0))));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Order>> attempts = List.of(
                    pool.submit(() -> buy(start, ticketId, "Ana López", "525500000001")),
                    pool.submit(() -> buy(start, ticketId, "Luis Pérez", "525500000002")));
            start.countDown();

            int reserved = 0;
            int soldOut = 0;
            for (Future<Order> attempt : attempts) {
                try {
                    Order order = attempt.get(10, TimeUnit.SECONDS);
                    assertThat(order.quantity()).isEqualTo(1);
                    assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
                    reserved++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(SoldOutException.class);
                    soldOut++;
                }
            }

            assertThat(reserved).isEqualTo(1);
            assertThat(soldOut).isEqualTo(1);
            TicketType stored = transaction.execute(status -> ticketTypes.findById(ticketId).orElseThrow());
            assertThat(stored.reserved()).isEqualTo(1);
            assertThat(stored.available()).isZero();
        } finally {
            pool.shutdownNow();
            transaction.executeWithoutResult(status -> {
                entityManager.createNativeQuery("DELETE FROM orders WHERE event_id = :id")
                        .setParameter("id", event.id())
                        .executeUpdate();
                entityManager.createNativeQuery("DELETE FROM ticket_type WHERE event_id = :id")
                        .setParameter("id", event.id())
                        .executeUpdate();
                entityManager.createNativeQuery("DELETE FROM event WHERE id = :id")
                        .setParameter("id", event.id())
                        .executeUpdate();
            });
        }
    }

    @Test
    void chargedOrderWebhookAndCheckInMatchJson() throws Exception {
        notifications.clear();
        UUID eventId = id("11111111-1111-4111-8111-111111111310");
        UUID ticketId = id("22222222-2222-4222-8222-222222222310");
        saveEvent(eventId, "Social de salsa", "social", at("2026-11-15", 21), List.of(new TicketType(
                ticketId,
                eventId,
                "General",
                new BigDecimal("250.00"),
                new BigDecimal("180.00"),
                OffsetDateTime.parse("2026-11-16T00:00:00Z"),
                4,
                0,
                0)));
        flush();

        String requestJson = """
                {
                  "ticketTypeId": "%s",
                  "quantity": 2,
                  "customerName": "Ana López",
                  "customerPhone": "525500000097"
                }
                """.formatted(ticketId);
        ResultActions created = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson));
        flush();
        Order stored = orders.findAll().stream()
                .filter(order -> "525500000097".equals(order.customerPhone()))
                .findFirst()
                .orElseThrow();
        expectJson(created, """
                {
                  "orderId": "%s",
                  "quantity": 2,
                  "amount": 360.00,
                  "qrString": "CODI|360.00|%d",
                  "paymentLinkUrl": "https://pay.example/link/%s",
                  "expiresAt": "2026-11-14T18:10:00Z"
                }
                """.formatted(stored.id(), stored.refNumber(), stored.id()));

        mockMvc.perform(post("/api/v1/payments/webhooks/pey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"codi.qr.payment_received\"}")
                        .header("webhook-id", "msg-bad")
                        .header("webhook-timestamp", webhookTimestamp())
                        .header("webhook-signature", "v1,aaaa"))
                .andExpect(status().isBadRequest());

        String payload = """
                {"type":"codi.qr.payment_received","amount":"360.00","codiQrId":"%s","transactionId":"txn-demo","numRef":%d}"""
                .formatted(stored.codiQrId(), stored.refNumber());
        String signature = webhookSignature("msg-demo", webhookTimestamp(), payload);
        mockMvc.perform(webhook("msg-demo", signature, payload)).andExpect(status().isOk());
        mockMvc.perform(webhook("msg-demo", signature, payload)).andExpect(status().isOk());
        flush();

        Order paid = orders.findById(stored.id()).orElseThrow();
        assertThat(paid.status()).isEqualTo(OrderStatus.TICKET_SENT);
        assertThat(paid.customerName()).isEqualTo("Ana López");
        assertThat(paid.quantity()).isEqualTo(2);
        assertThat(notifications.sent().stream().filter(message -> paid.id().toString().equals(message.orderId())).count())
                .isEqualTo(1);
        expectJson(mockMvc.perform(get("/api/v1/orders").param("phone", "525500000097")), """
                [
                  {
                    "id": "%s",
                    "refNumber": %d,
                    "eventId": "%s",
                    "ticketTypeId": "%s",
                    "quantity": 2,
                    "customerName": "Ana López",
                    "customerPhone": "525500000097",
                    "amount": 360.00,
                    "status": "boleto_enviado",
                    "codiQrId": "%s",
                    "paymentLinkId": "%s",
                    "paymentLinkUrl": "%s",
                    "trackingKey": "txn-demo",
                    "expiresAt": "2026-11-14T18:10:00Z",
                    "accessCode": "%s",
                    "checkedInAt": null,
                    "createdAt": "2026-11-14T18:00:00Z",
                    "paidAt": "2026-11-14T18:00:00Z",
                    "eventName": "Social de salsa",
                    "ticketTypeName": "General",
                    "reviewNote": null
                  }
                ]
                """.formatted(
                paid.id(),
                paid.refNumber(),
                paid.eventId(),
                paid.ticketTypeId(),
                paid.codiQrId(),
                paid.paymentLinkId(),
                paid.paymentLinkUrl(),
                paid.accessCode()));

        String checkInJson = """
                {
                  "admitted": true,
                  "reason": null,
                  "customerName": "Ana López",
                  "customerPhone": "525500000097",
                  "quantity": 2,
                  "checkedInAt": "2026-11-14T18:00:00Z",
                  "eventName": "Social de salsa",
                  "ticketTypeName": "General"
                }
                """;
        expectJson(mockMvc.perform(post("/api/v1/tickets/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessCode\":\"" + paid.accessCode() + "\"}")), checkInJson);
        expectJson(mockMvc.perform(post("/api/v1/tickets/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessCode\":\"" + paid.accessCode() + "\"}")), checkInJson.replace(
                "\"admitted\": true,\n  \"reason\": null",
                "\"admitted\": false,\n  \"reason\": \"already_checked_in\""));
    }

    private org.springframework.test.web.servlet.RequestBuilder webhook(String id, String signature, String payload) {
        return post("/api/v1/payments/webhooks/pey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("webhook-id", id)
                .header("webhook-timestamp", webhookTimestamp())
                .header("webhook-signature", signature);
    }

    private static String webhookTimestamp() {
        return Long.toString(FIXED_CLOCK.instant().getEpochSecond());
    }

    private static String webhookSignature(String id, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode("dGVzdC1zZWNyZXQ="), "HmacSHA256"));
        byte[] raw = mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(raw);
    }

    private Order buy(CountDownLatch start, UUID ticketTypeId, String name, String phone) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        return createOrders.create(new CreateOrderCommand(ticketTypeId, 1, name, phone)).order();
    }

    private static void expectJson(ResultActions actions, String responseJson) throws Exception {
        actions.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(responseJson, JsonCompareMode.STRICT));
    }

    private Event saveEvent(UUID eventId, String name, String category, OffsetDateTime startsAt, List<TicketType> draftedTickets) {
        events.save(new Event(eventId, name, "Foro", startsAt, category, "Descripción", List.of()));
        List<TicketType> stored = draftedTickets.stream()
                .map(draft -> ticketTypes.save(new TicketType(
                        draft.id(),
                        eventId,
                        draft.name(),
                        draft.regularPrice(),
                        draft.presalePrice(),
                        draft.presaleEndsAt(),
                        draft.capacity(),
                        draft.sold(),
                        draft.reserved())))
                .toList();
        return new Event(eventId, name, "Foro", startsAt, category, "Descripción", stored);
    }

    private static TicketType ticket(UUID id, String name, int capacity, int sold, int reserved) {
        return new TicketType(
                id,
                UUID.randomUUID(),
                name,
                new BigDecimal("100.00"),
                null,
                null,
                capacity,
                sold,
                reserved);
    }

    private static OffsetDateTime at(String date, int hour) {
        return LocalDate.parse(date).atTime(hour, 0).atZone(MexicoCity.ZONE).toOffsetDateTime();
    }

    private static UUID id(String value) {
        return UUID.fromString(value);
    }

    private static Order order(Event event, int refNumber) {
        return new Order(
                UUID.randomUUID(),
                refNumber,
                event.id(),
                event.ticketTypes().getFirst().id(),
                1,
                "Ana López",
                "525512345678",
                new BigDecimal("100.00"),
                OrderStatus.PENDING_PAYMENT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-11-14T18:00:00-06:00"),
                null);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
