import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/report-amount.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { setupPermissionDirective } from '@/directives/permission'
import { installErrorReporter } from '@/utils/errorReporter'

const app = createApp(App)

// SPEC-P152：全局错误兜底必须在 mount 之前装好，否则首屏渲染错误漏捕。
// 覆盖 Vue errorHandler / window.onerror / unhandledrejection / router.onError，
// 批量上报后端落 t_audit_log（module=FRONTEND），详见 utils/errorReporter.ts。
installErrorReporter(app, router)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: undefined })

// 全局注册图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

setupPermissionDirective(app)

app.mount('#app')
