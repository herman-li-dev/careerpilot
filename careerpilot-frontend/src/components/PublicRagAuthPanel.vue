<template>
  <section class="public-rag-auth" aria-labelledby="public-rag-auth-title">
    <div>
      <p class="public-rag-eyebrow">LIVE AI RESUME REVIEW</p>
      <h3 id="public-rag-auth-title">Google sign-in</h3>
      <p class="public-rag-copy">Verify a Google account before using the public Resume Review flow. Upload validation is request-scoped and does not yet call AI or RAG.</p>
      <p class="public-rag-privacy">CareerPilot does not save the uploaded file, filename, or extracted text. This validation step does not send Resume content to an AI provider. A future live review will show a separate provider-processing notice before submission.</p>
    </div>

    <ClerkLoading>
      <p class="public-rag-status" role="status">Loading secure sign-in…</p>
    </ClerkLoading>
    <ClerkLoaded>
      <div v-if="!isSignedIn" class="public-rag-actions">
        <SignInButton mode="modal" :with-sign-up="true">
          <button class="public-rag-button" type="button">Continue with Google</button>
        </SignInButton>
        <p class="public-rag-note">CareerPilot accepts Google sign-in only for this public feature.</p>
      </div>
      <div v-else class="public-rag-session">
        <UserButton />
        <p class="public-rag-status" :class="sessionState" :role="sessionState === 'error' ? 'alert' : 'status'">
          {{ sessionMessage }}
        </p>
        <button v-if="sessionState === 'error'" class="public-rag-retry" type="button" @click="verifySession">
          Retry verification
        </button>
        <form
          v-if="uploadEnabled && sessionState === 'success'"
          class="public-rag-upload"
          @submit.prevent="validateResume"
        >
          <label for="public-rag-resume">Validate a Resume</label>
          <input
            id="public-rag-resume"
            ref="fileInput"
            type="file"
            accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            required
            @change="selectResume"
          />
          <p class="public-rag-note">PDF or DOCX, maximum 5 MiB. The file and extracted text are discarded after this request.</p>
          <p
            v-if="uploadMessage"
            class="public-rag-status"
            :class="uploadState"
            :role="uploadState === 'error' ? 'alert' : 'status'"
          >
            {{ uploadMessage }}
          </p>
          <button class="public-rag-button" type="submit" :disabled="!selectedFile || uploadState === 'loading'">
            {{ uploadState === 'loading' ? 'Validating…' : 'Validate Resume' }}
          </button>
        </form>
      </div>
    </ClerkLoaded>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ClerkLoaded, ClerkLoading, SignInButton, UserButton, useAuth } from '@clerk/vue'
import { validatePublicRagResume, verifyPublicRagSession } from '../api'

const { getToken, isLoaded, isSignedIn } = useAuth()
const uploadEnabled = import.meta.env.VITE_CAREERPILOT_PUBLIC_RAG_UPLOAD_ENABLED === 'true'
const maxFileSize = 5 * 1024 * 1024
const sessionState = ref('idle')
const sessionMessage = ref('Waiting for Google sign-in.')
const selectedFile = ref(null)
const fileInput = ref(null)
const uploadState = ref('idle')
const uploadMessage = ref('')
let verificationVersion = 0

watch([isLoaded, isSignedIn], ([loaded, signedIn]) => {
  verificationVersion += 1
  if (!loaded || !signedIn) {
    sessionState.value = 'idle'
    sessionMessage.value = 'Waiting for Google sign-in.'
    resetUpload()
    return
  }
  verifySession()
}, { immediate: true })

async function verifySession() {
  const currentVersion = ++verificationVersion
  sessionState.value = 'loading'
  sessionMessage.value = 'Verifying your Google session…'
  try {
    const token = await getToken.value()
    if (!token) throw new Error('Clerk did not provide a session token.')
    const result = await verifyPublicRagSession(token)
    if (currentVersion !== verificationVersion) return
    if (!result?.authenticated) throw new Error('The backend did not confirm the session.')
    sessionState.value = 'success'
    sessionMessage.value = uploadEnabled
      ? 'Google account verified. You may validate one Resume without saving it.'
      : 'Google account verified. Resume upload and live review remain disabled.'
  } catch (error) {
    if (currentVersion !== verificationVersion) return
    sessionState.value = 'error'
    sessionMessage.value = verificationFailureMessage(error)
  }
}

