<template>
  <main class="page-shell">
    <p v-if="demoMode" class="demo-banner">Public read-only demo · synthetic data only · changes are disabled</p>

    <section v-if="loading" class="state-card" role="status" aria-live="polite">
      Loading your match report…
    </section>

    <section v-else-if="pageError" class="state-card error-state" role="alert">
      <h1>Report unavailable</h1>
      <p>{{ pageError }}</p>
      <button class="secondary-button" type="button" @click="backToWorkspace">Back to workspace</button>
    </section>

    <section v-else-if="analysis" class="report-page" aria-labelledby="report-title">
      <header class="topbar">
        <button class="text-button" type="button" @click="backToWorkspace">← Back to workspace</button>
        <div class="account-actions">
          <span v-if="demoMode" class="status">Read-only demo</span>
          <span>{{ demoMode ? 'Synthetic demo user' : currentUser?.email }}</span>
          <button class="text-button" type="button" @click="signOut">Sign out</button>
        </div>
      </header>

      <section class="report-summary">
        <div>
          <p class="eyebrow">MATCH REPORT</p>
          <h1 id="report-title">{{ resume?.title || 'Resume unavailable' }}</h1>
          <p class="subtitle">for {{ jobDescription?.title || 'Job description unavailable' }}</p>
          <time v-if="analysis.createdAt" :datetime="analysis.createdAt">Created {{ formatDate(analysis.createdAt) }}</time>
        </div>
        <div class="summary-status">
          <span class="status" :class="statusClass">{{ formatStatus(analysis.status) }}</span>
          <strong v-if="report" class="score">{{ report.matchScore }}<small>/100</small></strong>
          <p v-else class="muted">{{ analysis.errorMessage || statusMessage }}</p>
        </div>
      </section>

      <section v-if="report" class="metrics" aria-label="Report summary">
        <div><strong>{{ matchedSkills.length }}</strong><span>Matched</span></div>
        <div><strong>{{ partialMatches.length }}</strong><span>Partial</span></div>
        <div><strong>{{ missingSkills.length }}</strong><span>Missing</span></div>
      </section>

      <p v-if="demoMode" class="read-only-note" role="status">This demo is read-only. Plan changes are disabled, and the included interview questions are view-only.</p>
      <div class="tabs" role="tablist" aria-label="Report sections">
        <button id="overview-tab" class="tab-button" :class="{ active: activeTab === 'overview' }" type="button" role="tab" :aria-selected="activeTab === 'overview'" aria-controls="overview-panel" @click="activeTab = 'overview'">Overview</button>
        <button id="plan-tab" class="tab-button" :class="{ active: activeTab === 'plan' }" type="button" role="tab" :aria-selected="activeTab === 'plan'" aria-controls="plan-panel" @click="activeTab = 'plan'">14-day plan</button>
        <button id="interview-tab" class="tab-button" :class="{ active: activeTab === 'interview' }" type="button" role="tab" :aria-selected="activeTab === 'interview'" aria-controls="interview-panel" @click="activeTab = 'interview'">Interview preparation</button>
      </div>

      <section v-if="activeTab === 'overview'" id="overview-panel" class="tab-panel" role="tabpanel" aria-labelledby="overview-tab">
        <p v-if="!report" class="empty-state">{{ statusMessage }}</p>
        <div v-else class="report-grid">
          <ReportList title="Matched skills" :items="matchedSkills" />
          <ReportList title="Partial matches" :items="partialMatches" />
          <ReportList title="Missing skills" :items="missingSkills" />
          <ReportList title="Strengths" :items="report.strengths || []" />
          <ReportList title="Risks" :items="report.risks || []" />
          <ReportList title="Recommendations" :items="report.recommendations || []" />
        </div>
      </section>

      <section v-else-if="activeTab === 'plan'" id="plan-panel" class="tab-panel" role="tabpanel" aria-labelledby="plan-tab">
        <template v-if="!analysis.planId">
          <p class="empty-state">{{ report ? 'A preparation plan is not available for this report yet.' : statusMessage }}</p>
        </template>
        <template v-else>
          <div class="section-heading">
            <div><p class="eyebrow">PREPARATION PLAN</p><h2>{{ plan?.title || 'Your 14-day plan' }}</h2></div>
            <div class="plan-actions">
              <span v-if="plan" class="status" :class="formatStatusClass(plan.status)">{{ formatStatus(plan.status) }}</span>
              <button v-if="!demoMode && (activePlanTasks.length || planTasks.length === 0)" class="secondary-button" type="button" :disabled="regenerating || planLoading" @click="regenerateRemainingTasks">{{ regenerating ? 'Generating…' : activePlanTasks.length ? 'Regenerate remaining tasks' : 'Generate plan tasks' }}</button>
            </div>
          </div>
          <p v-if="planLoading" class="muted" role="status">Loading plan…</p>
          <p v-else-if="planMessage" class="message error" role="alert">{{ planMessage }}</p>
          <template v-else>
            <p v-if="plan?.summary" class="plan-summary">{{ plan.summary }}</p>
            <p v-if="planTasks.length === 0" class="empty-state">No tasks are available for this plan.</p>
            <ol v-else class="task-list">
              <li v-for="task in planTasks" :key="task.id" class="task-card">
                <div class="task-heading"><strong>{{ task.title }}</strong><span class="priority">{{ task.priority }} priority</span></div>
                <p>{{ task.description }}</p>
                <div v-if="!task.archivedAt && !demoMode" class="task-controls">
                  <label>Status<select :value="task.status" :disabled="taskUpdating === task.id" @change="updateTask(task, { status: $event.target.value })"><option value="TODO">To do</option><option value="IN_PROGRESS">In progress</option><option value="COMPLETED">Completed</option><option value="SKIPPED">Skipped</option></select></label>
                  <label>Due date<input type="date" :value="task.dueDate" :disabled="taskUpdating === task.id" @change="updateTask(task, { dueDate: $event.target.value })" /></label>
                </div>
                <p v-else-if="task.archivedAt" class="task-archived">Replaced by a later plan update.</p>
                <p class="task-evidence">Based on: {{ task.sourceEvidence }}</p>
                <time v-if="task.completedAt" :datetime="task.completedAt">Completed {{ formatDate(task.completedAt) }}</time>
              </li>
            </ol>
          </template>
        </template>
      </section>

      <section v-else id="interview-panel" class="tab-panel" role="tabpanel" aria-labelledby="interview-tab">
        <div class="section-heading">
          <div><p class="eyebrow">INTERVIEW PREPARATION</p><h2>Evidence-grounded questions</h2></div>
          <button v-if="!demoMode && canPrepareInterview" class="secondary-button" type="button" :disabled="interviewBusy" @click="createOrLoadInterviewPreparation">{{ interviewBusy ? 'Loading…' : interviewSession ? 'Reload interview preparation' : 'Generate interview preparation' }}</button>
        </div>
        <p v-if="interviewMessage" class="message error" role="alert">{{ interviewMessage }}</p>
        <template v-else-if="interviewSession">
          <p class="interview-summary"><strong>{{ interviewSession.title }}</strong><time :datetime="interviewSession.createdAt">Created {{ formatDate(interviewSession.createdAt) }}</time></p>
          <ol class="interview-question-list">
            <li v-for="question in interviewSession.questions || []" :key="question.id" class="interview-question-card">
              <p class="question-type">{{ formatQuestionType(question.questionType) }}</p>
              <h3>{{ question.questionText }}</h3>
              <p><strong>Assessment goal:</strong> {{ question.assessmentGoal }}</p>
              <p class="task-evidence"><strong>Based on:</strong> {{ question.sourceEvidence }}</p>
              <p><strong>Preparation tip:</strong> {{ question.preparationTip }}</p>
            </li>
          </ol>
        </template>
        <p v-else class="empty-state">{{ canPrepareInterview ? (demoMode ? 'The synthetic interview question set could not be loaded.' : 'Generate an evidence-grounded question set from this completed match report.') : statusMessage }}</p>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ReportList from '../components/ReportList.vue'
