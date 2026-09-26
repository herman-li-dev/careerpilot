<template>
  <ClerkLoading>
    <main class="auth-shell" aria-live="polite">
      <section class="auth-status">Loading secure sign-in…</section>
    </main>
  </ClerkLoading>
  <ClerkLoaded>
    <main v-if="!isSignedIn" class="auth-shell">
      <section class="auth-intro">
        <p class="eyebrow">CAREERPILOT</p>
        <h1>Prepare with evidence.</h1>
        <p>Sign in with Google to access your private career preparation workspace.</p>
      </section>
      <SignIn
        routing="hash"
        oauth-flow="redirect"
        :with-sign-up="true"
        sign-in-fallback-redirect-url="/app"
        sign-up-fallback-redirect-url="/app"
      />
      <nav class="auth-legal" aria-label="Public information">
        <RouterLink to="/">About CareerPilot</RouterLink>
        <RouterLink to="/privacy">Privacy</RouterLink>
        <RouterLink to="/terms">Terms</RouterLink>
      </nav>
    </main>
    <router-view v-else />
  </ClerkLoaded>
</template>

<script setup>
import { ClerkLoaded, ClerkLoading, SignIn, useAuth } from '@clerk/vue'
import { configureAuthTokenProvider } from '../api'

const { getToken, isSignedIn } = useAuth()

configureAuthTokenProvider(async () => {
  if (!isSignedIn.value) return null
  return getToken.value()
})
</script>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 460px);
  align-items: center;
  gap: clamp(36px, 8vw, 120px);
  padding: clamp(28px, 7vw, 96px);
  background: #f7f8fc;
  color: #182033;
}
.auth-intro { max-width: 680px; }
.eyebrow { margin-bottom: 14px; color: #5967d8; font-size: .78rem; font-weight: 800; letter-spacing: .14em; }
h1 { font-size: clamp(2.7rem, 7vw, 5.5rem); line-height: .98; letter-spacing: -.055em; }
p:not(.eyebrow) { max-width: 580px; margin-top: 22px; color: #64708a; font-size: 1.08rem; line-height: 1.6; }
.auth-status { grid-column: 1 / -1; color: #64708a; text-align: center; }
.auth-legal {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 18px;
  color: #5967d8;
  font-size: .86rem;
  font-weight: 700;
}
.auth-legal a:hover { text-decoration: underline; }
@media (max-width: 860px) {
  .auth-shell { grid-template-columns: 1fr; align-content: center; }
}
</style>
