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

The production Nginx image serves the generated assets and proxies same-origin `/api` requests to the private
backend service. The public demo must remain synthetic-only, AI-disabled, and read-only.
