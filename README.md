# CloudPulse: Unified Cloud Operations Dashboard

CloudPulse watches your deployed web apps (Vercel, Render, AWS, anywhere on the public internet) from one dashboard.
A Spring Boot scheduler checks each URL on its own interval, stores real HTTP status and response-time history in MySQL,
opens an incident after repeated failures, closes it on recovery, and shows everything in a React dashboard.

Built as a portfolio project for a Graduate Engineer Trainee (CS/IT) application: cloud hosting, automation,
security, monitoring, databases and CI/CD in one small, explainable codebase.

```
 Browser ──▶ React (nginx / Vercel)
                 │  /api (JWT bearer token)
                 ▼
          Spring Boot 3 (Java 21) ──▶ MySQL 8
           ├─ REST API + JWT auth
           ├─ Scheduler (virtual threads) ──▶ SafeHttpProbe ──▶ your apps (SSRF-guarded)
           ├─ Incident state machine
           └─ Actuator /health, optional e-mail alerts
 Python script (scripts/cloudpulse_check.py) ──▶ API ──▶ exit code / CloudWatch metrics
```

## What is in the box

| Area | Details |
|---|---|
| Monitoring engine | Per-service interval (10 to 3600 s), timeout, and failure threshold. The scheduler is backend-driven, so monitoring continues with no browser open. |
| Application metrics | Optional Spring Boot Actuator collection for each service. Numeric JVM, CPU, HTTP, database-pool and disk metrics are sampled after successful checks and shown in the service detail dashboard. |
| Data | Status code, response time, timestamp and error text for every check. History is pruned after 30 days (configurable). |
| Incidents | Opens after N consecutive failures (default 3), deduplicated, resolved on recovery, with a timeline in the UI. |
| Dashboard | Summary band, service list with a heartbeat strip of the last 40 checks, detail drawer with latency chart, availability, p95, checks table, add/edit/pause/delete, check-now. Loading, empty, error and offline states. |
| Security | JWT login, bcrypt-hashed admin password from env, login rate limiting, **SSRF protection** (see below), no secrets in code, non-root containers, DB not exposed. |
| DevOps | Docker Compose, GitHub Actions CI, OIDC-based deploy workflow, CloudWatch logs via the `awslogs` driver, Python automation script. |

### SSRF protection
Users supply URLs that the server then requests, which is a classic SSRF risk. `net/SsrfGuard` + `net/SafeHttpProbe` enforce:
- only `http`/`https`, no `user:pass@`, valid port;
- blocked names (`localhost`, `*.internal`, `*.local`, metadata names);
- **every** resolved IP must be public: loopback, RFC 1918, link-local (incl. `169.254.169.254`), CGNAT, multicast, reserved, IPv6 ULA/mapped/NAT64 ranges are refused;
- **DNS-rebinding defence**: the probe resolves once, validates, then connects the socket to that validated IP (TLS still verifies the certificate for the original host name);
- redirects are followed manually (max 3) and re-validated at each hop;
- response bodies are never downloaded; timeouts bound every request; stored error text is capped at 500 characters.

## Quick start (Docker)

Requirements: Docker with Compose v2.

```bash
cp .env.example .env
# edit .env: set MYSQL_ROOT_PASSWORD, MYSQL_PASSWORD, CLOUDPULSE_ADMIN_PASSWORD (10+ chars)
# and CLOUDPULSE_JWT_SECRET (32+ chars):  openssl rand -base64 48
docker compose up --build -d
docker compose ps            # wait until mysql and backend are "healthy"
```
Open http://localhost:8080 and sign in with `CLOUDPULSE_ADMIN_USERNAME` / `CLOUDPULSE_ADMIN_PASSWORD`.
Add the URL of one of your Vercel/Render apps. The first check runs within ~5 seconds.

To collect application metrics, edit a service and enable **Collect Spring Boot Actuator metrics**.
The monitored application must expose a public `GET /actuator/metrics` endpoint, for example:

```properties
management.endpoints.web.exposure.include=health,metrics
```

CloudPulse samples commonly available numeric metrics such as `system.cpu.usage`,
`jvm.memory.used`, `jvm.threads.live`, `http.server.requests`, HikariCP connections and
disk space. Services whose Actuator endpoint is private or authenticated can still use normal
health monitoring; authenticated metrics are not collected by this first version.

Stop: `docker compose down` (add `-v` to also delete the database volume).
Logs: `docker compose logs -f backend`.

## Local development (without Docker)

```bash
# 1. MySQL 8 with an empty database and user
mysql -uroot -e "CREATE DATABASE cloudpulse; CREATE USER 'cloudpulse'@'%' IDENTIFIED BY 'devpass'; GRANT ALL ON cloudpulse.* TO 'cloudpulse'@'%';"

# 2. Backend (Java 21 + Maven 3.9)
cd backend
export DB_PASSWORD=devpass CLOUDPULSE_ADMIN_USERNAME=admin CLOUDPULSE_ADMIN_PASSWORD=dev-password-123 \
       CLOUDPULSE_JWT_SECRET=dev-secret-dev-secret-dev-secret-1234
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # Flyway creates the schema on first start

# 3. Frontend (Node 22), in another terminal. Vite proxies /api to :8080
cd frontend && npm install && npm run dev                # http://localhost:5173
```