import {
  createInterviewPreparation,
  getAnalysis,
  getCurrentUser,
  getJobDescription,
  getPlan,
  getResume,
  listPlanTasks,
  logout,
  regenerateRemainingPlanTasks,
  updatePlanTask
} from '../api'

const route = useRoute()
const router = useRouter()
const clerkAuthEnabled = import.meta.env.VITE_CAREERPILOT_AUTH_ENABLED === 'true'
const demoMode = import.meta.env.VITE_CAREERPILOT_DEMO_MODE === 'true' && !clerkAuthEnabled
const currentUser = ref(null)
const analysis = ref(null)
const resume = ref(null)
const jobDescription = ref(null)
const plan = ref(null)
const planTasks = ref([])
const loading = ref(true)
const planLoading = ref(false)
const pageError = ref(null)
const planMessage = ref(null)
const taskUpdating = ref(null)
const regenerating = ref(false)
const interviewSession = ref(null)
const interviewBusy = ref(false)
const interviewMessage = ref(null)
const activeTab = ref('overview')

const report = computed(() => analysis.value?.report || null)
const matchedSkills = computed(() => report.value?.matchedSkills || [])
const partialMatches = computed(() => report.value?.partialMatches || [])
const missingSkills = computed(() => report.value?.missingSkills || [])
const activePlanTasks = computed(() => planTasks.value.filter(task => !task.archivedAt && ['TODO', 'IN_PROGRESS'].includes(task.status)))
const canPrepareInterview = computed(() => analysis.value?.status === 'COMPLETED' && Boolean(report.value))
const statusClass = computed(() => formatStatusClass(analysis.value?.status))
const statusMessage = computed(() => {
  if (analysis.value?.status === 'FAILED') return analysis.value.errorMessage || 'This analysis could not be completed.'
  if (analysis.value?.status === 'COMPLETED') return 'The completed report is unavailable.'
  return 'This analysis is still in progress. Refresh the workspace shortly for updates.'
})

