<template>
  <main class="page-shell">
    <p v-if="demoMode" class="demo-banner">Public read-only demo · synthetic data only · changes are disabled</p>
    <header v-if="!currentUser" class="site-header">
      <div>
        <p class="eyebrow">CAREERPILOT</p>
        <h1>Prepare with evidence.</h1>
        <p class="intro">Save your English resume and job descriptions before creating a match report.</p>
      </div>
    </header>

    <section v-if="!currentUser && clerkAuthEnabled" class="auth-card" aria-labelledby="auth-title">
      <p class="eyebrow">SECURE WORKSPACE</p>
      <h2 id="auth-title">Verifying your account</h2>
      <p class="muted">CareerPilot is confirming your Google session with the backend.</p>
      <p v-if="authMessage" class="message" :class="authMessage.type" role="alert">{{ authMessage.text }}</p>
      <button v-if="authMessage" class="secondary-button" type="button" :disabled="authBusy" @click="loadAuthenticatedWorkspace">
        {{ authBusy ? 'Verifying…' : 'Retry verification' }}
      </button>
    </section>

    <section v-else-if="!currentUser" class="auth-card" aria-labelledby="auth-title">
      <p class="eyebrow">GET STARTED</p>
      <template v-if="demoMode">
        <h2 id="auth-title">Explore the synthetic demo</h2>
        <p class="muted">Open a server-owned example workspace. It contains no personal Resume or job-application data.</p>
        <p v-if="authMessage" class="message" :class="authMessage.type" :role="authMessage.type === 'error' ? 'alert' : 'status'" aria-live="polite">{{ authMessage.text }}</p>
        <button class="primary-button demo-login-button" type="button" :disabled="authBusy" @click="openDemo">
          {{ authBusy ? 'Opening…' : 'Open read-only demo' }}
        </button>
      </template>
      <template v-else>
        <h2 id="auth-title">{{ authMode === 'login' ? 'Welcome back' : 'Create your account' }}</h2>
        <p class="muted">{{ authMode === 'login' ? 'Sign in to manage your saved documents.' : 'Use an email and a password with at least 8 characters.' }}</p>
        <form class="auth-form" @submit.prevent="submitAuth">
        <label>
          Email
          <input v-model.trim="authForm.email" type="email" autocomplete="email" required />
        </label>
        <label>
          Password
          <input v-model="authForm.password" type="password" :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'" minlength="8" required />
        </label>
        <p v-if="authMessage" class="message" :class="authMessage.type" :role="authMessage.type === 'error' ? 'alert' : 'status'" aria-live="polite">{{ authMessage.text }}</p>
        <button class="primary-button" type="submit" :disabled="authBusy">
          {{ authBusy ? 'Please wait…' : authMode === 'login' ? 'Sign in' : 'Create account' }}
        </button>
        </form>
        <button class="text-button" type="button" @click="switchAuthMode">
          {{ authMode === 'login' ? 'Need an account? Register' : 'Already have an account? Sign in' }}
        </button>
      </template>
    </section>

    <section v-else class="workspace" aria-labelledby="workspace-title">
      <div class="workspace-topbar">
        <div class="brand-lockup"><span class="brand-mark">CP</span><span>CareerPilot</span></div>
        <div class="account-actions">
          <span v-if="demoMode" class="status">Read-only demo</span>
          <ClerkAccountControls v-if="clerkAuthEnabled" />
          <template v-else>
            <span>{{ demoMode ? 'Synthetic demo user' : currentUser.email }}</span>
            <button class="text-button" type="button" @click="signOut">Sign out</button>
          </template>
        </div>
      </div>

      <div class="workspace-intro">
        <div>
          <p class="eyebrow">CAREER PREPARATION</p>
          <h2 id="workspace-title">Your career preparation workspace</h2>
          <p class="muted">Keep your source documents organized, then turn them into a focused preparation plan.</p>
        </div>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadDocuments">{{ loading ? 'Refreshing…' : 'Refresh' }}</button>
      </div>
      <PublicRagAuthPanel v-if="publicRagPanelEnabled" />
      <p v-if="workspaceMessage" class="message" :class="workspaceMessage.type" :role="workspaceMessage.type === 'error' ? 'alert' : 'status'" aria-live="polite">{{ workspaceMessage.text }}</p>

      <section class="progress-section" aria-label="Preparation steps">
        <article class="step-card" :class="resumeStep.className">
          <span class="step-number">1</span>
          <div><p class="step-label">Resume</p><strong>{{ resumeStep.title }}</strong><p>{{ resumeStep.detail }}</p></div>
        </article>
        <article class="step-card" :class="jobDescriptionStep.className">
          <span class="step-number">2</span>
          <div><p class="step-label">Job description</p><strong>{{ jobDescriptionStep.title }}</strong><p>{{ jobDescriptionStep.detail }}</p></div>
        </article>
        <article class="step-card match-step" :class="canCreateAnalysis ? 'is-ready' : ''">
          <span class="step-number">3</span>
          <div class="step-card-content">
            <p class="step-label">Match report</p>
            <strong>{{ demoMode ? 'Browse example reports' : canCreateAnalysis ? 'Ready to compare' : 'Choose parsed documents' }}</strong>
            <p v-if="demoMode">This synthetic workspace is read-only; report creation is unavailable.</p>
            <div v-else class="match-controls">
              <label>Resume
                <select v-model.number="analysisForm.resumeId">
                  <option :value="null">Choose a parsed resume</option>
                  <option v-for="item in parsedResumes" :key="item.id" :value="item.id">{{ item.title }}</option>
                </select>
              </label>
              <label>Job description
                <select v-model.number="analysisForm.jobDescriptionId">
                  <option :value="null">Choose a parsed job description</option>
                  <option v-for="item in parsedJobDescriptions" :key="item.id" :value="item.id">{{ item.title }}</option>
                </select>
              </label>
              <button class="primary-button" type="button" :disabled="!canCreateAnalysis || analysisBusy" @click="startAnalysis">{{ analysisBusy ? 'Starting…' : 'Create match report' }}</button>
            </div>
          </div>
        </article>
      </section>

      <div class="workspace-grid">
        <section class="overview-panel" aria-labelledby="documents-title">
          <div class="panel-heading">
            <div><p class="eyebrow">DOCUMENT LIBRARY</p><h3 id="documents-title">Saved documents</h3></div>
            <button class="secondary-button" type="button" :aria-expanded="manageDocuments" aria-controls="document-manager" @click="manageDocuments = !manageDocuments">{{ manageDocuments ? 'Hide documents' : demoMode ? 'View documents' : 'Manage documents' }}</button>
          </div>
          <div class="document-summary-list">
            <article class="summary-document">
              <div><p class="document-type">RESUME</p><strong>{{ latestResume?.title || 'No resume saved' }}</strong><time v-if="latestResume" :datetime="latestResume.updatedAt">Updated {{ formatDate(latestResume.updatedAt) }}</time><p v-else>Add a resume to begin.</p></div>
              <span v-if="latestResume" class="status" :class="latestResume.parseStatus.toLowerCase()">{{ formatStatus(latestResume.parseStatus) }}</span>
            </article>
            <article class="summary-document">
              <div><p class="document-type">JOB DESCRIPTION</p><strong>{{ latestJobDescription?.title || 'No job description saved' }}</strong><time v-if="latestJobDescription" :datetime="latestJobDescription.updatedAt">Updated {{ formatDate(latestJobDescription.updatedAt) }}</time><p v-else>Add a job description to compare.</p></div>
              <span v-if="latestJobDescription" class="status" :class="latestJobDescription.parseStatus.toLowerCase()">{{ formatStatus(latestJobDescription.parseStatus) }}</span>
            </article>
          </div>
        </section>

        <section class="overview-panel reports-panel" aria-labelledby="reports-title">
          <div class="panel-heading">
            <div><p class="eyebrow">MATCH HISTORY</p><h3 id="reports-title">Recent reports</h3></div>
            <button class="text-button" type="button" :disabled="analysisBusy" @click="loadAnalyses">Refresh reports</button>
          </div>
          <p v-if="analyses.length === 0" class="empty-state">Your completed match reports will appear here.</p>
          <div v-else class="recent-reports">
            <article v-for="analysis in recentAnalyses" :key="analysis.analysisId" class="report-card">
              <div class="report-card-heading"><div><strong>{{ analysisResumeTitle(analysis) }}</strong><span>for {{ analysisJobDescriptionTitle(analysis) }}</span></div><span class="status" :class="analysis.status.toLowerCase()">{{ formatStatus(analysis.status) }}</span></div>
              <time v-if="analysis.createdAt" :datetime="analysis.createdAt">Created {{ formatDate(analysis.createdAt) }}</time>
              <template v-if="analysis.report">
                <div class="report-metrics"><strong>{{ analysis.report.matchScore }}<small>/100</small></strong><span>{{ matchedCount(analysis) }} matched · {{ partialCount(analysis) }} partial · {{ missingCount(analysis) }} missing</span></div>
                <button class="text-button" type="button" @click="openAnalysis(analysis)">Open report</button>
              </template>
              <p v-else-if="analysis.errorMessage" class="parse-error">{{ analysis.errorMessage }}</p>
              <p v-else class="muted">Report is being prepared.</p>
            </article>
          </div>
        </section>
      </div>

      <section v-if="manageDocuments" id="document-manager" class="document-manager" aria-labelledby="document-manager-title">
        <div class="panel-heading"><div><p class="eyebrow">DOCUMENTS</p><h3 id="document-manager-title">{{ demoMode ? 'View demo documents' : 'Manage your documents' }}</h3></div><p v-if="demoMode" class="muted">Open the synthetic Resume to view its evidence and run a non-persistent review.</p></div>
        <div class="document-columns">
          <DocumentColumn
            title="Resumes"
            :description="demoMode ? 'Browse the synthetic Resume used by this example.' : 'Paste or upload the English resume version you want to compare.'"
            item-name="Resume"
            :items="resumes"
            :busy-id="parsing.resume"
            :read-only="demoMode"
            :create-document="payload => createDocument('resume', payload)"
            :upload-document="uploadResumeDocument"
            @open="openDocument('Resume', $event)"
            @parse="parseDocument('resume', $event)"
          />
          <DocumentColumn
            title="Job descriptions"
            :description="demoMode ? 'Browse the synthetic role description used by this example.' : 'Paste one English role description at a time.'"
            item-name="Job description"
            :items="jobDescriptions"
            :busy-id="parsing.jobDescription"
            :read-only="demoMode"
            :create-document="payload => createDocument('jobDescription', payload)"
            @open="openDocument('Job description', $event)"
            @parse="parseDocument('jobDescription', $event)"
          />
        </div>
      </section>
    </section>

    <div v-if="selectedDocument" class="dialog-backdrop" @click.self="closeDocument">
      <section class="document-dialog" role="dialog" aria-modal="true" :aria-label="selectedDocument.kind + ' details'">
        <div class="dialog-header">
          <div>
            <p class="eyebrow">{{ selectedDocument.kind }}</p>
            <h2>{{ selectedDocument.item.title }}</h2>
          </div>
          <button class="text-button" type="button" @click="closeDocument">Close</button>
        </div>
        <div class="status-row">
          <span class="status" :class="selectedDocument.item.parseStatus.toLowerCase()">{{ formatStatus(selectedDocument.item.parseStatus) }}</span>
          <time :datetime="selectedDocument.item.updatedAt">Updated {{ formatDate(selectedDocument.item.updatedAt) }}</time>
        </div>
        <p v-if="selectedDocument.item.parseError" class="message error" role="alert">{{ selectedDocument.item.parseError }}</p>
        <pre>{{ selectedDocument.item.rawText }}</pre>
        <section v-if="selectedDocument.kind === 'Resume' && selectedDocument.item.parseStatus === 'COMPLETED'" class="resume-review-section" aria-labelledby="resume-review-title">
          <div class="plan-heading">
            <div><p class="eyebrow">RESUME REVIEW</p><h3 id="resume-review-title">Truthful clarity suggestions</h3></div>
            <button class="secondary-button" type="button" :disabled="resumeReviewBusy || (clerkAuthEnabled && !resumeReviewConsent)" @click="reviewSelectedResume">{{ resumeReviewBusy ? 'Reviewing…' : 'Review resume' }}</button>
          </div>
          <p class="review-boundary">Uses public synthetic guidance. Recoverable vector or model failures use a lexical or deterministic fallback. Suggestions are not saved and do not modify your Resume.</p>
          <label v-if="clerkAuthEnabled" class="review-consent">
            <input v-model="resumeReviewConsent" type="checkbox" />
            I understand that bounded Resume evidence will be sent to the configured AI provider for processing. CareerPilot does not persist the prompt or review; provider handling follows its own terms.
          </label>
          <p v-if="resumeReviewMessage" class="message error" role="alert">{{ resumeReviewMessage }}</p>
          <template v-else-if="resumeReview">
            <p class="review-boundary" role="status">{{ reviewRetrievalDescription(resumeReview) }}</p>
            <p v-if="resumeReview.suggestions.length === 0" class="empty-state">No suggestions apply to this parsed Resume.</p>
            <ol v-else class="resume-review-list">
              <li v-for="suggestion in resumeReview.suggestions" :key="`${suggestion.category}-${suggestion.sourceId}-${suggestion.resumeEvidence}`" class="resume-review-card">
                <div class="task-heading"><span class="question-type">{{ formatStatus(suggestion.category) }}</span><span class="priority">{{ formatStatus(suggestion.priority) }} priority</span></div>
                <p><strong>Finding:</strong> {{ suggestion.finding }}</p>
                <p class="task-evidence"><strong>Resume evidence:</strong> {{ suggestion.resumeEvidence }}</p>
                <p><strong>Recommendation:</strong> {{ suggestion.recommendation }}</p>
                <template v-if="suggestion.citation">
                  <p class="review-source"><strong>Source:</strong> {{ citationTitle(suggestion.citation) }}<span v-if="suggestion.citation.sourceVersion"> · Version {{ suggestion.citation.sourceVersion }}</span></p>
                  <p class="review-source"><strong>Location:</strong> {{ citationLocation(suggestion.citation) }}</p>
                  <blockquote v-if="suggestion.citation.excerpt" class="review-excerpt"><strong>Excerpt:</strong> {{ suggestion.citation.excerpt }}</blockquote>
                </template>
                <p v-else class="review-source"><strong>Source:</strong> {{ suggestion.sourceTitle }} ({{ suggestion.sourceId }})</p>
              </li>
            </ol>
          </template>
        </section>
      </section>
    </div>

  </main>
