# CareerPilot frontend

Vue 3 and Vite frontend for CareerPilot. It provides the authenticated document workspace, Resume PDF/DOCX
upload, match reports, preparation plans, interview questions, and ephemeral Resume Review UI.

## Local development

Start the CareerPilot backend on `http://localhost:8123/api`, then run:

```bash
npm ci
npm run dev
```

Vite serves the local frontend on the port printed in the terminal. Development API requests use
`http://localhost:8123/api` and include the authentication cookie.

## Production builds

Normal application:

```bash
npm run build
```

Read-only synthetic demo:

```bash
VITE_CAREERPILOT_DEMO_MODE=true npm run build
```

The optional public Google-authenticated upload slice is disabled by default. For a local or CI build that
includes it, set both feature flags and a browser-safe Clerk publishable key:

```dotenv
VITE_CAREERPILOT_PUBLIC_RAG_AUTH_ENABLED=true
VITE_CAREERPILOT_PUBLIC_RAG_UPLOAD_ENABLED=true
VITE_CLERK_PUBLISHABLE_KEY=pk_test_replace_with_your_publishable_key
```

The upload form accepts one PDF or DOCX up to 5 MiB after the backend verifies the Clerk session. It validates
and extracts text only for that request; it does not persist the file or text and does not call AI or RAG. The
panel explicitly distinguishes this validation-only behavior from a future live review. Before any Resume text
is sent to an AI provider, that later flow must display a separate provider-processing disclosure.

The production Nginx image serves the generated assets and proxies same-origin `/api` requests to the private
backend service. With the optional flags off, the public demo remains synthetic-only, AI-disabled, and
read-only.
