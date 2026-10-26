import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import BatchActionBar from '@/components/batch/BatchActionBar.vue'
import { useAuthStore } from '@/stores/auth.store'
import type { BatchActionDef } from '@/composables/useBatchOperation'

/**
 * SPEC-P136 —— 批量操作条按钮的权限门禁（AT-136-1 / AT-136-2）。
 *
 * <p>验证的是**真实接线**：组件从 auth store 读权限，而不是靠注入的桩。
 */

const ACTIONS: BatchActionDef[] = [
  { key: 'submit', label: '批量提交', permission: 'voucher:submit' },
  { key: 'audit', label: '批量审核', permission: 'voucher:audit' },
]

const STATUS_MATRIX: Record<string, string[]> = {
  DRAFT: ['submit'],
  SUBMITTED: ['audit'],
}

function mountBar(permissions: string[], status = 'SUBMITTED') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.permissions = permissions

  const wrapper = mount(BatchActionBar, {
    props: {
      rows: [{ id: 1, status }],
      actions: ACTIONS,
      statusMatrix: STATUS_MATRIX,
    },
    global: { plugins: [pinia] },
  })
  return wrapper
}

describe('SPEC-P136 BatchActionBar 权限门禁', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('AT-136-1 disabledWhenLacksPermission：无权限 → 视觉禁用但仍可点击并派发 action', async () => {
    const wrapper = mountBar([]) // 无 voucher:audit
    const btn = wrapper.get('[data-test="batch-action-audit"]')

    expect(btn.classes()).toContain('is-permission-locked')
    // 关键：**不能**是原生 disabled —— 否则 click 不派发，用户点不出解释
    expect(btn.attributes('disabled')).toBeUndefined()

    await btn.trigger('click')
    expect(wrapper.emitted('action')).toEqual([['audit']])
  })

  it('AT-136-2 enabledWhenHasPermission：有权限 → 可用且不派发权限禁用样式', async () => {
    const wrapper = mountBar(['voucher:audit'])
    const btn = wrapper.get('[data-test="batch-action-audit"]')

    expect(btn.classes()).not.toContain('is-permission-locked')
    expect(btn.attributes('disabled')).toBeUndefined()

    await btn.trigger('click')
    expect(wrapper.emitted('action')).toEqual([['audit']])
  })

  it('状态不符 → 原生 disabled（与「无权限」的交互刻意不同）', () => {
    const wrapper = mountBar(['voucher:audit'], 'DRAFT') // DRAFT 不支持 audit
    const btn = wrapper.get('[data-test="batch-action-audit"]')
    expect(btn.attributes('disabled')).toBeDefined()
    expect(btn.classes()).not.toContain('is-permission-locked')
  })

  it('无权限 + 状态不符 → 权限优先，仍可点击（先告诉用户找管理员）', async () => {
    const wrapper = mountBar([], 'DRAFT')
    const btn = wrapper.get('[data-test="batch-action-audit"]')
    expect(btn.attributes('disabled')).toBeUndefined()
    expect(btn.classes()).toContain('is-permission-locked')
  })
})