## Tests

```bash
cd backend  && mvn -B verify          # unit + integration tests (H2 in-memory, no MySQL needed)
cd frontend && npm test && npm run build
python scripts/cloudpulse_check.py --help
```

## Demonstrating failure detection and recovery

1. In `.env` set `CLOUDPULSE_ALLOW_PRIVATE_TARGETS=true` and restart: `docker compose up -d`. (Local testing only. This relaxes the SSRF guard so a target on your machine is allowed; link-local/metadata addresses stay blocked.)
2. Start the switchable test target: `python scripts/flaky_target.py` (port 9000).
3. In the dashboard add a service `Test target`, URL `http://host.docker.internal:9000/health`, interval `10`, timeout `3000`, failures before alert `3`.
4. **Healthy**: the status turns Up with green bars.
5. **Failure**: `curl localhost:9000/set/fail`. After 3 failed checks (~30 s) the service is Down and an incident appears with reason `HTTP 503`.
6. **Timeout**: `curl localhost:9000/set/slow`. Checks fail with `Timed out after 3000 ms`.
7. **Recovery**: `curl localhost:9000/set/ok`. The next check marks it Up and the incident shows its recovery time and duration.
8. **SSRF**: try adding `http://169.254.169.254/latest/meta-data/`. The API answers `400 Target URL rejected`.

Set it back to `CLOUDPULSE_ALLOW_PRIVATE_TARGETS=false` afterwards.

## Configuration

All settings are environment variables (see `.env.example`).

| Variable | Purpose | Default |
|---|---|---|
| `CLOUDPULSE_ADMIN_USERNAME` / `_PASSWORD` | Dashboard admin login (password ≥ 10 chars) | required (no default) |
| `CLOUDPULSE_JWT_SECRET` | HMAC key for tokens (≥ 32 chars) | `dev-secret-dev-secret-dev-secret-1234` |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` or `DB_URL` | MySQL connection | localhost:3306/cloudpulse |
| `CLOUDPULSE_CORS_ALLOWED_ORIGINS` | Origins allowed when the frontend is on another domain | empty |
| `CLOUDPULSE_ALLOW_PRIVATE_TARGETS` | Dev-only SSRF relaxation | `false` |
| `CLOUDPULSE_RECOVERY_THRESHOLD` | Consecutive successes needed to close an incident | `1` |
| `CLOUDPULSE_RETENTION_DAYS` | How long check history is kept | `30` |
| `CLOUDPULSE_ALERTS_ENABLED`, `_ALERT_RECIPIENTS`, `SPRING_MAIL_*` | E-mail alerts on incident open/recover | disabled |

The backend refuses to start if the admin password or JWT secret are missing or too short.

## REST API

All `/api/**` routes except login need `Authorization: Bearer <token>`. `GET /actuator/health` is public.

| Method & path | Description |
|---|---|
| `POST /api/auth/login` | `{username, password}` → `{token, expiresAt}` (rate-limited) |
| `GET /api/summary` | Counts, active incidents, average response (last hour; `null` if no data) |
| `GET /api/services` | All services with state and the last 40 checks |
| `POST /api/services` · `PUT /api/services/{id}` | Create / update (validated, SSRF-checked) |
| `PATCH /api/services/{id}/enabled` | `{enabled}` pause or resume |
| `DELETE /api/services/{id}` | Delete with history |
| `POST /api/services/{id}/check-now` | Run a probe immediately |
| `GET /api/services/{id}/checks?limit=` | Recent checks |
| `GET /api/services/{id}/latency?hours=` | Chart points and stats (availability, avg, p95, max) |
| `GET /api/services/{id}/metrics?hours=` | Numeric Spring Boot Actuator metric samples collected for the service |
| `GET /api/services/{id}/incidents` · `GET /api/incidents?status=active\|all` | Incident history |

Errors are consistent JSON: `{timestamp, status, error, message, fieldErrors}`.

## Project layout

```
backend/    Spring Boot (net/ SSRF + probe, service/ scheduler + incidents, security/, web/, db/migration/)
frontend/   React + Vite + Tailwind (no chart library: the latency chart is plain SVG)
scripts/    cloudpulse_check.py (automation / CloudWatch), flaky_target.py (test target)
deploy/     Caddyfile (HTTPS reverse proxy for EC2)
docs/       AWS deployment guide, resume + interview notes
.github/    CI and OIDC deploy workflows
```

## Deploying to AWS
See [docs/AWS_DEPLOYMENT.md](docs/AWS_DEPLOYMENT.md). Resume and interview guidance, including which claims you may and may not make, is in [docs/RESUME_AND_INTERVIEW.md](docs/RESUME_AND_INTERVIEW.md).

## Known limitations (be honest about these in interviews)
- Single instance and a single admin account. No high availability, no multi-user roles.
- Login rate-limit is in memory and keyed by client IP (behind a proxy all users share the proxy's IP).
- The JWT lives in `sessionStorage`, so an XSS bug could steal it. Mitigated by React escaping and no third-party scripts, but an httpOnly cookie would be stronger.
- Only HTTP(S) checks. No TCP/ICMP/DNS checks and no response-body assertions.
- Scheduler is not leader-elected, so run exactly one backend instance.
