import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as systemApi from '@/api/modules/system'

const mockRequest = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: mockRequest,
}))

beforeEach(() => {
  vi.clearAllMocks()
})

describe('System API Module', () => {
  describe('User APIs', () => {
    it('getUserPage calls correct endpoint', async () => {
      const mockPage = { records: [], total: 0, page: 1, size: 10, pages: 0 }
      mockRequest.get.mockResolvedValue(mockPage)

      const result = await systemApi.getUserPage({ page: 1, size: 10 })
      expect(mockRequest.get).toHaveBeenCalledWith('/system/user/page', {
        params: { page: 1, size: 10 },
      })
      expect(result).toEqual(mockPage)
    })

    it('createUser calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      const params: systemApi.UserParam = {
        username: 'testuser',
        realName: '测试',
        password: 'test123',
        roleIds: [1],
      }
      await systemApi.createUser(params)
      expect(mockRequest.post).toHaveBeenCalledWith('/system/user', params)
    })

    it('deleteUser calls correct endpoint', async () => {
      mockRequest.delete.mockResolvedValue(undefined)

      await systemApi.deleteUser(2)
      expect(mockRequest.delete).toHaveBeenCalledWith('/system/user/2')
    })

    it('getUser calls correct endpoint', async () => {
      const mockUser: systemApi.UserVO = {
        id: 1,
        username: 'admin',
        realName: '管理员',
        nickname: 'admin',
        email: '',
        phone: '',
        avatar: '',
        deptId: 1,
        deptName: '技术部',
        status: 'active',
        remark: '',
        lastLoginIp: '',
        lastLoginAt: '',
        roleIds: [1],
        createdAt: '2026-01-01T00:00:00',
      }
      mockRequest.get.mockResolvedValue(mockUser)

      const result = await systemApi.getUser(1)
      expect(mockRequest.get).toHaveBeenCalledWith('/system/user/1')
      expect(result).toEqual(mockUser)
    })

    it('updateUser calls correct endpoint', async () => {
      mockRequest.put.mockResolvedValue(undefined)

      await systemApi.updateUser(1, { realName: '新名称' })
      expect(mockRequest.put).toHaveBeenCalledWith('/system/user/1', { realName: '新名称' })
    })

    it('resetPwd calls correct endpoint', async () => {
      mockRequest.put.mockResolvedValue(undefined)

      await systemApi.resetPwd(1, 'newpwd123')
      expect(mockRequest.put).toHaveBeenCalledWith('/system/user/1/reset-pwd', { newPassword: 'newpwd123' })
    })
  })

  describe('Role APIs', () => {
    it('getRolePage calls correct endpoint', async () => {
      mockRequest.get.mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 })

      await systemApi.getRolePage({ page: 1, size: 10 })
      expect(mockRequest.get).toHaveBeenCalledWith('/system/role/page', {
        params: { page: 1, size: 10 },
      })
    })

    it('createRole calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await systemApi.createRole({ code: 'admin', name: '管理员' })
      expect(mockRequest.post).toHaveBeenCalledWith('/system/role', { code: 'admin', name: '管理员' })
    })

    it('deleteRole calls correct endpoint', async () => {
      mockRequest.delete.mockResolvedValue(undefined)

      await systemApi.deleteRole(1)
      expect(mockRequest.delete).toHaveBeenCalledWith('/system/role/1')
    })
  })

  describe('Menu APIs', () => {
    it('getMenuTree calls correct endpoint', async () => {
      const mockTree = [
        {
          id: 1,
          name: '系统管理',
          permissionCode: 'system:manage',
          type: 'menu',
          parentId: null,
          path: '/system',
          component: '',
          icon: 'Setting',
          sortOrder: 1,
          isActive: true,
          isVisible: true,
          keepAlive: false,
          alwaysShow: false,
          children: [],
        },
      ]
      mockRequest.get.mockResolvedValue(mockTree)

      const result = await systemApi.getMenuTree()
      expect(mockRequest.get).toHaveBeenCalledWith('/system/menu/tree')
      expect(result).toEqual(mockTree)
    })

    it('createMenu calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await systemApi.createMenu({ name: '报表管理', permissionCode: 'report:list', type: 'menu', path: '/report' })
      expect(mockRequest.post).toHaveBeenCalledWith('/system/menu', {
        name: '报表管理',
        permissionCode: 'report:list',
        type: 'menu',
        path: '/report',
      })
    })
  })

  describe('Dept API', () => {
    it('getDeptTree calls correct endpoint', async () => {
      const mockTree = [
        { id: 1, name: '总公司', parentId: null, sortOrder: 1, status: 'active', leader: '', phone: '', email: '', children: [] },
      ]
      mockRequest.get.mockResolvedValue(mockTree)

      const result = await systemApi.getDeptTree()
      expect(mockRequest.get).toHaveBeenCalledWith('/system/dept/tree')
      expect(result).toEqual(mockTree)
    })
  })
})
