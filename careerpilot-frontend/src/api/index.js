import axios from 'axios'

const API_BASE_URL = process.env.NODE_ENV === 'production'
 ? '/api'
 : 'http://localhost:8123/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: true
})

const clerkApplicationAuthEnabled = import.meta.env.VITE_CAREERPILOT_AUTH_ENABLED === 'true'

let authTokenProvider = null

export const configureAuthTokenProvider = provider => {
  authTokenProvider = typeof provider === 'function' ? provider : null
}

const currentAuthorizationHeaders = async () => {
  if (!authTokenProvider) return {}
  const token = await authTokenProvider()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

request.interceptors.request.use(async config => {
  Object.assign(config.headers, await currentAuthorizationHeaders())
  return config
})

const data = response => response.data.data

export const getCurrentUser = () => request.get('/users/me').then(data)
export const register = payload => request.post('/auth/register', payload).then(data)
export const login = payload => request.post('/auth/login', payload).then(data)
export const demoLogin = () => request.post('/auth/demo-login').then(data)
export const logout = () => request.post('/auth/logout')
export const verifyPublicRagSession = token => request.get('/rag/session', {
  headers: { Authorization: `Bearer ${token}` }
}).then(data)
export const validatePublicRagResume = ({ token, file }) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/rag/resume/validate', formData, {
    headers: { Authorization: `Bearer ${token}` }
  }).then(data)
}

export const listResumes = () => request.get('/resumes').then(data)
export const getResume = id => request.get(`/resumes/${id}`).then(data)
export const createResume = payload => request.post('/resumes', payload).then(data)
export const uploadResume = ({ file, title }) => {
  const formData = new FormData()
  formData.append('file', file)
  if (title) formData.append('title', title)
  return request.post('/resumes/upload', formData).then(data)
}
export const parseResume = id => request.post(`/resumes/${id}/parse`).then(data)
export const reviewResume = id => clerkApplicationAuthEnabled
  ? request.post('/rag/resume/review', { resumeId: id }).then(data)
  : request.post(`/resumes/${id}/review`).then(data)

export const listJobDescriptions = () => request.get('/job-descriptions').then(data)
export const getJobDescription = id => request.get(`/job-descriptions/${id}`).then(data)
export const createJobDescription = payload => request.post('/job-descriptions', payload).then(data)
export const parseJobDescription = id => request.post(`/job-descriptions/${id}/parse`).then(data)
export const listAnalyses = () => request.get('/analyses').then(data)
export const getAnalysis = id => request.get(`/analyses/${id}`).then(data)
export const createAnalysis = payload => request.post('/analyses', payload).then(data)
export const createInterviewPreparation = analysisId => request.post(`/analyses/${analysisId}/interview-prep`).then(data)
export const getPlan = id => request.get(`/plans/${id}`).then(data)
export const listPlanTasks = id => request.get(`/plans/${id}/tasks`).then(data)
export const updatePlanTask = (planId, taskId, payload) => request.patch(`/plans/${planId}/tasks/${taskId}`, payload).then(data)
export const regenerateRemainingPlanTasks = planId => request.post(`/plans/${planId}/regenerate-remaining`).then(data)

export const connectAnalysisEvents = (analysisId, handlers) => {
  const controller = new AbortController()
  let terminalEventReceived = false

  const connection = {
    close() {
      controller.abort()
    }
  }

  const dispatchBlock = block => {
    let eventName = 'message'
    const dataLines = []
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
    }
    if (!dataLines.length || !handlers[eventName]) return

    let payload
    try {
      payload = JSON.parse(dataLines.join('\n'))
    } catch {
      terminalEventReceived = true
      connection.close()
      handlers.error?.({ message: 'The analysis event stream returned invalid data.' })
      return
    }

    if (eventName === 'done' || eventName === 'error') {
      terminalEventReceived = true
    }
    handlers[eventName]?.(payload)
    if (terminalEventReceived) connection.close()
  }

  ;(async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/analyses/${analysisId}/events`, {
        headers: {
          Accept: 'text/event-stream',
          ...await currentAuthorizationHeaders()
        },
        credentials: 'include',
        signal: controller.signal
      })
      if (!response.ok || !response.body) {
        throw new Error(`The analysis event stream returned HTTP ${response.status}.`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (!terminalEventReceived) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        let boundary = buffer.search(/\r?\n\r?\n/)
        while (boundary >= 0) {
          const separator = buffer.slice(boundary).match(/^\r?\n\r?\n/)?.[0] || '\n\n'
          dispatchBlock(buffer.slice(0, boundary))
          buffer = buffer.slice(boundary + separator.length)
          boundary = buffer.search(/\r?\n\r?\n/)
        }
      }
      if (!terminalEventReceived && !controller.signal.aborted) handlers.closed?.()
    } catch (error) {
      if (!controller.signal.aborted) {
        handlers.error?.({ message: error?.message || 'The analysis event stream could not be opened.' })
      }
    }
  })()

  return connection
}

export default {
  getCurrentUser,
  register,
  login,
  demoLogin,
  logout,
  verifyPublicRagSession,
  validatePublicRagResume,
  listResumes,
  getResume,
  createResume,
  uploadResume,
  parseResume,
  reviewResume,
  listJobDescriptions,
  getJobDescription,
  createJobDescription,
  parseJobDescription,
  listAnalyses,
  getAnalysis,
  createAnalysis,
  createInterviewPreparation,
  getPlan,
  listPlanTasks,
  updatePlanTask,
  regenerateRemainingPlanTasks,
  connectAnalysisEvents
}
