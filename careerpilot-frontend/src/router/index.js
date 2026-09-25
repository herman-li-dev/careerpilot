import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/CareerPilotHome.vue'),
    meta: {
      title: 'CareerPilot — Resume and job description preparation',
      description: 'Save English resumes and job descriptions for CareerPilot matching.'
    }
  },
  {
    path: '/app',
    name: 'Workspace',
    component: () => import('../views/CareerPilotHome.vue'),
    meta: {
      title: 'CareerPilot — Your workspace',
      description: 'Manage your private CareerPilot preparation workspace.'
    }
  },
  {
    path: '/app/analyses/:analysisId',
    name: 'AnalysisReport',
    component: () => import('../views/CareerPilotReport.vue'),
    meta: {
      title: 'CareerPilot — Match report',
      description: 'Review your CareerPilot match report, preparation plan, and interview preparation.'
    }
  },
  {
    path: '/analyses/:analysisId',
    redirect: to => ({ name: 'AnalysisReport', params: to.params })
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router
