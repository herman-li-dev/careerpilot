import { createApp } from 'vue'
import { clerkPlugin } from '@clerk/vue'
import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)
const clerkAuthEnabled = import.meta.env.VITE_CAREERPILOT_AUTH_ENABLED === 'true'
  || import.meta.env.VITE_CAREERPILOT_PUBLIC_RAG_AUTH_ENABLED === 'true'

async function bootstrap() {
  if (clerkAuthEnabled) {
    const publishableKey = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY
    if (!publishableKey) {
      throw new Error('VITE_CLERK_PUBLISHABLE_KEY is required when Clerk authentication is enabled.')
    }
    app.use(clerkPlugin, { publishableKey })
  }

  app.use(router)
  app.mount('#app')
}

bootstrap()
