<template>
  <PublicSiteLayout>
    <article class="legal-document">
      <header>
        <p class="eyebrow">CAREERPILOT</p>
        <h1>Terms of Service</h1>
        <p class="effective-date">Effective September 25, 2026</p>
        <p class="summary">
          These terms govern access to CareerPilot and its career-preparation features.
        </p>
      </header>

      <section>
        <h2>1. Acceptance and eligibility</h2>
        <p>
          By accessing CareerPilot, you agree to these terms and the <RouterLink to="/privacy">Privacy Policy</RouterLink>.
          You must be legally able to accept these terms and use Google sign-in for your own account.
        </p>
      </section>

      <section>
        <h2>2. Account responsibility</h2>
        <p>
          CareerPilot currently supports Google sign-in. You are responsible for protecting your Google and
          Clerk sessions and for activity performed through your account. Do not share authentication tokens,
          attempt to impersonate another user, or bypass resource-ownership controls.
        </p>
      </section>

      <section>
        <h2>3. Your content</h2>
        <p>
          You retain ownership of Resume, job-description, and other content you provide. You grant CareerPilot
          permission to process that content only as needed to provide the features you request, maintain service
          security, and comply with law. You must have the right to submit the content and should remove
          unnecessary sensitive or third-party information before uploading it.
        </p>
      </section>

      <section>
        <h2>4. AI-generated assistance</h2>
        <p>
          AI-generated matches, suggestions, plans, and interview material may be incomplete or incorrect. They
          are preparation aids, not employment, legal, immigration, financial, or professional advice. You are
          responsible for reviewing all output, preserving truthful Resume claims, and deciding how to use it.
          CareerPilot does not guarantee interviews, offers, hiring outcomes, or Resume performance.
        </p>
      </section>

      <section>
        <h2>5. Acceptable use</h2>
        <p>You may not:</p>
        <ul>
          <li>access another user's account or resources;</li>
          <li>upload malicious, unlawful, infringing, or deceptive content;</li>
          <li>probe, disrupt, overload, scrape, or circumvent security, quotas, or rate limits;</li>
          <li>use automated access except where CareerPilot explicitly permits it;</li>
          <li>misrepresent generated content as verified fact without reviewing its source evidence.</li>
        </ul>
      </section>

      <section>
        <h2>6. Availability and changes</h2>
        <p>
          CareerPilot may change, limit, suspend, or discontinue features, including AI functionality, to protect
          users, control cost, perform maintenance, or comply with law. Data loss cannot be ruled out; keep your
          own copy of important career documents.
        </p>
      </section>

      <section>
        <h2>7. Suspension and termination</h2>
        <p>
          Access may be restricted or terminated for abuse, security threats, unlawful activity, or material
          violation of these terms. You may stop using CareerPilot at any time and may request account deletion
          as described in the Privacy Policy.
        </p>
      </section>

      <section>
        <h2>8. Disclaimers and limitation</h2>
        <p>
          CareerPilot is provided on an "as available" basis without warranties of uninterrupted operation,
          accuracy, fitness for a particular purpose, or employment results. To the extent permitted by law,
          the operator is not liable for indirect, incidental, special, consequential, or lost-opportunity
          damages resulting from use of the service.
        </p>
      </section>

      <section>
        <h2>9. Changes and contact</h2>
        <p>
          Updated terms will appear on this page with a revised effective date. Questions may be sent to
          <a href="mailto:hermanli.dev@gmail.com">hermanli.dev@gmail.com</a>.
        </p>
      </section>
    </article>
  </PublicSiteLayout>
</template>

<script setup>
import PublicSiteLayout from '../components/PublicSiteLayout.vue'
</script>

<style scoped src="../legal.css"></style>
