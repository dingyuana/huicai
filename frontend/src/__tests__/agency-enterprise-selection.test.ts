import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

/**
 * SPEC-P123：代理端会计切换客户后刷新丢失当前企业。
 *
 * <p><b>为什么用行为测试而非源码匹配</b>：缺陷本质是一个**条件判断写错**
 * （`!== undefined` 对 `null` 为真），字符串匹配只能证明「代码长这样」，
 * 证明不了「运行时确实没被覆盖」。本测试直接驱动 store，断言真实状态迁移。
 */

const getUserInfo = vi.fn()

vi.mock('@/api/modules/auth', () => ({
  login: vi.fn(),
  getUserInfo: (...args: unknown[]) => getUserInfo(...args),
}))
vi.mock('@/api/request', () => ({ default: { post: vi.fn(() => Promise.resolve()) } }))
// ⚠️ store 的 logout 会 `router.replace(...).finally(...)`，mock 必须返回 Promise，
//    否则报 Cannot read properties of undefined (reading 'finally') —— 那是装置缺陷，不是被测缺陷
vi.mock('@/router', () => ({ default: { replace: vi.fn(() => Promise.resolve()) } }))

const KEY = 'huicai_current_enterprise_id'

async function newStore() {
  const { useAuthStore } = await import('@/stores/auth.store')
  return useAuthStore()
}

describe('SPEC-P123：当前工作企业不被「归属企业」覆盖', () => {
  beforeEach(() => {
    localStorage.clear()
    getUserInfo.mockReset()
    setActivePinia(createPinia())
  })

  it('AT-123-3a userinfo 返回 enterpriseId=null 时，必须保留本地已选客户', async () => {
    localStorage.setItem(KEY, '2')
    const store = await newStore()
    expect(store.currentEnterpriseId).toBe(2)

    // 代理会计的 t_user.enterprise_id 按设计为 NULL —— 这正是线上情形
    getUserInfo.mockResolvedValue({
      permissions: ['voucher:list'], userType: 'AGENCY',
      agencyId: 1, agencyRole: 'ACCOUNTANT',
      homeEnterpriseId: null, currentEnterpriseId: null,
      enterpriseSelectionRequired: true,
    })

    await store.fetchUserInfo()

    expect(store.currentEnterpriseId).toBe(2)
    expect(localStorage.getItem(KEY)).toBe('2')
  })

  /**
   * AT-123-5a（SPEC-P124 切片4 后已改写）：服务端返回**当前工作企业**时以其为准。
   *
   * 🔴 原用例断言「服务端返回非空**归属企业**时覆盖本地选择」。切片4 已把
   * `enterpriseId` 拆成 `homeEnterpriseId`（归属）与 `currentEnterpriseId`（当前），
   * 该前提不再成立 —— 若继续用 `enterpriseId` 字段名，恰好是切片4 要消灭的
   * 「同名不同义」。故改为断言**当前工作企业**这一语义。
   */
  it('AT-123-5a 服务端返回非空 currentEnterpriseId 时以其为准（回归锁）', async () => {
    localStorage.setItem(KEY, '99')
    const store = await newStore()

    getUserInfo.mockResolvedValue({
      permissions: [], userType: 'ENTERPRISE',
      agencyId: null, agencyRole: '',
      homeEnterpriseId: 5, currentEnterpriseId: 5,
      enterpriseSelectionRequired: false,
    })

    await store.fetchUserInfo()

    expect(store.currentEnterpriseId).toBe(5)
  })

  /** 切片4 新增：归属企业非空但当前为空时，**不得**用归属冒充当前。 */
  it('AT-123-4a 仅有归属企业时不得用它冒充当前工作企业', async () => {
    localStorage.setItem(KEY, '2')
    const store = await newStore()

    getUserInfo.mockResolvedValue({
      permissions: [], userType: 'AGENCY',
      agencyId: 1, agencyRole: 'ACCOUNTANT',
      homeEnterpriseId: 9, currentEnterpriseId: null,
      enterpriseSelectionRequired: true,
    })

    await store.fetchUserInfo()

    expect(store.currentEnterpriseId).toBe(2)
  })

  it('AT-123-1a 未选客户时 needsEnterpriseSelection 为真；选定后为假', async () => {
    const store = await newStore()

    getUserInfo.mockResolvedValue({
      permissions: [], userType: 'AGENCY',
      agencyId: 1, agencyRole: 'ACCOUNTANT',
      homeEnterpriseId: null, currentEnterpriseId: null,
      enterpriseSelectionRequired: true,
    })
    await store.fetchUserInfo()
    expect(store.needsEnterpriseSelection).toBe(true)

    // ⚠️ 必须走真实的 switchEnterprise 动作，不能直接改 localStorage ——
    //    currentEnterpriseId 只在 store **创建时**读一次 localStorage，
    //    事后改 localStorage 不会让 ref 变化（装置陷阱，非被测行为）。
    await store.switchEnterprise(2)
    expect(store.currentEnterpriseId).toBe(2)
    expect(store.needsEnterpriseSelection).toBe(false)

    // 关键回归：切换后再刷新（fetchUserInfo 返回 enterpriseId=null）不能把选择抹掉
    await store.fetchUserInfo()
    expect(store.currentEnterpriseId).toBe(2)
  })

  it('AT-123-6a 退出登录后本地企业键被清除（回归锁）', async () => {
    localStorage.setItem(KEY, '2')
    const store = await newStore()
    await store.logout()
    expect(localStorage.getItem(KEY)).toBe(null)
  })
})
/**
 * SPEC-P124：跨标签页同步。
 *
 * <p>实测复现（双标签页）：A 切到企业2 后，B 未刷新时 store 仍是旧状态，
 * 用内存里的旧 token 请求 → <b>HTTP 200 + 0 条</b>，无任何报错
 * ——旧 token 没有企业号 ⇒ GUC 设不上 ⇒ RLS 静默过滤全部。
 *
 * <p>localStorage 本身跨标签页是同步的，缺的是 Pinia 内存态的跟随。
 */
