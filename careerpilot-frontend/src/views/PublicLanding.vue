<template>
  <PublicSiteLayout>
    <section class="hero" aria-labelledby="public-title">
      <div>
        <p class="eyebrow">EVIDENCE-GROUNDED CAREER PREPARATION</p>
        <h1 id="public-title">Prepare for the role with evidence you already have.</h1>
        <p class="hero-copy">
          CareerPilot helps you organize a Resume and job description, understand their match,
          and turn verified gaps into a focused preparation plan.
        </p>
        <div class="hero-actions">
          <RouterLink class="primary-action" to="/app">Continue with Google</RouterLink>
          <RouterLink class="secondary-action" to="/privacy">Review privacy practices</RouterLink>
        </div>
        <p class="identity-note">
          Google sign-in is used only for account authentication. CareerPilot does not request access
          to Gmail, Google Drive, Calendar, contacts, or other Google services.
        </p>
      </div>
      <aside class="process-card" aria-label="CareerPilot workflow">
        <p class="card-label">YOUR PRIVATE WORKSPACE</p>
        <ol>
          <li><span>1</span> Add a Resume and job description</li>
          <li><span>2</span> Validate evidence before analysis</li>
          <li><span>3</span> Review the match and preparation plan</li>
          <li><span>4</span> Practice evidence-based interview answers</li>
        </ol>
      </aside>
    </section>

    <section class="principles" aria-labelledby="principles-title">
      <div class="section-heading">
        <p class="eyebrow">DESIGNED FOR CONTROL</p>
        <h2 id="principles-title">Your documents remain tied to your signed-in account.</h2>
      </div>
      <div class="principle-grid">
        <article>
          <h3>Private by default</h3>
          <p>The backend derives ownership from the verified session instead of trusting a browser-supplied user ID.</p>
        </article>
        <article>
          <h3>Original files are not retained</h3>
          <p>PDF or DOCX uploads are validated and converted to text. CareerPilot stores the extracted text, not the original file.</p>
        </article>
        <article>
          <h3>AI is an explicit action</h3>
          <p>When AI features are available, relevant evidence is sent only after you start the action and acknowledge provider processing.</p>
        </article>
      </div>
    </section>
  </PublicSiteLayout>
</template>

<script setup>
import PublicSiteLayout from '../components/PublicSiteLayout.vue'
</script>

<style scoped>
.hero { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(300px, .65fr); gap: 64px; align-items: center; }
.eyebrow { color: #5967d8; font-size: .75rem; font-weight: 800; letter-spacing: .14em; }
h1 { max-width: 850px; margin-top: 18px; font-size: clamp(3rem, 7vw, 6.2rem); line-height: .96; letter-spacing: -.06em; }
.hero-copy { max-width: 720px; margin-top: 28px; color: #64708a; font-size: 1.14rem; line-height: 1.7; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 30px; }
.primary-action,
.secondary-action { padding: 13px 18px; border-radius: 11px; font-weight: 800; }
.primary-action { background: #30366d; color: #fff; }
.primary-action:hover { background: #252a59; }
.secondary-action { border: 1px solid #cdd3e2; color: #404b68; }
.secondary-action:hover { border-color: #5967d8; }
.identity-note { max-width: 720px; margin-top: 22px; color: #68738a; font-size: .85rem; line-height: 1.6; }
.process-card { padding: 28px; border: 1px solid #dfe3ee; border-radius: 22px; background: rgba(255, 255, 255, .8); box-shadow: 0 20px 70px rgba(49, 57, 104, .08); }
.card-label { color: #5967d8; font-size: .72rem; font-weight: 800; letter-spacing: .12em; }
.process-card ol { display: grid; gap: 20px; margin-top: 24px; list-style: none; }
.process-card li { display: flex; align-items: center; gap: 13px; color: #3e4861; font-weight: 700; line-height: 1.4; }
.process-card span { display: grid; flex: 0 0 32px; height: 32px; place-items: center; border-radius: 50%; background: #eef0fa; color: #5967d8; }
.principles { margin-top: 112px; }
.section-heading h2 { max-width: 720px; margin-top: 12px; font-size: clamp(2rem, 4vw, 3.5rem); line-height: 1.08; letter-spacing: -.04em; }
.principle-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; margin-top: 34px; }
.principle-grid article { padding: 26px; border: 1px solid #dfe3ee; border-radius: 18px; background: #fff; }
.principle-grid h3 { font-size: 1.02rem; }
.principle-grid p { margin-top: 12px; color: #68738a; line-height: 1.65; }
@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; }
  .principle-grid { grid-template-columns: 1fr; }
}
</style>
