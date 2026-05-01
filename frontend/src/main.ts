import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import router from './router'
import App from './App.vue'
import '@/styles/index.css'
import '@/styles/element/index.scss'
import { handleError } from '@/utils/error'
import { i18n } from '@/i18n'
import permDirective from '@/directives/perm'

const app = createApp(App)

app.config.errorHandler = (err) => handleError(err)

app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(ElementPlus)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.directive('perm', permDirective)

app.mount('#app')