describe('SPEC-P124：跨标签页跟随切换', () => {
  beforeEach(() => {
    localStorage.clear()
    getUserInfo.mockReset()
    setActivePinia(createPinia())
  })

  /** 模拟「其它标签页改写了 localStorage 后浏览器派发的 storage 事件」。 */
  function fireStorage(key: string, newValue: string | null) {
    window.dispatchEvent(new StorageEvent('storage', { key, newValue }))
  }

  it('AT-124-1 其它标签页切换企业后，本标签页 store 应跟随', async () => {
    const store = await newStore()
    getUserInfo.mockResolvedValue({
      permissions: ['voucher:list'], userType: 'AGENCY',
      agencyId: 1, agencyRole: 'ACCOUNTANT',
      homeEnterpriseId: null, currentEnterpriseId: null,
      enterpriseSelectionRequired: true,
    })
    await store.fetchUserInfo()
    expect(store.currentEnterpriseId).toBe(null)

    // 模拟 A 标签页完成切换：写入新 token 与新企业号，然后派发事件
    const newToken = 'header.payload.sig-new'
    localStorage.setItem(KEY, '2')
    localStorage.setItem('huicai_token', newToken)
    fireStorage('huicai_token', newToken)
    await new Promise((r) => setTimeout(r, 0))

    expect(store.token).toBe(newToken)
    expect(store.currentEnterpriseId).toBe(2)
  })

  it('AT-124-2 无关的 storage 事件不得触发同步', async () => {
    const store = await newStore()
    const before = store.token
    fireStorage('some_unrelated_key', 'x')
    await new Promise((r) => setTimeout(r, 0))
    expect(store.token).toBe(before)
  })

  it('AT-124-3 其它标签页登出后，本标签页不得继续持有旧 token', async () => {
    localStorage.setItem(KEY, '2')
    localStorage.setItem('huicai_token', 'old.token.sig')
    const store = await newStore()
    expect(store.token).toBe('old.token.sig')

    localStorage.removeItem('huicai_token')
    fireStorage('huicai_token', null)
    await new Promise((r) => setTimeout(r, 0))

    expect(store.token).toBe('')
  })
})
