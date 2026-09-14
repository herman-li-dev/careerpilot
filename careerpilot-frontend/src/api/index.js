import axios from 'axios'

const API_BASE_URL = process.env.NODE_ENV === 'production'
 ? '/api'
 : 'http://localhost:8123/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: true
})

const data = response => response.data.data

export const getCurrentUser = () => request.get('/users/me').then(data)
export const register = payload => request.post('/auth/register', payload).then(data)
export const login = payload => request.post('/auth/login', payload).then(data)
export const demoLogin = () => request.post('/auth/demo-login').then(data)
export const logout = () => request.post('/auth/logout')

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
export const reviewResume = id => request.post(`/resumes/${id}/review`).then(data)

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
  const eventSource = new EventSource(`${API_BASE_URL}/analyses/${analysisId}/events`, { withCredentials: true })
  let terminalEventReceived = false
  const hasData = event => typeof event.data === 'string' && event.data.trim().length > 0

  ;['progress', 'report', 'plan', 'done', 'error'].forEach(name => {
    eventSource.addEventListener(name, event => {
      if (!hasData(event)) return

      let payload
      try {
        payload = JSON.parse(event.data)
      } catch {
        terminalEventReceived = true
        eventSource.close()
        handlers.error?.({ message: 'The analysis event stream returned invalid data.' })
        return
      }

      if (name === 'done' || name === 'error') {
        terminalEventReceived = true
        eventSource.close()
      }
      handlers[name]?.(payload)
    })
  })

  eventSource.onerror = event => {
    if (terminalEventReceived || hasData(event)) return
    if (eventSource.readyState === EventSource.CLOSED) {
      handlers.closed?.()
      return
    }
    handlers.reconnecting?.()
  }
  return eventSource
}

export default {
  getCurrentUser,
  register,
  login,
  demoLogin,
  logout,
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
