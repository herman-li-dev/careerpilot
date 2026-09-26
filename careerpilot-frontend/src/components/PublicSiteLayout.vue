<template>
  <div class="public-site">
    <header class="public-header">
      <RouterLink class="brand" to="/" aria-label="CareerPilot home">
        <span class="brand-mark">CP</span>
        <span>CareerPilot</span>
      </RouterLink>
      <nav class="public-nav" aria-label="Public navigation">
        <RouterLink to="/privacy">Privacy</RouterLink>
        <RouterLink to="/terms">Terms</RouterLink>
        <RouterLink class="workspace-link" to="/app">Open workspace</RouterLink>
      </nav>
    </header>

    <main class="public-main">
      <slot />
    </main>

    <footer class="public-footer">
      <p>CareerPilot is an independent career-preparation application.</p>
      <nav aria-label="Legal links">
        <RouterLink to="/privacy">Privacy Policy</RouterLink>
        <RouterLink to="/terms">Terms of Service</RouterLink>
        <a href="mailto:hermanli.dev@gmail.com">Support</a>
      </nav>
    </footer>
  </div>
</template>

<style scoped>
.public-site {
  min-height: 100vh;
  background:
    radial-gradient(circle at 86% 8%, rgba(89, 103, 216, .12), transparent 28%),
    #f7f8fc;
  color: #182033;
}
.public-header,
.public-footer {
  width: min(1180px, calc(100% - 40px));
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.public-header { min-height: 82px; border-bottom: 1px solid #e2e6f0; }
.brand { display: inline-flex; align-items: center; gap: 11px; font-weight: 800; }
.brand-mark {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 11px;
  background: #30366d;
  color: #fff;
  font-size: .78rem;
}
.public-nav,
.public-footer nav { display: flex; align-items: center; flex-wrap: wrap; gap: 18px; }
.public-nav a,
.public-footer a { color: #4f5dbb; font-size: .9rem; font-weight: 700; }
.public-nav a:hover,
.public-footer a:hover { text-decoration: underline; }
.workspace-link {
  padding: 10px 15px;
  border-radius: 10px;
  background: #30366d;
  color: #fff !important;
}
.workspace-link:hover { text-decoration: none !important; background: #252a59; }
.public-main { width: min(1180px, calc(100% - 40px)); margin: 0 auto; padding: 72px 0 88px; }
.public-footer {
  padding: 28px 0 38px;
  border-top: 1px solid #e2e6f0;
  color: #68738a;
  font-size: .86rem;
}
@media (max-width: 680px) {
  .public-header { align-items: flex-start; padding: 20px 0; }
  .public-header,
  .public-footer { flex-direction: column; }
  .public-nav { gap: 12px; }
  .public-main { padding-top: 48px; }
}
</style>