onMounted(loadReport)

async function loadReport() {
  const analysisId = parseAnalysisId(route.params.analysisId)
  if (!analysisId) {
    loading.value = false
    pageError.value = 'The report address is invalid.'
    return
  }
  try {
    currentUser.value = await getCurrentUser()
    const loadedAnalysis = await getAnalysis(analysisId)
    analysis.value = loadedAnalysis
    const [loadedResume, loadedJobDescription] = await Promise.all([
      getResume(loadedAnalysis.resumeId),
      getJobDescription(loadedAnalysis.jobDescriptionId)
    ])
    resume.value = loadedResume
    jobDescription.value = loadedJobDescription
    if (loadedAnalysis.planId) await loadPlan(loadedAnalysis.planId)
    if (demoMode && loadedAnalysis.status === 'COMPLETED' && loadedAnalysis.report) {
      await createOrLoadInterviewPreparation()
    }
  } catch (error) {
    if (error.response?.status === 401) {
      await router.replace({ name: 'Home' })
      return
    }
    pageError.value = 'This report could not be loaded. Please return to the workspace and try again.'
  } finally {
    loading.value = false
  }
}

async function loadPlan(planId) {
  planLoading.value = true
  planMessage.value = null
  try {
    const [loadedPlan, loadedTasks] = await Promise.all([getPlan(planId), listPlanTasks(planId)])
    plan.value = loadedPlan
    planTasks.value = loadedTasks
  } catch (error) {
    planMessage.value = messageFrom(error)
  } finally {
    planLoading.value = false
  }
}

async function updateTask(task, changes) {
  if (demoMode || !analysis.value?.planId || taskUpdating.value === task.id) return
  taskUpdating.value = task.id
  planMessage.value = null
  try {
    const updated = await updatePlanTask(analysis.value.planId, task.id, changes)
    const index = planTasks.value.findIndex(item => item.id === task.id)
    if (index >= 0) planTasks.value.splice(index, 1, updated)
  } catch (error) {
    planMessage.value = messageFrom(error)
  } finally {
    taskUpdating.value = null
  }
}

