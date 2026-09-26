import { createRouter, createWebHistory } from 'vue-router'

const clerkApplicationAuthEnabled = import.meta.env.VITE_CAREERPILOT_AUTH_ENABLED === 'true'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: clerkApplicationAuthEnabled
      ? () => import('../views/PublicLanding.vue')
      : () => import('../views/CareerPilotHome.vue'),
    meta: {
      title: 'CareerPilot — Resume and job description preparation',
      description: 'CareerPilot turns Resume and job-description evidence into focused career preparation.'
    }
  },
  {
    path: '/privacy',
    name: 'PrivacyPolicy',
    component: () => import('../views/PrivacyPolicy.vue'),
    meta: {
      title: 'Privacy Policy — CareerPilot',
      description: 'How CareerPilot handles Google sign-in, Resume data, AI processing, and account information.'
    }
  },
  {
    path: '/terms',
    name: 'TermsOfService',
    component: () => import('../views/TermsOfService.vue'),
    meta: {
      title: 'Terms of Service — CareerPilot',
      description: 'Terms for using the CareerPilot career-preparation application.'
    }
  },
  {
    path: '/app',
    name: 'Workspace',
    component: () => import('../views/CareerPilotHome.vue'),
    meta: {
      requiresAuthentication: true,
      title: 'CareerPilot — Your workspace',
      description: 'Manage your private CareerPilot preparation workspace.'
    }
  },
  {
    path: '/app/analyses/:analysisId',
    name: 'AnalysisReport',
    component: () => import('../views/CareerPilotReport.vue'),
    meta: {
      requiresAuthentication: true,
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
  if (to.meta.description) {
    document.querySelector('meta[name="description"]')
      ?.setAttribute('content', to.meta.description)
  }
  next()
})

export default router
