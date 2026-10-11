# Resume and interview notes

Use only what you have actually run. Mark each item below honestly after you verify it yourself.

## Status of this repository when it was generated

| Item | Status |
|---|---|
| SSRF guard + HTTP probe (`net/`) | Unit-tested: 48 JUnit tests passed (private/metadata ranges, redirects, loops, timeouts) |
| Incident state machine (`CheckRecorder`) | Logic exercised with in-memory fakes; also covered by Mockito tests in the repo |
| Spring Boot wiring, JPA, security, controllers, integration tests | **Written, not compiled or run** (no access to Maven Central where this was generated). Run `mvn verify` and fix anything it reports. |
| React dashboard | Built with Vite, unit tests pass, UI checked in a browser against a mocked API |
| Docker / Compose, CI, deploy workflow | Written; YAML parsed, **never executed** |
| Python script | Tested against a fake API (exit codes 0/1/2) |
| AWS deployment | **Not performed.** Planned only |

## Requirement-to-evidence map (fill the last column yourself)

| Job-description area | Where it lives | You have verified it? |
|---|---|---|
| Cloud infrastructure | `docker-compose.prod.yml`, `docs/AWS_DEPLOYMENT.md`, EC2 | only after you deploy |
| Automation / scripting | `scripts/cloudpulse_check.py`, `flaky_target.py` | |
| Security best practices | `SsrfGuard`, JWT auth, bcrypt, rate limit, env secrets, non-root containers | |
| Monitoring / troubleshooting | scheduler, incident timeline, Actuator health, CloudWatch logs | |
| Databases / RDBMS | MySQL schema + Flyway migration, JPA | |
| Version control / CI/CD | Git, GitHub Actions `ci.yml`, `deploy.yml` | |

## Resume bullets (adapt once the facts are true for you)
- Built **CloudPulse**, a full-stack monitoring dashboard (Java 21, Spring Boot, React, MySQL) that checks my Vercel and Render apps on a backend scheduler and records status, latency and incidents.
- Implemented SSRF protection for user-supplied URLs: DNS-resolved IP validation, private/metadata range blocking and redirect re-validation, covered by automated tests.
- Containerised the stack with Docker Compose and set up GitHub Actions CI (build, test, image build). *(Add: "deployed to AWS EC2 with CloudWatch logging" **only after you have done and verified it**.)*

Tech line: `Java 21, Spring Boot 3, React, Vite, Tailwind CSS, MySQL, Flyway, Docker, GitHub Actions, Python` (+ `AWS EC2, CloudWatch` after deployment).

## Do NOT claim
- AWS deployment, CloudWatch, or OIDC deploys unless you completed and verified them.
- Uptime percentages, number of users, or "production" or "high availability". It is a single instance.
- Terraform, Kubernetes, Kafka, Prometheus, Azure/GCP, SAP: none are used.
- Any performance figure you did not measure.

## Ten likely interview questions
1. **How does the scheduler work and why is it independent of the browser?** A `@Scheduled` tick every 5 s finds enabled services whose interval has elapsed and runs the probes on virtual threads. A set of in-flight IDs prevents overlapping checks of one service. The UI only reads stored data.
2. **How do you avoid false alarms?** A failure threshold per service (default 3 consecutive failures) plus an optional recovery threshold. One transient failure never opens an incident.
3. **Explain the incident lifecycle.** `CheckRecorder` is a small state machine: UNKNOWN/UP → DOWN after N failures (creates one incident, deduplicated by looking for an open one), DOWN → UP after M successes (sets `resolved_at`). It runs in one transaction; alerts are sent after commit.
4. **What is SSRF and how did you mitigate it?** Tricking the server into calling internal resources like cloud metadata at 169.254.169.254. I validate scheme and host, resolve DNS and reject any private/loopback/link-local/reserved address, then connect directly to the validated IP so DNS rebinding can't swap it, and re-validate every redirect hop.
5. **Why a hand-written HTTP probe instead of a library?** Standard clients resolve DNS internally, so I can't guarantee the IP I validated is the IP they connect to. Connecting the socket myself closes that gap. TLS is layered on with the original host name so certificates are still verified. (Trade-off: it only handles what I implemented, i.e. GET, status line and headers.)
6. **How is authentication done?** `/api/auth/login` checks credentials from environment variables (bcrypt-hashed in memory, constant-time username compare), rate-limits failures, and returns an HS256 JWT. Spring Security's resource server validates it on every other request. Sessions are stateless.
7. **Where do secrets live?** Environment variables only (`.env` ignored by Git, GitHub secrets, or SSM Parameter Store). The app refuses to start without strong values. Nothing secret is in the frontend bundle.
8. **Why Flyway and why `ddl-auto: none`?** The schema is versioned and reproducible across environments; Hibernate must not alter production tables.
9. **How would you scale or harden it?** Move to RDS, run behind a load balancer with leader election (or a DB lock) for the scheduler, httpOnly cookies, per-user roles, Prometheus metrics, Terraform for infrastructure. I'd state clearly that these are not built.
10. **How did you test failure and recovery?** `scripts/flaky_target.py` switches a local target between OK, 503 and slow; I watched the incident open after three failed checks, timeouts reported, and the incident close on recovery. Unit tests cover the state machine and the SSRF guard.

## Verification commands
```bash
cd backend && mvn -B verify                              # tests
docker compose up --build -d && docker compose ps       # stack healthy
curl -i http://localhost:8081/api/services              # 401 without a token
curl -s http://localhost:8081/actuator/health           # {"status":"UP"}
python scripts/cloudpulse_check.py; echo $?              # 0 healthy, 1 problems, 2 API error
git log --oneline && gh run list                         # version control + CI history
```
