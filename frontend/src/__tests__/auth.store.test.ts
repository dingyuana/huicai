import { setActivePinia, createPinia } from 'pinia'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useAuthStore } from '@/stores/auth.store'
import * as authApi from '@/api/modules/auth'

vi.mock('@/api/modules/auth', () => ({
  login: vi.fn(),
  getUserInfo: vi.fn(),
}))

const mockLoginResult = {
  token: 'test-jwt-token',
  refreshToken: 'test-refresh-token',
  tokenType: 'Bearer',
  userInfo: {
    id: 1,
    username: 'admin',
    realName: '管理员',
    nickname: 'admin',
    email: 'admin@huicai.com',
    phone: '13800138000',
    avatar: '',
    deptId: 1,
    roles: [1],
    permissions: ['system:user:list', 'system:role:list'],
  },
}

const mockUserInfo = {
  id: 1,
  username: 'admin',
  realName: '管理员',
  nickname: 'admin',
  email: 'admin@huicai.com',
  phone: '13800138000',
  avatar: '',
  deptId: 1,
  roles: [1],
  permissions: ['system:user:list'],
}

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  vi.clearAllMocks()
})

describe('useAuthStore', () => {
  it('initializes with no token', () => {
    const store = useAuthStore()
    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(store.permissions).toEqual([])
    expect(store.isLoggedIn).toBe(false)
  })

  it('restores token from localStorage', () => {
    localStorage.setItem('huicai_token', 'saved-token')
    const store = useAuthStore()
    expect(store.token).toBe('saved-token')
    expect(store.isLoggedIn).toBe(true)
  })

  it('hasPermission returns true for empty perm', () => {
    const store = useAuthStore()
    expect(store.hasPermission('')).toBe(true)
    expect(store.hasPermission('')).toBe(true)
  })

  it('hasPermission checks permissions array', () => {
    const store = useAuthStore()
    store.permissions = ['system:user:list', 'system:role:list']
    expect(store.hasPermission('system:user:list')).toBe(true)
    expect(store.hasPermission('system:role:list')).toBe(true)
    expect(store.hasPermission('system:user:create')).toBe(false)
  })

  it('login stores token and userInfo', async () => {
    vi.mocked(authApi.login).mockResolvedValue(mockLoginResult)

    const store = useAuthStore()
    await store.login({ username: 'admin', password: 'admin123' })

    expect(store.token).toBe('test-jwt-token')
    expect(store.userInfo).toEqual(mockLoginResult.userInfo)
    expect(store.permissions).toEqual(['system:user:list', 'system:role:list'])
    expect(store.isLoggedIn).toBe(true)
    expect(localStorage.getItem('huicai_token')).toBe('test-jwt-token')
    expect(localStorage.getItem('huicai_refresh_token')).toBe('test-refresh-token')
  })

  it('fetchUserInfo stores user info and permissions', async () => {
    vi.mocked(authApi.getUserInfo).mockResolvedValue(mockUserInfo)

    const store = useAuthStore()
    store.token = 'existing-token'
    await store.fetchUserInfo()

    expect(store.userInfo).toEqual(mockUserInfo)
    expect(store.permissions).toEqual(['system:user:list'])
  })

  it('fetchUserInfo calls logout on error', async () => {
    vi.mocked(authApi.getUserInfo).mockRejectedValue(new Error('Unauthorized'))

    const store = useAuthStore()
    store.token = 'bad-token'
    store.userInfo = mockUserInfo as any
    store.permissions = ['system:user:list']

    const originalLocation = window.location
    const assignMock = vi.fn()
    Object.defineProperty(window, 'location', { value: { href: '' }, writable: true })

    await store.fetchUserInfo()

    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(store.permissions).toEqual([])

    Object.defineProperty(window, 'location', { value: originalLocation, writable: true })
  })

  it('logout clears all state', () => {
    const store = useAuthStore()
    store.token = 'some-token'
    store.userInfo = mockUserInfo as any
    store.permissions = ['system:user:list']
    localStorage.setItem('huicai_token', 'some-token')
    localStorage.setItem('huicai_refresh_token', 'refresh-token')

    expect(store.isLoggedIn).toBe(true)

    const originalLocation = window.location
    Object.defineProperty(window, 'location', { value: { href: '' }, writable: true })

    store.logout()

    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(store.permissions).toEqual([])
    expect(store.isLoggedIn).toBe(false)
    expect(localStorage.getItem('huicai_token')).toBeNull()
    expect(localStorage.getItem('huicai_refresh_token')).toBeNull()
    expect(window.location.href).toBe('/login')

    Object.defineProperty(window, 'location', { value: originalLocation, writable: true })
  })
})
