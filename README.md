# mex-transfer-api

Java 21 / Spring Boot API for CoDi event-ticket checkout. It is the source of truth for events, ticket types, orders, Pey charges, payment webhooks, and door check-in. It does not talk to WhatsApp. The bot `mex-transfer-whatsapp-api` sends the messages, and `mex-transfer-web` is the organizer UI.

Remote: `git@github.com:sergiosgalindo/banking-transfer-api.git`  
Integration branch: `feature/init-proyecto`

The Java package stays `mx.codi.core`. Order status values stored in PostgreSQL and returned by the API stay `pendiente_de_pago`, `pagada`, `boleto_enviado`, `expirada`, `cancelada`, and `seleccionando_evento`.

## Run

Requires Java 21 and Docker.

```bash
docker compose up -d
./gradlew bootRun
```

Postgres listens on `localhost:5433` (`codi` / `codi` / database `codi`). The API listens on port `8080`. If that port is taken, set `SERVER_PORT=8081`.

`GET /api/health` returns `{"status":"UP"}` when the database answers.

```bash
./gradlew test
```

## Configuration

Secrets stay in the environment. Do not commit them.

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/codi` | PostgreSQL |
| `PEY_MODE` | `fake` | `fake` simulates Pey. `live` calls `https://api.pey.mx` |
| `PEY_API_KEY` | empty | Pey bearer token, live mode only |
| `PEY_WEBHOOK_SECRET` | empty | Standard Webhooks secret (`whsec_...`) |
| `NOTIFICATIONS_MODE` | `fake` | `live` posts the ticket to the bot |
| `BOT_INTERNAL_URL` | `http://localhost:8000` | `mex-transfer-whatsapp-api` base URL |
| `INTERNAL_API_TOKEN` | empty | When set, `POST /api/v1/orders` requires `X-Internal-Token` |
| `BACKOFFICE_ORIGIN` | `http://localhost:5173` | CORS origin for `mex-transfer-web` |

Pey webhook path: `POST /api/v1/payments/webhooks/pey`.

## Main routes

- `GET /api/v1/events/upcoming-dates`
- `GET /api/v1/events` and `GET /api/v1/events?date=YYYY-MM-DD`
- `POST /api/v1/events` and `POST /api/v1/events/{eventId}/ticket-types`
- `POST /api/v1/orders`, `GET /api/v1/orders`, `POST /api/v1/orders/{id}/renew`, `POST /api/v1/orders/{id}/cancel`
- `POST /api/v1/payments/webhooks/pey`
- `POST /api/v1/tickets/check-in`
- `GET /api/v1/payment-alerts`