</template>

<script setup>
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import ClerkAccountControls from '../components/ClerkAccountControls.vue'
import DocumentColumn from '../components/DocumentColumn.vue'
import {
  createJobDescription,
  createResume,
  demoLogin,
  uploadResume,
  getCurrentUser,
  listJobDescriptions,
  listResumes,
  login,
  logout,
  parseJobDescription,
  parseResume,
  reviewResume,
  createAnalysis,
  listAnalyses,
  connectAnalysisEvents,
  register
} from '../api'

const authMode = ref('login')
const router = useRouter()
const clerkAuthEnabled = import.meta.env.VITE_CAREERPILOT_AUTH_ENABLED === 'true'
const publicRagPanelEnabled = import.meta.env.VITE_CAREERPILOT_PUBLIC_RAG_AUTH_ENABLED === 'true'
  && !clerkAuthEnabled
const PublicRagAuthPanel = publicRagPanelEnabled
  ? defineAsyncComponent(() => import('../components/PublicRagAuthPanel.vue'))
  : null
const demoMode = import.meta.env.VITE_CAREERPILOT_DEMO_MODE === 'true' && !clerkAuthEnabled
const authForm = reactive({ email: '', password: '' })
const authBusy = ref(false)
const authMessage = ref(null)
const currentUser = ref(null)
const resumes = ref([])
const jobDescriptions = ref([])
const loading = ref(false)
const workspaceMessage = ref(null)
const selectedDocument = ref(null)
const resumeReview = ref(null)
const resumeReviewBusy = ref(false)
const resumeReviewMessage = ref(null)
const resumeReviewConsent = ref(false)
const parsing = reactive({ resume: null, jobDescription: null })
const analyses = ref([])
const analysisForm = reactive({ resumeId: null, jobDescriptionId: null })
const analysisBusy = ref(false)
const manageDocuments = ref(false)
let resumeReviewRequestVersion = 0
let analysisEventSource = null
const parsedResumes = computed(() => resumes.value.filter(item => item.parseStatus === 'COMPLETED'))
const parsedJobDescriptions = computed(() => jobDescriptions.value.filter(item => item.parseStatus === 'COMPLETED'))
const canCreateAnalysis = computed(() => analysisForm.resumeId && analysisForm.jobDescriptionId)
const latestResume = computed(() => mostRecent(resumes.value))
const latestJobDescription = computed(() => mostRecent(jobDescriptions.value))
const recentAnalyses = computed(() => [...analyses.value].sort((left, right) => new Date(right.createdAt || 0) - new Date(left.createdAt || 0)).slice(0, 4))
const resumeStep = computed(() => stepStatus('resume', resumes.value, parsedResumes.value))
const jobDescriptionStep = computed(() => stepStatus('job description', jobDescriptions.value, parsedJobDescriptions.value))

