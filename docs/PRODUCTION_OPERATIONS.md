# GRACE Production Operations

## Deployment

Use a dedicated Linux host with current Docker Engine and Docker Compose. The production overlay requires Compose support for the `!override` tag. Populate `.env.production` from `.env.production.example` using secret-manager values, then install the TLS certificate as `deploy/certs/fullchain.pem` and `deploy/certs/privkey.pem`.

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.production.yml config --quiet
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.production.yml up -d --build
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.production.yml ps
```

Only nginx publishes host ports in the production overlay. Application, database, object storage, search, metrics, and management endpoints remain on the private Compose network. A separate institutional load balancer is acceptable when it replaces forwarded client-address headers and preserves `X-Forwarded-Proto`.

## Security Baseline

- Keep secrets in a secrets manager and rotate database, Redis, object-store, identity, and JWT credentials at least annually and after suspected disclosure.
- Remove bootstrap administrator credentials after provisioning. Require MFA for privileged Keycloak roles.
- Limit the host firewall to TCP 80/443 and an allowlisted administration path. Never publish ports 5432, 6379, 8080, 8081, 9000, 9001, or 9200.
- Backend and frontend run without root and with read-only production filesystems. Apply image and host security updates on a scheduled cadence.
- The API and frontend emit CSP, permissions, referrer, framing, and MIME-sniffing controls. Nginx adds HSTS at the TLS boundary.
- Rate-limit identities are SHA-256 hashes of the direct peer address. Redis coordinates limits across replicas; bounded local counters retain protection during Redis interruption.
- `SERVER_FORWARD_HEADERS_STRATEGY=framework` is safe only when the backend is exclusively reachable through the trusted proxy.

## Monitoring And Logs

Prometheus scrapes `backend:8081/actuator/prometheus` every 15 seconds. The management port is intentionally not published. Import standard JVM, Spring MVC, PostgreSQL pool, and Redis dashboards into the institution's Grafana service.

| Signal | Suggested alert |
| --- | --- |
| Readiness | down for 2 minutes |
| HTTP 5xx | over 2% for 5 minutes |
| HTTP p95 | over 1 second for 10 minutes |
| JVM heap | over 85% for 10 minutes |
| JDBC pool | pending connections for 5 minutes |
| Rate limiting | sustained `grace_rate_limit_blocked_total` increase |
| Redis fallback | sustained `grace_rate_limit_fallback_total` increase |
| Disk | less than 20% free |
| Backup | no verified backup in 24 hours |

Spring Boot writes ECS JSON to stdout. `X-Request-ID` is returned to callers and included as `request.id` in backend logs. Restrict log access and alert on authentication or authorization failures, unusual 429 volume, and repeated 5xx responses. Never enable request-body logging in production.

## Backup And Recovery

Run from a protected operator host with Docker access:

```powershell
./scripts/Backup-Grace.ps1 -BackupRoot E:\GRACE-Backups -RetentionDays 30
./scripts/Test-GraceBackup.ps1 -BackupPath E:\GRACE-Backups\20260807-020000
```

The job creates a PostgreSQL custom-format archive, copies MinIO data, and records SHA-256 checksums. Replicate completed directories to encrypted off-site storage. Local retention is not a substitute for an off-site lifecycle policy.

Quarterly restore drill:

1. Verify the backup with `Test-GraceBackup.ps1`.
2. Restore into an isolated, empty GRACE environment with outbound integrations disabled.
3. Run `pg_restore --clean --if-exists --no-owner --dbname grace postgres.dump` from its PostgreSQL container.
4. Stop isolated MinIO, replace only that environment's MinIO data volume, and restart it.
5. Run migrations, readiness, login, search, guide rendering, and object download checks.
6. Record duration and evidence. Initial targets are RPO 24 hours and RTO 4 hours; tighten these to institutional policy.

## Performance And Accessibility

Run the API smoke test while the stack is healthy:

```powershell
Get-Content -Raw tests/performance/smoke.js | docker run --rm -i -e GRACE_BASE_URL=http://host.docker.internal:8080 grafana/k6 run -
```

The gate requires under 1% failures, overall p95 below 750 ms, and search p95 below 1 second. Establish production baselines with representative data before raising traffic.

The frontend suite scans all public routes for automated WCAG A/AA findings:

```bash
cd frontend
pnpm exec playwright install chromium
pnpm test:a11y
```

Automation cannot replace manual review. Each release should also verify keyboard-only navigation, visible focus, 200% zoom, 320 CSS-pixel reflow, reduced motion, headings and landmarks, screen-reader names, error identification, and contrast with institution branding.

## Release And Rollback

Build and test immutable images in CI, pin deployed image digests, back up before database-changing releases, and use an announced release window. Verify readiness, frontend health, login, public search, Prometheus ingestion, and logs after deployment. Roll back images only while migrations remain backward-compatible; otherwise follow the approved recovery plan using the pre-release backup.
