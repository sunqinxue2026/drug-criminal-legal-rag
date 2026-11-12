import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/global.css'
import { installElementPlus } from '@/plugins/element-plus'
import { useUserStore } from '@/stores/user'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
useUserStore().loadFromStorage()
app.use(router)
installElementPlus(app)

app.mount('#app')