const createMethods = {
  resume: createResume,
  jobDescription: createJobDescription
}
const parseMethods = {
  resume: parseResume,
  jobDescription: parseJobDescription
}

onMounted(loadAuthenticatedWorkspace)

async function loadAuthenticatedWorkspace() {
  authBusy.value = true
  authMessage.value = null
  try {
    currentUser.value = await getCurrentUser()
    await Promise.all([loadDocuments(), loadAnalyses()])
  } catch (error) {
    currentUser.value = null
    if (clerkAuthEnabled) {
      authMessage.value = {
        type: 'error',
        text: error.response?.status === 401
          ? 'Google session verification failed. Please sign out and sign in again, or retry.'
          : messageFrom(error)
      }
    } else if (error.response?.status !== 401) {
      authMessage.value = { type: 'error', text: messageFrom(error) }
    }
  } finally {
    authBusy.value = false
  }
}

async function submitAuth() {
  authBusy.value = true
  authMessage.value = null
  try {
    if (authMode.value === 'register') {
      await register({ ...authForm })
      authMode.value = 'login'
      authForm.password = ''
      authMessage.value = { type: 'success', text: 'Account created. Please sign in.' }
      return
    }
    currentUser.value = await login({ ...authForm })
    authForm.password = ''
    await Promise.all([loadDocuments(), loadAnalyses()])
  } catch (error) {
    authMessage.value = { type: 'error', text: messageFrom(error) }
  } finally {
    authBusy.value = false
  }
}