// The backend deliberately returns one non-disclosing 401, so the panel reports only the
// transport status. It never echoes the token, the Clerk subject, or any server detail.
function verificationFailureMessage(error) {
  const status = error?.response?.status
  if (status === 401) {
    return 'The backend rejected the Google session token (HTTP 401). The configured Clerk issuer or authorized party does not match this sign-in.'
  }
  if (status === 403 || status === 404) {
    return `The backend did not accept this request path (HTTP ${status}). Confirm the public RAG authentication and upload flags are enabled.`
  }
  if (status >= 500) {
    return `The backend could not verify the session (HTTP ${status}). Check the backend log for the failing step.`
  }
  return error?.message || 'Google session verification failed. Please sign in again or retry.'
}

function selectResume(event) {
  const file = event.target.files?.[0] ?? null
  uploadState.value = 'idle'
  uploadMessage.value = ''
  if (file && file.size > maxFileSize) {
    selectedFile.value = null
    uploadState.value = 'error'
    uploadMessage.value = 'The Resume exceeds the 5 MiB limit.'
    event.target.value = ''
    return
  }
  selectedFile.value = file
}

async function validateResume() {
  if (!selectedFile.value || uploadState.value === 'loading') return
  uploadState.value = 'loading'
  uploadMessage.value = 'Validating the Resume securely…'
  try {
    const token = await getToken.value()
    if (!token) throw new Error('Clerk did not provide a session token.')
    const result = await validatePublicRagResume({ token, file: selectedFile.value })
    if (!result?.accepted) throw new Error('The backend did not accept the Resume.')
    uploadState.value = 'success'
    uploadMessage.value = `${result.documentType} accepted. ${result.extractedCharacterCount.toLocaleString()} characters were extracted and discarded.`
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
  } catch (error) {
    uploadState.value = 'error'
    uploadMessage.value = error?.response?.data?.error?.message
      || 'Resume validation failed. Check the file and try again.'
  }
}

function resetUpload() {
  selectedFile.value = null
  uploadState.value = 'idle'
  uploadMessage.value = ''
  if (fileInput.value) fileInput.value.value = ''
}
</script>

<style scoped>
.public-rag-auth {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, .8fr);
  gap: 28px;
  align-items: center;
  margin: 28px 0;
  padding: 24px;
  border: 1px solid #d9ddf6;
  border-radius: 16px;
  background: #f7f8ff;
}

.public-rag-auth h3 { margin: 4px 0 8px; color: #20233a; }
.public-rag-eyebrow { color: #5964c7; font-size: .75rem; font-weight: 800; letter-spacing: .12em; }
.public-rag-copy, .public-rag-note, .public-rag-status, .public-rag-privacy { color: #5f6474; line-height: 1.55; }
.public-rag-privacy { margin: 12px 0 0; font-size: .86rem; }
.public-rag-note { margin-top: 10px; font-size: .82rem; }
.public-rag-actions { text-align: right; }
.public-rag-session { display: grid; grid-template-columns: auto 1fr; gap: 10px 14px; align-items: center; }
.public-rag-upload { grid-column: 1 / -1; display: grid; gap: 10px; margin-top: 8px; }
.public-rag-upload label { color: #20233a; font-weight: 750; }
.public-rag-upload input { width: 100%; color: #454b60; }
.public-rag-status.success { color: #196b49; }
.public-rag-status.error { color: #a12f3b; }
.public-rag-button, .public-rag-retry {
  border: 0;
  border-radius: 10px;
  font: inherit;
  font-weight: 750;
}
.public-rag-button { padding: 12px 18px; background: #3947b7; color: #fff; }
.public-rag-retry { grid-column: 2; justify-self: start; padding: 8px 12px; background: #e7e9fb; color: #303b9b; }

@media (max-width: 760px) {
  .public-rag-auth { grid-template-columns: 1fr; }
  .public-rag-actions { text-align: left; }
}
</style>
