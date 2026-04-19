# stocktrace

Multi-broker stock trading platform. **v1 ships a Spring Boot REST service wrapping Zerodha Kite
Connect**, with a broker abstraction (`BrokerService`) so new brokers (5paisa, ICICI, etc.) can be
plugged in later.

## Features (v1)

1. **Full Kite Connect REST API wrapper** — auth, orders, trades, portfolio (holdings/positions),
   market data (instruments, quote, OHLC, LTP, historical), GTT, mutual funds, margins, profile.
   See [API endpoints](#api-endpoints).
2. **Chartink webhook** — `POST /api/webhook/chartink` accepts Chartink's alert JSON and places a
   BUY order on **every user where `active = true`**, in parallel. Each placement is audit-logged.
3. **WebSocket live feed** — per-user `KiteTicker` connection with subscribe / unsubscribe APIs.
   Ticks are persisted to `tick_log`.
4. **Scheduled scanner** — `@Scheduled` job runs every 1 min during IST market hours, evaluates all
   active `scan_rule` rows against the Kite Quote API, and fires fan-out orders when a rule
   matches.
5. **Multi-user** — persistent `kite_users` table. Each user has their own API key, secret, and
   access token. The webhook / scheduler only place orders on users where `active = true`.
6. **Pluggable broker abstraction** — `BrokerService` interface with Zerodha as the first
   implementation. Future: 5paisa and other brokers. Default broker selected via
   `stocktrace.broker.default`.

## Stack

- Java 17, Spring Boot 3.3.x, Maven
- Spring Data JPA + Flyway
- H2 (dev profile) / PostgreSQL (prod profile)
- [`com.zerodhatech.kiteconnect:kiteconnect:4.0.0`](https://github.com/zerodha/javakiteconnect)

## Quick start

```bash
# dev profile (H2 on disk, H2 console at /h2-console)
mvn spring-boot:run

# prod profile against Postgres
DB_URL=jdbc:postgresql://localhost:5432/stocktrace \
DB_USERNAME=stocktrace \
DB_PASSWORD=secret \
SPRING_PROFILES_ACTIVE=prod \
  mvn spring-boot:run
```

Base URL: `http://localhost:8080`

## Configuration

See `src/main/resources/application.yml`:

| Key | Default | Purpose |
| --- | --- | --- |
| `stocktrace.broker.default` | `zerodha` | Which `BrokerService` to use for webhook / scheduler. |
| `stocktrace.scheduler.stock-scan.enabled` | `true` | Toggle the scanner. |
| `stocktrace.scheduler.stock-scan.cron` | `0 */1 9-15 * * MON-FRI` | Every minute, 9:00–15:59 IST, Mon–Fri. |
| `stocktrace.scheduler.stock-scan.zone` | `Asia/Kolkata` | Scheduler time zone. |
| `stocktrace.ticker.enabled` | `true` | Toggle the ticker service. |
| `stocktrace.ticker.mode` | `full` | `ltp`, `quote`, or `full`. |
| `stocktrace.webhook.chartink.secret` | _(empty)_ | If set, requires `?secret=...` on the webhook URL. |

## Workflow: one-time per user

1. **Create the user record** (stores creds; `active=false` is safer at first):

   ```bash
   curl -X POST localhost:8080/api/users \
     -H 'Content-Type: application/json' \
     -d '{
           "userId": "AB1234",
           "label": "my-live-account",
           "apiKey": "your_kite_api_key",
           "apiSecret": "your_kite_api_secret",
           "defaultExchange": "NSE",
           "defaultProduct": "CNC",
           "defaultOrderType": "MARKET",
           "defaultQuantity": 1,
           "active": false
         }'
   ```

2. **Get the Kite login URL** and open it in a browser:

   ```bash
   curl localhost:8080/api/auth/AB1234/login-url
   ```

   After Zerodha login, you'll be redirected to your app's redirect URI with
   `?request_token=...&action=login&status=success`.

3. **Exchange the request token** for an access token (stored on the user):

   ```bash
   curl -X POST localhost:8080/api/auth/AB1234/session \
     -H 'Content-Type: application/json' \
     -d '{"requestToken":"abcd1234"}'
   ```

4. **Activate the user** (the webhook / scheduler will now fan out to this user):

   ```bash
   curl -X POST localhost:8080/api/users/AB1234/activate
   ```

Kite access tokens expire each day at ~6 AM IST. Repeat steps 2–3 each trading day (or use
`POST /api/auth/{userId}/renew` with a refresh token if your Kite app type supports it).

## API endpoints

### User admin
- `GET/POST /api/users`, `GET/PATCH/DELETE /api/users/{userId}`
- `POST /api/users/{userId}/activate`, `POST /api/users/{userId}/deactivate`
- `GET /api/users/active`

### Auth (per user)
- `GET /api/auth/{userId}/login-url`
- `POST /api/auth/{userId}/session` — `{ "requestToken": "..." }`
- `POST /api/auth/{userId}/renew` — `{ "refreshToken": "..." }`
- `POST /api/auth/{userId}/logout`

### Kite API passthrough (per user)
Prefix: `/api/kite/{userId}`

- `GET /profile`
- Margins: `GET /margins`, `GET /margins/{segment}`, `POST /margins/calc`, `POST /margins/calc/combined`, `POST /margins/virtual-contract-note`
- Orders: `GET/POST /orders`, `GET /orders/trades`, `GET /orders/{id}/history`, `GET /orders/{id}/trades`, `PUT /orders/{variety}/{id}`, `DELETE /orders/{variety}/{id}`
- Portfolio: `GET /holdings`, `GET /positions`, `POST /positions/convert`, `GET /auction-instruments`
- Market data: `GET /marketdata/instruments`, `/quote`, `/ohlc`, `/ltp`, `/historical/{token}`
- GTT: `GET/POST /gtt`, `GET/PUT/DELETE /gtt/{id}`
- Mutual funds: `GET /mf/instruments`, `/orders`, `/orders/{id}`, `POST /mf/orders`, `DELETE /mf/orders/{id}`, `/sips` CRUD, `/holdings`

### Chartink webhook
`POST /api/webhook/chartink?secret=...&exchange=NSE&quantity=1&orderType=LIMIT&useTriggerPriceAsLimit=true`

Body: Chartink's standard JSON alert payload. For each `stocks[i]` the service places a BUY order
on every active user (in parallel) using the user's defaults, optionally using `trigger_prices[i]`
as the LIMIT price. Use the query params to override defaults per webhook.

Event log: `GET /api/webhook/chartink/events?page=0&size=50`

### Scheduler rules (`scan_rule`)
- `GET/POST /api/scan-rules`, `GET/PUT/DELETE /api/scan-rules/{id}`

Rules are **one-shot**: the scanner deactivates a rule (`active = false`) as soon as it
triggers so the same condition cannot fire every minute while it stays true. Re-arm
explicitly with `PUT /api/scan-rules/{id}` when you want it to watch again.

Example rule — buy INFY the first time LTP crosses above 1800:

```json
{
  "name": "INFY breakout",
  "exchange": "NSE",
  "tradingsymbol": "INFY",
  "conditionType": "LTP_ABOVE",
  "thresholdHigh": 1800.0,
  "transactionType": "BUY",
  "quantity": 1,
  "product": "CNC",
  "orderType": "MARKET",
  "active": true
}
```

### Ticker (per user)
- `POST /api/ticker/{userId}/connect`, `POST /api/ticker/{userId}/disconnect`
- `POST /api/ticker/{userId}/subscribe` — `{ "instrumentTokens": [408065, 738561] }`
- `POST /api/ticker/{userId}/unsubscribe`
- `GET /api/ticker/{userId}/status`
- `GET /api/ticker/ticks?page=0&size=100`

### Audit
- `GET /api/audit/orders?userId=AB1234&page=0&size=50`

### Meta
- `GET /api/brokers` — lists registered brokers and the default one.
- `GET /actuator/health`

## 5paisa broker

stocktrace ships a second `BrokerService` implementation backed by the
[5paisa Java SDK](https://github.com/sivaramc/5paisa-java). The SDK jar has no
Maven Central artifact so it is vendored under `local-maven-repo/` and wired up
via `<repositories>` in `pom.xml`.

### 5paisa user management

5paisa auth is fundamentally different from Kite (AES-encrypted creds + TOTP
login), so 5paisa accounts live in their own table `fivepaisa_users` with its
own CRUD endpoints:

- `POST /api/5paisa/users` — register (`appName`, `encryptKey`, `userKey`, `fivepaisaUserId`, `password`, `loginId`, `clientCode`, defaults).
- `GET /api/5paisa/users`, `GET /api/5paisa/users/{userId}`, `PUT /api/5paisa/users/{userId}`, `DELETE /api/5paisa/users/{userId}`.
- `POST /api/5paisa/auth/{userId}/totp-session` — exchange TOTP + PIN for a JWT; persisted on the user record.

### 5paisa API passthroughs

Every method on `com.FivePaisa.api.RestClient` is exposed verbatim. All routes
take the stocktrace user id via the `X-User-Id` header and forward the request
body to the SDK as JSON. Responses are returned as-is (content type preserved).

| Route | SDK method |
|---|---|
| `POST /api/5paisa/orders/place` | `placeOrderRequest` |
| `POST /api/5paisa/orders/modify` | `modifyOrderRequest` |
| `POST /api/5paisa/orders/cancel` | `cancelOrderRequest` |
| `POST /api/5paisa/orders/smo/place` | `smoOrderRequest` |
| `POST /api/5paisa/orders/smo/modify` | `modifySmoOrder` |
| `POST /api/5paisa/orders/status` | `orderStatus` |
| `POST /api/5paisa/orders/book` | `orderBookV2` |
| `POST /api/5paisa/orders/trades` | `tradeInformation` |
| `POST /api/5paisa/portfolio/holdings` | `holdingV2` |
| `POST /api/5paisa/portfolio/positions` | `netPositionNetWiseV1` |
| `POST /api/5paisa/portfolio/margin` | `marginV3` |
| `POST /api/5paisa/marketdata/feed` | `marketFeed` |
| `POST /api/5paisa/auth/login-check` | `loginCheck` |

### Multi-broker fan-out

`OrderFanoutService` now walks **every** registered broker and places the order
on all active users in parallel. A single Chartink alert therefore fires orders
on both Zerodha and 5paisa accounts. Failures are isolated per-user and
recorded in `order_audit` (now carrying a `broker` column).

### Symbol convention

5paisa identifies instruments by numeric **ScripCode**, not trading symbol.
When fan-out targets a 5paisa user, `BrokerOrderRequest.tradingsymbol()` is
parsed as a `ScripCode` (e.g. `"1594"` for Infosys on NSE). Symbol -> ScripCode
mapping (ingesting the 5paisa scrip master) is out of scope for this release.

## Adding another broker

1. Implement `in.stocktrace.broker.BrokerService` and register it as a Spring bean.
2. The bean's `id()` is used by `stocktrace.broker.default` and the `BrokerRegistry`.
3. Add the broker id to `OrderFanoutService.placeForAllActive` if you also want
   Chartink/scanner fan-out to target its users.

Broker-native endpoints (GTT, MF, etc.) live under `/api/kite/**` and
`/api/5paisa/**` respectively.

## Project layout

```
src/main/java/in/stocktrace/
  StocktraceApplication.java
  audit/            # order_audit entity + service/controller
  auth/             # per-user Kite OAuth-ish flow
  broker/           # BrokerService, BrokerRegistry, OrderFanoutService
  broker/zerodha/   # ZerodhaBrokerService + KiteConnectFactory
  broker/fivepaisa/ # FivePaisaBrokerService + user/auth/api controllers
  common/           # exceptions + global error handler
  kiteapi/          # REST controllers that passthrough to the Kite SDK
  scheduler/        # scan_rule + StockScannerJob
  ticker/           # Kite WebSocket consumer
  user/             # kite_users CRUD
  webhook/chartink/ # Chartink alert endpoint
src/main/resources/
  application.yml, application-dev.yml, application-prod.yml
  db/migration/V1__init.sql, V2__fivepaisa.sql
local-maven-repo/   # vendored 5paisa SDK jar
```

## Security notes

- `api_secret` and `access_token` are stored **in plain text** in the database for simplicity.
  For production, encrypt at rest using Spring Cloud Config / Vault / Jasypt / database-level
  encryption.
- The Chartink webhook supports a shared `secret` query param
  (`stocktrace.webhook.chartink.secret`). Deploy behind HTTPS.
- There is no built-in authentication on the admin REST APIs in v1. Deploy behind a reverse proxy
  with auth (nginx basic auth, mTLS, or add Spring Security).