async function openDemo() {
  authBusy.value = true
  authMessage.value = null
  try {
    currentUser.value = await demoLogin()
    await Promise.all([loadDocuments(), loadAnalyses()])
  } catch (error) {
    authMessage.value = { type: 'error', text: messageFrom(error) }
  } finally {
    authBusy.value = false
  }
}

function switchAuthMode() {
  authMode.value = authMode.value === 'login' ? 'register' : 'login'
  authMessage.value = null
  authForm.password = ''
}

async function signOut() {
  try {
    await logout()
  } finally {
    currentUser.value = null
    resumes.value = []
    jobDescriptions.value = []
    closeDocument()
    analyses.value = []
    manageDocuments.value = false
    analysisEventSource?.close()
    authMode.value = 'login'
    authForm.password = ''
  }
}

async function loadAnalyses() {
  try {
    analyses.value = await listAnalyses()
  } catch (error) {
    workspaceMessage.value = { type: 'error', text: messageFrom(error) }
  }
}

function openDocument(kind, item) {
  clearResumeReview()
  selectedDocument.value = { kind, item }
}

function closeDocument() {
  clearResumeReview()
  selectedDocument.value = null
}

function clearResumeReview() {
  resumeReviewRequestVersion += 1
  resumeReview.value = null
  resumeReviewBusy.value = false
  resumeReviewMessage.value = null
  resumeReviewConsent.value = false
}

