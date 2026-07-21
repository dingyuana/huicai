import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useAuthStore } from './auth.store'

export const useLabStore = defineStore('lab', () => {
  const authStore = useAuthStore()

  // 本地存储 Key
  const STORAGE_KEY = 'huicai:lab:enabled'

  // 状态：仅超管可见，默认关闭
  const _enabled = ref(false)

  // 细粒度功能开关
  const features = ref<Record<string, boolean>>({
    budget: false,      // 预算管理
    analysis: false,    // 财务分析（杜邦/关键指标）
    salary: false,      // 工资薪酬
    aiAgents: false,    // AI Agent 入口
  })

  // 初始化：从本地存储读取，仅超管生效
  function init() {
    if (authStore.isSuperAdmin) {
      const stored = localStorage.getItem(STORAGE_KEY)
      _enabled.value = stored === 'true'
      // 同步细粒度开关
      if (_enabled.value) {
        features.value = {
          budget: true,
          analysis: true,
          salary: true,
          aiAgents: true,
        }
      }
    } else {
      _enabled.value = false
    }
  }

  // 切换开关（仅超管）
  function toggle() {
    if (!authStore.isSuperAdmin) return
    _enabled.value = !_enabled.value
    localStorage.setItem(STORAGE_KEY, _enabled.value.toString())
    // 触发路由重新计算
    window.location.reload()
  }

  // 切换单项功能（仅超管）
  function toggleFeature(key: keyof typeof features.value) {
    if (!authStore.isSuperAdmin) return
    features.value[key] = !features.value[key]
    localStorage.setItem(`huicai:lab:feature:${key}`, String(features.value[key]))
    window.location.reload()
  }

  // 对外只读
  const enabled = computed(() => _enabled.value && authStore.isSuperAdmin)

  return {
    enabled,
    features,
    init,
    toggle,
    toggleFeature,
  }
})