async function regenerateRemainingTasks() {
  if (demoMode || !analysis.value?.planId || regenerating.value) return
  const emptyPlan = planTasks.value.length === 0
  if (!window.confirm(emptyPlan ? 'Generate tasks for this plan?' : 'Replace the remaining unfinished tasks? Completed and skipped tasks will be kept.')) return
  regenerating.value = true
  planMessage.value = null
  try {
    planTasks.value = await regenerateRemainingPlanTasks(analysis.value.planId)
  } catch (error) {
    planMessage.value = messageFrom(error)
  } finally {
    regenerating.value = false
  }
}

async function createOrLoadInterviewPreparation() {
  if (!canPrepareInterview.value || interviewBusy.value) return
  interviewBusy.value = true
  interviewMessage.value = null
  try {
    interviewSession.value = await createInterviewPreparation(analysis.value.analysisId)
  } catch (error) {
    interviewMessage.value = messageFrom(error)
  } finally {
    interviewBusy.value = false
  }
}

async function signOut() {
  try {
    await logout()
  } finally {
    await router.replace({ name: 'Home' })
  }
}

function backToWorkspace() {
  router.push({ name: 'Home' })
}

function parseAnalysisId(value) {
  const candidate = Array.isArray(value) ? value[0] : value
  if (!/^[1-9]\d*$/.test(candidate || '')) return null
  const parsed = Number(candidate)
  return Number.isSafeInteger(parsed) ? parsed : null
}

function formatStatus(value) {
  return (value || 'UNKNOWN').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, character => character.toUpperCase())
}

function formatStatusClass(value) {
  return (value || '').toLowerCase()
}