function isCurrentResumeReviewRequest(requestVersion, resumeId) {
  return resumeReviewRequestVersion === requestVersion
    && selectedDocument.value?.kind === 'Resume'
    && selectedDocument.value.item.id === resumeId
}

async function reviewSelectedResume() {
  const resume = selectedDocument.value?.kind === 'Resume' ? selectedDocument.value.item : null
  const resumeId = resume?.id
  if (!resumeId || resume.parseStatus !== 'COMPLETED' || resumeReviewBusy.value
    || (clerkAuthEnabled && !resumeReviewConsent.value)) return
  const requestVersion = ++resumeReviewRequestVersion
  resumeReviewBusy.value = true
  resumeReviewMessage.value = null
  try {
    const review = await reviewResume(resumeId)
    if (isCurrentResumeReviewRequest(requestVersion, resumeId)) {
      resumeReview.value = review
    }
  } catch (error) {
    if (isCurrentResumeReviewRequest(requestVersion, resumeId)) {
      resumeReviewMessage.value = messageFrom(error)
    }
  } finally {
    if (isCurrentResumeReviewRequest(requestVersion, resumeId)) {
      resumeReviewBusy.value = false
    }
  }
}

async function startAnalysis() {
  if (!canCreateAnalysis.value || analysisBusy.value) return
  analysisBusy.value = true
  workspaceMessage.value = null
  try {
    const created = await createAnalysis({ ...analysisForm })
    connectToAnalysis(created.analysisId)
  } catch (error) {
    workspaceMessage.value = { type: 'error', text: messageFrom(error) }
  } finally {
    analysisBusy.value = false
  }
}

function connectToAnalysis(analysisId) {
  analysisEventSource?.close()
  analysisEventSource = connectAnalysisEvents(analysisId, {
    progress: () => loadAnalyses(),
    report: async () => {
      await loadAnalyses()
      const completed = analyses.value.find(item => item.analysisId === analysisId)
      if (completed?.report) openAnalysis(completed)
    },
    done: () => { analysisEventSource?.close(); loadAnalyses() },
    error: event => { workspaceMessage.value = { type: 'error', text: event.message || 'The analysis failed.' }; loadAnalyses() },
    closed: () => loadAnalyses()
  })
}

function openAnalysis(analysis) {
  router.push({ name: 'AnalysisReport', params: { analysisId: analysis.analysisId } })
}

async function loadDocuments() {
  loading.value = true
  workspaceMessage.value = null
  try {
    const [loadedResumes, loadedJobDescriptions] = await Promise.all([listResumes(), listJobDescriptions()])
    resumes.value = loadedResumes
    jobDescriptions.value = loadedJobDescriptions
    selectAvailableDocuments()
  } catch (error) {
    workspaceMessage.value = { type: 'error', text: messageFrom(error) }
  } finally {
    loading.value = false
  }
}

async function createDocument(kind, payload) {
  workspaceMessage.value = null
  try {
    const created = await createMethods[kind](payload)
    const target = kind === 'resume' ? resumes : jobDescriptions
    target.value.unshift(created)
    workspaceMessage.value = { type: 'success', text: `${kind === 'resume' ? 'Resume' : 'Job description'} saved.` }
  } catch (error) {
    workspaceMessage.value = { type: 'error', text: messageFrom(error) }
    throw error
  }
}

async function uploadResumeDocument(payload) {
  workspaceMessage.value = null
  try {
    const created = await uploadResume(payload)
    resumes.value.unshift(created)
    workspaceMessage.value = { type: 'success', text: 'Resume uploaded and saved as extracted text.' }
  } catch (error) {
    workspaceMessage.value = { type: 'error', text: messageFrom(error) }
    throw error
  }
}

async function parseDocument(kind, item) {
  parsing[kind] = item.id
  workspaceMessage.value = null
  try {
    const updated = await parseMethods[kind](item.id)
    const target = kind === 'resume' ? resumes : jobDescriptions
    const index = target.value.findIndex(entry => entry.id === updated.id)
    target.value.splice(index, 1, updated)
    if (selectedDocument.value?.item.id === updated.id) {
      selectedDocument.value.item = updated
    }
    selectAvailableDocuments()
  } catch (error) {
    workspaceMessage.value = { type: 'error', text: messageFrom(error) }
  } finally {
    parsing[kind] = null
  }
}

function formatStatus(status) {
  return status.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, character => character.toUpperCase())
}

