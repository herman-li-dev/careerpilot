<template>
  <PublicSiteLayout>
    <article class="legal-document">
      <header>
        <p class="eyebrow">CAREERPILOT</p>
        <h1>Privacy Policy</h1>
        <p class="effective-date">Effective September 25, 2026</p>
        <p class="summary">
          This policy explains how CareerPilot handles account information, career documents,
          generated results, and optional AI processing.
        </p>
      </header>

      <section>
        <h2>1. Who operates CareerPilot</h2>
        <p>
          CareerPilot is an independent portfolio and career-preparation application. Privacy questions
          or deletion requests may be sent to
          <a href="mailto:hermanli.dev@gmail.com">hermanli.dev@gmail.com</a>.
        </p>
      </section>

      <section>
        <h2>2. Google sign-in and account identity</h2>
        <p>
          CareerPilot uses Google sign-in through Clerk to authenticate users. Clerk may process your Google
          account identifier, name, email address, profile image, session information, and security signals.
          CareerPilot stores an internal user identifier linked to the verified Clerk issuer and subject so
          that your resources remain associated with your account.
        </p>
        <p>
          CareerPilot does not receive or store your Google password or Google OAuth access token, and it does
          not request access to Gmail, Google Drive, Calendar, contacts, or other Google services.
        </p>
      </section>

      <section>
        <h2>3. Information you provide</h2>
        <p>Depending on the features you use, CareerPilot may store:</p>
        <ul>
          <li>Resume text and job-description text saved in your private workspace;</li>
          <li>validated structured Resume and job-description data;</li>
          <li>match reports, preparation plans, task status, and interview-preparation results;</li>
          <li>daily feature-usage counters and other security or abuse-prevention state.</li>
        </ul>
        <p>
          When you upload a PDF or DOCX Resume to your workspace, CareerPilot validates the file and stores
          extracted text in the Resume record. It does not retain the original uploaded file, original filename,
          or reconstructed document formatting. Scanned-document OCR is not supported.
        </p>
      </section>

      <section>
        <h2>4. AI and retrieval processing</h2>
        <p>
          When an AI feature is enabled and you explicitly start it, CareerPilot may send relevant Resume,
          job-description, or derived evidence to the configured DashScope model provider. Vector Resume Review
          may also send bounded evidence for embedding and retrieval against CareerPilot's public synthetic guide.
          Provider processing is governed by that provider's terms and privacy practices.
        </p>
        <p>
          Resume Review suggestions are request-scoped: CareerPilot does not persist the review prompt, model
          response, or suggestions and does not automatically rewrite your Resume. Authentication, ownership,
          quota, and security failures are not bypassed through an AI fallback.
        </p>
      </section>

      <section>
        <h2>5. How information is used</h2>
        <p>CareerPilot uses information only to:</p>
        <ul>
          <li>authenticate your account and isolate your resources from other users;</li>
          <li>provide document organization, matching, planning, and interview-preparation features;</li>
          <li>apply usage limits, prevent abuse, diagnose failures, and maintain service security;</li>
          <li>comply with applicable legal obligations.</li>
        </ul>
        <p>CareerPilot does not sell personal information or use it for targeted advertising.</p>
      </section>

      <section>
        <h2>6. Logs and technical data</h2>
        <p>
          The service may process request identifiers, route names, status codes, latency, timestamps, and
          network information required for security and operation. Application audit logs are designed not to
          contain Resume text, prompts, complete model responses, Google or Clerk tokens, Clerk subject values,
          or email addresses.
        </p>
      </section>

      <section>
        <h2>7. Service providers and disclosure</h2>
        <p>
          CareerPilot relies on service providers including Google for sign-in, Clerk for authentication,
          hosting and database infrastructure for application operation, and DashScope when AI features are
          enabled. Information may also be disclosed when required by law or necessary to protect users and
          the service. Each provider processes information under its own terms and privacy practices.
        </p>
      </section>

      <section>
        <h2>8. Retention, deletion, and security</h2>
        <p>
          Saved workspace content is retained while needed to provide the service. Supported resources can be
          deleted from the application when they are not required by linked history. You may request account and
          personal-data deletion using the contact address above. Limited records may be retained when required
          for security, legal compliance, backup integrity, or resolving disputes.
        </p>
        <p>
          CareerPilot uses access controls, encrypted transport, ownership checks, bounded uploads, and usage
          limits. No Internet service can guarantee absolute security, so do not upload information that is not
          needed for career preparation.
        </p>
      </section>

      <section>
        <h2>9. International processing and children</h2>
        <p>
          Service providers may process information in countries different from your own. CareerPilot is not
          directed to children under 13, and users must be able to consent to the processing described here.
        </p>
      </section>

      <section>
        <h2>10. Changes to this policy</h2>
        <p>
          Material changes will be reflected on this page with a revised effective date. Continued use after a
          change means the updated policy applies to subsequent use.
        </p>
      </section>
    </article>
  </PublicSiteLayout>
</template>

<script setup>
import PublicSiteLayout from '../components/PublicSiteLayout.vue'
</script>

<style scoped src="../legal.css"></style>
