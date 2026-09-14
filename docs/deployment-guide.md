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
- `CAREERPILOT_AI_ENABLED=false` prevents provider calls and removes the need for an API key;
- the browser offers credential-free demo sign-in and hides mutation controls;
- Nginx and the backend independently reject writes except demo sign-in/sign-out and the existing
  non-persistent Resume Review / read-existing Interview Preparation actions;
- uploaded files, pasted content, user registration, task updates, new analyses, and regeneration are not
  available through the public demo;
- the seeded account password is random and never published or stored in source control.

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

The checked-in limits target the current low-traffic portfolio host: `640m` for Spring Boot, `320m` for
PostgreSQL, and `96m` for Nginx. The Java heap is capped at half of its container limit. These limits prevent
CareerPilot from claiming all memory on a 2 GB host, but they do not make that host suitable for image builds.

Do not add a model key. Do not put a personal Resume, proprietary guide, session Cookie, token, or real user
record in this environment.

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
rules remain included.

Point the outer HTTPS proxy at `127.0.0.1:${CAREERPILOT_HTTP_PORT}`. The proxy must preserve `Host`,
`X-Forwarded-For`, and `X-Forwarded-Proto`. Then verify:

```text
GET https://your-domain.example/api/health -> 200
Open read-only demo -> synthetic account and fixture are visible
POST /api/resumes -> 403 DEMO_READ_ONLY
```

Also verify that browser Cookies are `HttpOnly`, `Secure`, `SameSite=Strict`, no backend/database port is
public, and logs contain no document text, tokens, secrets, prompt bodies, or model responses.

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
a container registry release workflow, analytics, monitoring SaaS, live AI, OCR, vector RAG, or real-user data
collection.