function reviewRetrievalDescription(review) {
  if (review.reviewType === 'MODEL_ASSISTED_SYNTHETIC_VECTOR_RAG') {
    return 'Vector RAG retrieval selected these suggestions.'
  }
  if (review.reviewType === 'MODEL_ASSISTED_SYNTHETIC_LEXICAL_RULES') {
    return 'Model-assisted lexical retrieval selected these suggestions.'
  }
  if (review.reviewType === 'SYNTHETIC_LEXICAL_RULES_FALLBACK') {
    return 'Deterministic lexical fallback selected these suggestions.'
  }
  return review.suggestions.length === 0
    ? 'Deterministic lexical retrieval found no applicable guidance.'
    : 'Deterministic lexical retrieval selected these suggestions.'
}

function citationTitle(citation) {
  return citation.sourceTitle || citation.sourceId
}

function citationLocation(citation) {
  const location = []
  if (citation.section) location.push(`Section: ${citation.section}`)
  if (citation.page !== null && citation.page !== undefined) {
    location.push(`Page ${citation.page}`)
  } else if (citation.chunkIndex !== null && citation.chunkIndex !== undefined) {
    location.push(`Chunk ${citation.chunkIndex}`)
  }
  return location.join(' · ')
}

function mostRecent(items) {
  return [...items].sort((left, right) => new Date(right.updatedAt || right.createdAt || 0) - new Date(left.updatedAt || left.createdAt || 0))[0]
}

function stepStatus(name, items, parsedItems) {
  if (parsedItems.length) {
    return { className: 'is-complete', title: `${parsedItems.length} parsed`, detail: `${parsedItems.length === 1 ? 'One document is' : 'Documents are'} ready for matching.` }
  }
  if (items.length) {
    return { className: 'is-attention', title: 'Ready to parse', detail: `Parse a saved ${name} to use it in a report.` }
  }
  return { className: 'is-pending', title: 'Not added yet', detail: `Add a ${name} to get started.` }
}

function selectAvailableDocuments() {
  if (!parsedResumes.value.some(item => item.id === analysisForm.resumeId)) {
    analysisForm.resumeId = parsedResumes.value[0]?.id || null
  }
  if (!parsedJobDescriptions.value.some(item => item.id === analysisForm.jobDescriptionId)) {
    analysisForm.jobDescriptionId = parsedJobDescriptions.value[0]?.id || null
  }
}

function analysisResumeTitle(analysis) {
  return resumes.value.find(item => item.id === analysis.resumeId)?.title || 'Resume unavailable'
}

function analysisJobDescriptionTitle(analysis) {
  return jobDescriptions.value.find(item => item.id === analysis.jobDescriptionId)?.title || 'Job description unavailable'
}

function matchedCount(analysis) {
  return analysis.report?.matchedSkills?.length || 0
}

function partialCount(analysis) {
  return analysis.report?.partialMatches?.length || 0
}

function missingCount(analysis) {
  return analysis.report?.missingSkills?.length || 0
}

