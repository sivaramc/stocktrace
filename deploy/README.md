# Production deploy — stocktrace

Containerised stack for running stocktrace on a plain Linux VPS behind HTTPS.

## Layout

| File | Purpose |
|------|---------|
| [`../Dockerfile`](../Dockerfile) | Multi-stage build → distroless-ish `eclipse-temurin:17-jre-jammy` runtime |
| [`docker-compose.yml`](docker-compose.yml) | `postgres` + `app` + `caddy` (auto Let's Encrypt TLS) |
| [`Caddyfile`](Caddyfile) | Reverse proxy, SSE-aware, strict security headers |
| [`.env.example`](.env.example) | Template — copy to `.env` on the server |
| [`deploy.sh`](deploy.sh) | SSH-based installer / updater |

## One-time server setup

Point an A record for your domain (e.g. `api.stocktrace.in`) at the VPS
public IP. Then from your laptop:

```bash
# 1. Copy the env template and fill in real values
cp deploy/.env.example deploy/.env.prod
$EDITOR deploy/.env.prod

# 2. Ship the stack + env file and start everything
VPS_HOST=vps.example.com \
VPS_USER=ubuntu \
VPS_SSH_KEY=~/.ssh/id_ed25519 \
VPS_ENV_FILE=deploy/.env.prod \
  ./deploy/deploy.sh
```

The script:

1. Installs Docker Engine + Compose plugin if missing.
2. Creates `/opt/stocktrace`.
3. rsyncs `docker-compose.yml`, `Caddyfile`, and your `.env` into
   `/opt/stocktrace`.
4. `docker compose pull && up -d` so Postgres + app + Caddy come up. Caddy
   requests a Let's Encrypt cert automatically on first run (ports 80/443 must
   be reachable from the internet).

## Updating

Push a new image via the `docker-publish.yml` workflow (tag `vX.Y.Z` or
push to `main`), then just re-run `deploy.sh` — it pulls the latest image tag
specified in `.env`.

```bash
./deploy/deploy.sh
```

## Verifying

```bash
# Public health (Caddy → app):
curl https://api.stocktrace.in/actuator/health
# Expected: {"status":"UP"}

# Register the first real user (admin is seeded via env var):
curl -sS https://api.stocktrace.in/api/auth/login \
  -H 'content-type: application/json' \
  -d '{"email":"admin@stocktrace.in","password":"YOUR_ADMIN_PASSWORD"}'
```

## Operational notes

- **Database backups** — Postgres volume is `postgres-data`. Snapshot from
  the host with `docker exec -t stocktrace-postgres-1 pg_dump -U stocktrace stocktrace | gzip > backup-$(date +%F).sql.gz`
  and copy off the server.
- **Secrets rotation** — update `deploy/.env.prod`, re-run `deploy.sh`. The
  admin password env var is only read on first startup (when the admin row
  doesn't exist). Change it afterwards by PATCHing via the app API.
- **Logs** — `ssh $VPS_USER@$VPS_HOST 'sudo docker compose -f /opt/stocktrace/docker-compose.yml logs -f app'`
- **Caddy cert storage** — persisted in the `caddy-data` named volume; don't
  `docker volume rm` it casually, otherwise Let's Encrypt will rate-limit
  fresh-cert requests.

## Security

- Only ports 22 (SSH), 80, 443 need to be open on the firewall. The app and
  Postgres are bound to the internal Docker network only.
- JWT signing key and Postgres password must be unique per environment — do
  **not** reuse the placeholder values from `.env.example`.
- HSTS is enabled. First deploy commits you to HTTPS for 1 year; plan DNS /
  cert rotations with that in mind.
