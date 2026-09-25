# CareerPilot Read-Only Demo Deployment

Status: published at [careerpilot.hermanlidev.com](https://careerpilot.hermanlidev.com); production package and
provider-specific deployment verified through DEPLOY-02 on 2026-09-11.

## 1. Deployment boundary

`compose.production.yml` runs three services on one private Docker network:

```text
Internet -> TLS reverse proxy -> frontend Nginx -> Spring Boot -> PostgreSQL
                                  only public port      private       private
```

The included Nginx container listens on HTTP so it can sit behind a hosting platform or host-level TLS reverse
proxy. Do not expose the backend or PostgreSQL ports. HTTPS is required for the default Secure session Cookie.
The frontend host port binds only to `127.0.0.1`; the host-level proxy is the only intended caller.

This public mode is intentionally different from local development:

- `CAREERPILOT_DEMO_ENABLED=true` resets only the exact synthetic demo account at backend startup and creates
  one synthetic Resume, JD, completed report, preparation plan, tasks, and interview session;
- `CAREERPILOT_AI_ENABLED=false` and `CAREERPILOT_RAG_ENABLED=false` prevent provider calls and vector retrieval,
  removing the need for an API key;
- `CAREERPILOT_RAG_SIMILARITY_THRESHOLD=0.50` documents the calibrated local default but is inactive while RAG
  remains disabled;
- the browser offers credential-free demo sign-in and hides mutation controls;
- Nginx and the backend independently reject writes except demo sign-in/sign-out and the existing
  non-persistent Resume Review / read-existing Interview Preparation actions;
- uploaded files, pasted content, user registration, task updates, new analyses, and regeneration are not
  available through the public demo;
- the seeded account password is random and never published or stored in source control.
- the optional Clerk public-RAG authentication and request-scoped upload boundaries remain off unless their
  backend and frontend feature flags are deliberately enabled; neither boundary enables AI or RAG.

Run a single backend replica in this mode. Startup seeding is deliberately transactional and is not designed
for concurrent replicas.

### Verified portfolio deployment

The current public deployment uses this path:

```text
Browser
  -> Cloudflare DNS (DNS-only)
  -> DigitalOcean Ubuntu host, ports 80/443
  -> BaoTa-managed host Nginx and Let's Encrypt TLS
  -> 127.0.0.1:18080
  -> Docker frontend Nginx
  -> private Docker Spring Boot service
  -> private Docker PostgreSQL service
```

The frontend is the only container with a published host port, and that port is bound to loopback. Spring Boot
and PostgreSQL have no public host-port mapping. The public health endpoint returns HTTPS 200, HTTP redirects
to HTTPS, HSTS is enabled, and port 18080 is not reachable from the Internet. This topology was verified on the
current 1-vCPU/2-GB portfolio host without adding a model key or personal data.

## 2. Required host configuration

The host needs Docker Engine with Compose, persistent storage for the PostgreSQL volume, and a TLS endpoint.
Create `.env.production` from the committed example and keep the real file outside version control:

```powershell
Copy-Item .env.production.example .env.production
```

Set:

- `CAREERPILOT_DB_PASSWORD`: a new strong database password;
- `CAREERPILOT_JWT_SECRET`: a random secret of at least 32 bytes;
- `CAREERPILOT_PUBLIC_ORIGIN`: the exact public HTTPS origin, with no path;
- `CAREERPILOT_HTTP_PORT`: the host-only HTTP port consumed by the outer TLS proxy.

For a `PUBLIC-AUTH-02` rollout, first configure a Clerk production instance with Google as its only
sign-in/sign-up strategy. Then set `CAREERPILOT_CLERK_AUTH_ENABLED=true`,
`VITE_CAREERPILOT_AUTH_ENABLED=true`, the Clerk Frontend API origin as `CAREERPILOT_CLERK_ISSUER`,
the exact public CareerPilot origin as `CAREERPILOT_CLERK_AUTHORIZED_PARTIES`, and the browser-safe publishable
key as `VITE_CLERK_PUBLISHABLE_KEY`. The application flag replaces the legacy Cookie identity on personal APIs
and provisions an internal `app_user.id` through the unique Clerk `(issuer, subject)` mapping. Never add a Clerk
secret key to Compose or a Vite variable. Keep AI and RAG disabled until quota, rate-limit, concurrency, and
cost-control verification is complete. Upload
validation additionally requires `CAREERPILOT_PUBLIC_RAG_UPLOAD_ENABLED=true` and
`VITE_CAREERPILOT_PUBLIC_RAG_UPLOAD_ENABLED=true`; it discards the request bytes and extracted text and never
invokes the provider. The
frontend container uses that same exact Clerk issuer when rendering its Nginx CSP and separately allows only
Clerk's documented protection, challenge, image, and worker sources; do not replace it with a wildcard HTTPS
source.

Keep `CAREERPILOT_PUBLIC_RAG_GUARD_ENABLED=false` until production live-review acceptance. Before enabling it,
set a stable random
`CAREERPILOT_PUBLIC_RAG_GUARD_HMAC_SECRET` containing at least 32 UTF-8 bytes. It is a backend secret: do not put
it in a Vite variable, frontend image build argument, browser response, or log. Changing it during a UTC day
changes user quota keys and can reset effective per-user limits, so rotate it only as a deliberate operational
change.

Live review additionally requires `CAREERPILOT_AI_ENABLED=true`, `CAREERPILOT_RAG_ENABLED=true`, and a
process-only `DASHSCOPE_API_KEY`. The production Compose file passes these values only from the deployment
environment and defaults every provider flag off. Do not enable only part of this set: the API deliberately
returns `503 AI_UNAVAILABLE` until AI, RAG, and the guard are all available.

The inner Nginx uses the host proxy's `X-Real-IP` value for its two-requests-per-minute public-RAG limit. Because
the container binds only to `127.0.0.1`, the host proxy is the only intended network caller. The BaoTa/host Nginx
configuration must overwrite rather than append this header:

```nginx
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
```

Do not publish the inner Nginx port on a non-loopback interface while it trusts this header. The upload and
reserved live-review paths share one IP bucket; excessive requests receive a JSON
`429 PUBLIC_RAG_IP_RATE_LIMITED` response before reaching Spring Boot.

The checked-in limits target the current low-traffic portfolio host: `640m` for Spring Boot, `320m` for
PostgreSQL, and `96m` for Nginx. The Java heap is capped at half of its container limit. These limits prevent
CareerPilot from claiming all memory on a 2 GB host, but they do not make that host suitable for image builds.

Do not add a model key or enable RAG. Do not put a personal Resume, proprietary guide, session Cookie, token, or
real user record in this environment. The pgvector-capable database image supports the local implementation but
the public demo indexes no vector knowledge because its AI/RAG flags remain false.

## 3. Validate and start

Validate interpolation without printing the resolved configuration:

```powershell
docker compose --env-file .env.production -f compose.production.yml config --quiet
```

Build locally on a `linux/amd64` Docker host:

```powershell
docker compose --env-file .env.production -f compose.production.yml build
```

For the current 1-vCPU/2-GB server, load the prebuilt image archive and start without building:

```bash
docker load -i careerpilot-images.tar.gz
docker compose --env-file .env.production -f compose.production.yml up -d --no-build
docker compose --env-file .env.production -f compose.production.yml ps
```

The backend build context excludes `careerpilot-private-knowledge/`. The bundled CareerPilot synthetic review
guide remains included, but public-demo AI/RAG settings keep the vector path disabled.

Point the outer HTTPS proxy at `127.0.0.1:${CAREERPILOT_HTTP_PORT}`. The proxy must preserve `Host`,
overwrite `X-Real-IP` with the direct client address, and preserve `X-Forwarded-For` and `X-Forwarded-Proto`.
Then verify:

```text
GET https://your-domain.example/api/health -> 200
Open read-only demo -> synthetic account and fixture are visible
POST /api/resumes -> 403 DEMO_READ_ONLY
```

Also verify that browser Cookies are `HttpOnly`, `Secure`, `SameSite=Strict`, no backend/database port is
public, and logs contain no document text, tokens, secrets, prompt bodies, or model responses. Public-RAG audit
events are intentionally limited to generated request ID, method, fixed route, status, and latency. Do not enable
HTTP debug request-detail logging, multipart-body logging, authorization-header logging, or proxy request-body
logging in production.

The validation-only UI states that CareerPilot does not store the uploaded file, filename, or extracted text
and that validation does not contact an AI provider. The live-review control separately requires an explicit
acknowledgement before it sends bounded Resume evidence to the configured provider. Do not add retention or
region claims beyond the provider deployment's current terms.

“Not stored” means no database row, application-managed upload file, durable upload volume, log body, or browser
storage. Nginx and Spring multipart handling may use bounded container-local temporary request buffers, which
are discarded with the request lifecycle. Do not describe this validation path as memory-only, and do not mount
its temporary directories on persistent volumes.

## 4. Updates and rollback

CI must pass before an update. Rebuild the same stack after pulling an approved revision. The synthetic fixture
is recreated on backend startup; no public user content is expected to survive. The PostgreSQL volume remains
available for operational rollback, but it must contain synthetic data only.

Before loading a replacement image, keep a local rollback tag for the currently running image. For a
frontend-only update:

```bash
cd /www/wwwroot/careerpilot
docker image tag careerpilot-demo-frontend:latest careerpilot-demo-frontend:rollback
docker load -i careerpilot-frontend-update.tar.gz
docker compose --env-file .env.production -f compose.production.yml up -d --no-deps --force-recreate frontend
docker compose --env-file .env.production -f compose.production.yml ps
curl -i https://careerpilot.hermanlidev.com/api/health
```

If that frontend update fails, restore only the frontend image and container:

```bash
docker image tag careerpilot-demo-frontend:rollback careerpilot-demo-frontend:latest
docker compose --env-file .env.production -f compose.production.yml up -d --no-deps --force-recreate frontend
```

For a full tested image set, load the archive and recreate the services without deleting the PostgreSQL volume:

```bash
docker load -i careerpilot-images.tar.gz
docker compose --env-file .env.production -f compose.production.yml up -d --no-build
docker compose --env-file .env.production -f compose.production.yml ps
```

Stop the application without deleting the volume:

```powershell
docker compose --env-file .env.production -f compose.production.yml down
```

Deleting the volume is intentionally not included here because it is destructive.

The current database contains only reproducible synthetic demo data, so application recovery does not depend
on retaining that data. Preserve `.env.production` securely on the server, rotate its secrets if exposed, and
never copy it into an image archive or source-control commit.

## 5. Not included

The repository package does not automate cloud-account creation, domain purchase, DNS, or TLS issuance; the
verified portfolio deployment configures those provider-owned concerns manually. It does not add Kubernetes,
a container registry release workflow, analytics, monitoring SaaS, live AI/RAG in the public demo, OCR, private
knowledge ingestion, or real-user data collection.