function formatDate(value) {
  return new Intl.DateTimeFormat('en-CA', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function messageFrom(error) {
  const fieldErrors = error.response?.data?.error?.fieldErrors
  const fieldMessage = fieldErrors && Object.values(fieldErrors)
    .find(message => typeof message === 'string' && message.trim())
  if (fieldMessage) return fieldMessage

  const responseMessage = error.response?.data?.error?.message
  if (typeof responseMessage === 'string' && responseMessage.trim()) return responseMessage
  if (!error.response) return 'CareerPilot could not reach the API. Check that the backend is running.'
  return 'Something went wrong. Please try again.'
}

</script>

<style scoped>
.page-shell { min-height: 100vh; padding: 48px clamp(20px, 6vw, 96px); background: #f7f8fc; color: #182033; }
.demo-banner { max-width: 1220px; margin: -24px auto 28px; padding: 10px 14px; border: 1px solid #cfd5ff; border-radius: 10px; background: #eef0ff; color: #3545a5; font-size: .86rem; font-weight: 750; text-align: center; }
.site-header, .workspace-heading, .dialog-header, .status-row, .account-actions, .card-footer { display: flex; justify-content: space-between; gap: 20px; align-items: center; }
.site-header { max-width: 1220px; margin: 0 auto 48px; align-items: flex-start; }
.eyebrow { color: #5967d8; font-size: .75rem; font-weight: 800; letter-spacing: .12em; margin-bottom: 10px; }
h1, h2, h3, p { margin: 0; } h1 { font-size: clamp(2.25rem, 5vw, 4rem); letter-spacing: -.045em; } h2 { font-size: 1.55rem; } h3 { font-size: 1.2rem; }
.intro, .muted, .column-heading p, .document-open span, time { color: #64708a; } .intro { max-width: 630px; margin-top: 12px; font-size: 1.06rem; }
.auth-card { max-width: 460px; margin: 8vh auto; padding: 34px; background: white; border: 1px solid #e3e6f0; border-radius: 18px; box-shadow: 0 16px 40px rgba(31, 45, 88, .08); }
.auth-card .muted { margin-top: 10px; }.auth-form, .document-form { display: grid; gap: 16px; margin: 26px 0 18px; }
.demo-login-button { width: 100%; margin-top: 24px; }
label { display: grid; gap: 7px; color: #35405a; font-size: .9rem; font-weight: 700; } input, textarea { width: 100%; padding: 11px 12px; border: 1px solid #cdd4e4; border-radius: 9px; background: #fff; color: #182033; font: inherit; } textarea { resize: vertical; line-height: 1.5; } input:focus, textarea:focus { outline: 3px solid rgba(89, 103, 216, .18); border-color: #5967d8; }
.workspace { max-width: 1220px; margin: 0 auto; }.workspace-heading { margin-bottom: 24px; }.document-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24px; }.analysis-panel { margin-top: 28px; padding: 24px; background: #fff; border: 1px solid #e3e6f0; border-radius: 16px; }.analysis-form { display: grid; gap: 14px; grid-template-columns: repeat(3, minmax(0, 1fr)); align-items: end; }.analysis-form select { padding: 11px 12px; border: 1px solid #cdd4e4; border-radius: 9px; font: inherit; }.analysis-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 12px; margin-top: 18px; }.analysis-card { padding: 14px; border: 1px solid #e4e8f2; border-radius: 11px; }.document-column { padding: 24px; background: #fff; border: 1px solid #e3e6f0; border-radius: 16px; }.column-heading { min-height: 68px; }.document-list { display: grid; gap: 12px; }.empty-state { padding: 20px 0; color: #7c8598; text-align: center; }
.resume-review-section { margin-top: 26px; padding-top: 24px; border-top: 1px solid #e3e6f0; }.review-boundary { margin: 14px 0; color: #64708a; font-size: .9rem; line-height: 1.5; }.review-consent { display: flex; gap: 9px; align-items: flex-start; margin: 14px 0; color: #46516a; font-size: .88rem; line-height: 1.5; }.review-consent input { margin-top: 4px; }.resume-review-list { display: grid; gap: 12px; margin: 18px 0 0; padding-left: 24px; }.resume-review-card { padding: 16px; border: 1px solid #e4e8f2; border-radius: 11px; }.resume-review-card > p { margin-top: 9px; color: #46516a; line-height: 1.45; }.review-source { color: #64708a !important; font-size: .84rem; }.review-excerpt { margin: 10px 0 0; padding: 9px 12px; border-left: 3px solid #cfd5ff; color: #46516a; font-size: .88rem; line-height: 1.45; }
.document-card { border: 1px solid #e4e8f2; border-radius: 11px; padding: 14px; }.document-open { width: 100%; display: grid; gap: 5px; border: 0; padding: 0; background: transparent; color: #1c2741; text-align: left; }.document-open:hover strong { color: #5967d8; }.document-open span { font-size: .82rem; }.card-footer { margin-top: 13px; }.status { display: inline-flex; padding: 4px 8px; border-radius: 999px; background: #edf0f7; color: #52607a; font-size: .72rem; font-weight: 800; }.status.completed { color: #137a4a; background: #e4f6ed; }.status.failed { color: #a44917; background: #fff0e8; }.status.running { color: #3c51b5; background: #e9ecff; }.parse-error { margin-top: 10px; color: #a44917; font-size: .82rem; }
.primary-button, .secondary-button, .text-button { border: 0; border-radius: 9px; font: inherit; font-weight: 750; }.primary-button { padding: 11px 15px; background: #5967d8; color: white; }.primary-button:hover:not(:disabled) { background: #4655c9; }.secondary-button { padding: 10px 14px; background: #e9ecff; color: #4050b6; }.text-button { padding: 4px 0; background: transparent; color: #4d5bc8; text-align: left; }.text-button:hover:not(:disabled) { color: #2739a8; text-decoration: underline; } button:disabled { cursor: not-allowed; opacity: .55; }
.message { margin: 16px 0; padding: 10px 12px; border-radius: 9px; font-size: .9rem; }.message.error { color: #982f31; background: #fff0f0; }.message.success { color: #1d7748; background: #e9f8ef; }.dialog-backdrop { position: fixed; inset: 0; z-index: 10; display: grid; place-items: center; padding: 20px; background: rgba(24, 32, 51, .42); }.document-dialog { width: min(760px, 100%); max-height: 85vh; overflow: auto; padding: 26px; background: #fff; border-radius: 16px; }.status-row { margin: 20px 0; } pre { overflow: auto; padding: 16px; border-radius: 10px; background: #f5f7fb; color: #24304d; font: .9rem/1.6 ui-monospace, SFMono-Regular, Menlo, monospace; white-space: pre-wrap; }
.workspace { max-width: 1220px; }.workspace-topbar { display: flex; align-items: center; justify-content: space-between; gap: 24px; min-height: 56px; padding: 0 0 18px; border-bottom: 1px solid #e3e6f0; }.brand-lockup { display: flex; align-items: center; gap: 9px; color: #24304d; font-weight: 800; }.brand-mark { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 9px; background: #5967d8; color: #fff; font-size: .72rem; letter-spacing: -.04em; }.workspace-intro { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin: 38px 0 26px; }.workspace-intro h2 { font-size: clamp(1.8rem, 3vw, 2.45rem); letter-spacing: -.035em; }.workspace-intro .muted { margin-top: 9px; }.progress-section { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-bottom: 24px; }.step-card { display: flex; gap: 13px; min-height: 150px; padding: 20px; border: 1px solid #e3e6f0; border-radius: 16px; background: #fff; box-shadow: 0 8px 24px rgba(31, 45, 88, .035); }.step-card.is-ready { border-color: #aeb7f6; background: #f9faff; }.step-card.is-complete .step-number { background: #e4f6ed; color: #137a4a; }.step-card.is-attention .step-number { background: #fff0e8; color: #a44917; }.step-number { display: grid; flex: 0 0 28px; height: 28px; place-items: center; border-radius: 50%; background: #edf0f7; color: #52607a; font-size: .82rem; font-weight: 800; }.step-label, .document-type { margin-bottom: 6px; color: #5967d8; font-size: .69rem; font-weight: 800; letter-spacing: .1em; }.step-card strong { display: block; color: #1c2741; font-size: 1rem; }.step-card p:not(.step-label) { margin-top: 7px; color: #64708a; font-size: .87rem; line-height: 1.45; }.step-card-content { min-width: 0; width: 100%; }.match-controls { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto; gap: 9px; align-items: end; margin-top: 12px; }.match-controls label { gap: 5px; font-size: .76rem; }.match-controls select { min-width: 0; padding: 9px 10px; border: 1px solid #cdd4e4; border-radius: 8px; background: #fff; color: #182033; font: inherit; }.match-controls .primary-button { padding: 10px 12px; white-space: nowrap; font-size: .83rem; }.workspace-grid { display: grid; grid-template-columns: minmax(0, .94fr) minmax(0, 1.4fr); gap: 24px; align-items: start; }.overview-panel, .document-manager { padding: 24px; border: 1px solid #e3e6f0; border-radius: 16px; background: #fff; }.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }.panel-heading .eyebrow { margin-bottom: 7px; }.panel-heading h3 { font-size: 1.18rem; }.document-summary-list { display: grid; gap: 12px; }.summary-document { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 15px; border: 1px solid #e8ebf3; border-radius: 11px; }.summary-document strong { display: block; color: #1c2741; line-height: 1.35; }.summary-document time, .summary-document p:not(.document-type) { display: block; margin-top: 6px; font-size: .8rem; }.recent-reports { display: grid; gap: 10px; }.report-card { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px 16px; padding: 15px; border: 1px solid #e8ebf3; border-radius: 11px; }.report-card-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; min-width: 0; }.report-card-heading strong, .report-card-heading span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.report-card-heading strong { color: #1c2741; }.report-card-heading > div > span { margin-top: 4px; color: #64708a; font-size: .82rem; }.report-card time { grid-column: 1 / -1; font-size: .78rem; }.report-metrics { display: flex; align-items: baseline; gap: 8px; color: #64708a; font-size: .8rem; }.report-metrics > strong { color: #4050b6; font-size: 1.45rem; letter-spacing: -.04em; }.report-metrics small { margin-left: 2px; font-size: .7rem; letter-spacing: 0; }.report-card .text-button { align-self: end; justify-self: end; }.report-card .muted, .report-card .parse-error { grid-column: 1 / -1; margin: 0; font-size: .82rem; }.document-manager { margin-top: 24px; }.document-manager .panel-heading { align-items: center; }.document-manager .panel-heading .muted { font-size: .85rem; }.document-manager .document-columns { margin-top: 4px; } button, select, input, textarea { transition: border-color .15s ease, box-shadow .15s ease, background-color .15s ease; } button:focus-visible, select:focus-visible, input:focus-visible, textarea:focus-visible { outline: 3px solid rgba(89, 103, 216, .28); outline-offset: 2px; }
@media (max-width: 900px) { .progress-section, .workspace-grid { grid-template-columns: 1fr; }.match-controls { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }.match-controls .primary-button { grid-column: 1 / -1; width: 100%; }.reports-panel { order: 2; } }
@media (max-width: 760px) { .page-shell { padding: 28px 16px; }.site-header, .workspace-heading, .status-row, .task-heading, .workspace-topbar, .workspace-intro, .panel-heading { align-items: flex-start; flex-direction: column; }.workspace-topbar { gap: 14px; }.workspace-intro { margin-top: 28px; }.workspace-intro .secondary-button, .panel-heading .secondary-button { width: 100%; }.progress-section { gap: 12px; }.step-card { min-height: auto; padding: 17px; }.match-controls, .document-columns, .analysis-form { grid-template-columns: 1fr; }.match-controls .primary-button { grid-column: auto; }.account-actions { align-items: flex-start; flex-direction: column; }.overview-panel, .document-manager { padding: 18px; }.report-card { grid-template-columns: 1fr; }.report-card time { grid-column: auto; }.report-card .text-button { justify-self: start; }.report-card-heading { width: 100%; }.summary-document { padding: 13px; } }
</style>
