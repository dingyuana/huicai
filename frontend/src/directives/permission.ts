import type { App } from 'vue'
import { useAuthStore } from '@/stores/auth.store'

export function setupPermissionDirective(app: App) {
  app.directive('permission', {
    mounted(el: HTMLElement, binding) {
      const authStore = useAuthStore()
      const { value } = binding
      if (value && !authStore.hasPermission(value)) {
        el.parentNode?.removeChild(el)
      }
    },
  })

  app.directive('role', {
    mounted(el: HTMLElement, binding) {
      const authStore = useAuthStore()
      const { value } = binding
      if (value && authStore.userInfo && !value.includes(authStore.userInfo.realName)) {
        el.parentNode?.removeChild(el)
      }
    },
  })
}