function formatQuestionType(value) {
  return formatStatus(value)
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
.page-shell { min-height: 100vh; padding: 38px clamp(20px, 6vw, 96px); background: #f7f8fc; color: #182033; }
.demo-banner, .read-only-note { max-width: 1100px; margin: 0 auto 20px; padding: 10px 14px; border: 1px solid #cfd5ff; border-radius: 10px; background: #eef0ff; color: #3545a5; font-size: .86rem; font-weight: 750; text-align: center; }
.report-page, .state-card { max-width: 1100px; margin: 0 auto; }.state-card { padding: 34px; border: 1px solid #e3e6f0; border-radius: 16px; background: #fff; color: #64708a; }.error-state { color: #982f31; }.error-state h1 { color: #182033; }.error-state p { margin: 12px 0 20px; }
.topbar, .account-actions, .report-summary, .section-heading, .plan-actions, .task-heading, .task-controls, .interview-summary { display: flex; align-items: center; justify-content: space-between; gap: 16px; }.topbar { min-height: 56px; padding-bottom: 18px; border-bottom: 1px solid #e3e6f0; }.account-actions { color: #52607a; font-size: .9rem; }.eyebrow { margin: 0 0 8px; color: #5967d8; font-size: .73rem; font-weight: 800; letter-spacing: .12em; }.report-summary { align-items: flex-end; padding: 36px 0 24px; }.report-summary h1, h2, h3, p { margin: 0; }.report-summary h1 { max-width: 700px; color: #1c2741; font-size: clamp(2rem, 4vw, 3.2rem); letter-spacing: -.045em; }.subtitle { margin-top: 8px; color: #46516a; font-size: 1.05rem; }.report-summary time { display: block; margin-top: 14px; color: #64708a; font-size: .85rem; }.summary-status { display: grid; gap: 10px; justify-items: end; text-align: right; }.score { color: #4050b6; font-size: clamp(2.5rem, 5vw, 4rem); letter-spacing: -.07em; line-height: 1; }.score small { margin-left: 4px; color: #64708a; font-size: .85rem; letter-spacing: 0; }.muted { color: #64708a; }.metrics { display: grid; grid-template-columns: repeat(3, 1fr); overflow: hidden; border: 1px solid #e3e6f0; border-radius: 14px; background: #fff; }.metrics div { display: grid; gap: 3px; padding: 17px 20px; border-right: 1px solid #e3e6f0; }.metrics div:last-child { border: 0; }.metrics strong { color: #4050b6; font-size: 1.35rem; }.metrics span { color: #64708a; font-size: .8rem; font-weight: 700; text-transform: uppercase; letter-spacing: .08em; }
.tabs { display: flex; gap: 8px; margin-top: 30px; border-bottom: 1px solid #dfe4f0; }.tab-button { border: 0; border-bottom: 3px solid transparent; padding: 12px 14px; background: transparent; color: #64708a; font: inherit; font-weight: 750; }.tab-button.active { border-color: #5967d8; color: #3545a5; }.tab-panel { margin-top: 24px; padding: 24px; border: 1px solid #e3e6f0; border-radius: 16px; background: #fff; }.report-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }.report-grid > :last-child { grid-column: 1 / -1; }.section-heading { align-items: flex-start; }.section-heading h2 { color: #1c2741; font-size: 1.35rem; }.empty-state { padding: 20px 0; color: #7c8598; text-align: center; }.plan-actions { justify-content: flex-end; }.plan-summary { margin: 16px 0; color: #46516a; line-height: 1.5; }.task-list { display: grid; gap: 12px; margin: 18px 0 0; padding: 0; list-style: none; }.task-card, .interview-question-card { padding: 16px; border: 1px solid #e4e8f2; border-radius: 11px; }.task-card > p, .interview-question-card p { margin-top: 9px; color: #46516a; line-height: 1.45; }.task-controls { justify-content: flex-start; margin-top: 14px; }.task-controls label { display: grid; min-width: 160px; gap: 7px; color: #35405a; font-size: .9rem; font-weight: 700; }.task-controls select, .task-controls input { padding: 9px 10px; border: 1px solid #cdd4e4; border-radius: 9px; background: #fff; color: #182033; font: inherit; }.priority { color: #64708a; font-size: .78rem; font-weight: 800; }.task-evidence { font-size: .84rem; }.task-archived { color: #7c8598 !important; font-size: .85rem; font-style: italic; }.task-card time, .interview-summary time { color: #64708a; font-size: .82rem; }.interview-summary { margin: 16px 0; color: #46516a; }.interview-question-list { display: grid; gap: 12px; margin: 18px 0 0; padding-left: 24px; }.interview-question-card h3 { margin: 4px 0 10px; color: #1c2741; font-size: 1rem; }.question-type { color: #5967d8 !important; font-size: .76rem; font-weight: 800; letter-spacing: .06em; text-transform: uppercase; }.status { display: inline-flex; padding: 4px 8px; border-radius: 999px; background: #edf0f7; color: #52607a; font-size: .72rem; font-weight: 800; }.status.completed { color: #137a4a; background: #e4f6ed; }.status.failed { color: #a44917; background: #fff0e8; }.status.running, .status.pending { color: #3c51b5; background: #e9ecff; }.primary-button, .secondary-button, .text-button { border: 0; border-radius: 9px; font: inherit; font-weight: 750; cursor: pointer; }.secondary-button { padding: 10px 14px; background: #e9ecff; color: #4050b6; }.text-button { padding: 4px 0; background: transparent; color: #4d5bc8; text-align: left; }.text-button:hover:not(:disabled) { color: #2739a8; text-decoration: underline; }.message { margin: 16px 0; padding: 10px 12px; border-radius: 9px; font-size: .9rem; }.message.error { color: #982f31; background: #fff0f0; } button:disabled { cursor: not-allowed; opacity: .55; } button:focus-visible, select:focus-visible, input:focus-visible { outline: 3px solid rgba(89, 103, 216, .28); outline-offset: 2px; }
@media (max-width: 760px) { .page-shell { padding: 24px 16px; }.topbar, .account-actions, .report-summary, .section-heading, .plan-actions, .task-heading, .task-controls, .interview-summary { align-items: flex-start; flex-direction: column; }.report-summary { padding-top: 28px; }.summary-status { justify-items: start; text-align: left; }.metrics div { padding: 14px; }.tabs { overflow-x: auto; }.tab-button { flex: 0 0 auto; }.report-grid { grid-template-columns: 1fr; }.report-grid > :last-child { grid-column: auto; }.tab-panel { padding: 18px; }.task-controls label { width: 100%; }.interview-question-list { padding-left: 20px; } }
</style>
