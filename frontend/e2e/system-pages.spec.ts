import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('系统管理 - 用户管理', () => {
  async function mockAuth(page: any) {
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
      localStorage.setItem('huicai_current_enterprise_id', '1')
    })
    await page.route(url => url.toString().includes('/api/v1/auth/userinfo'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          id: 1, username: 'admin', realName: '管理员', nickname: 'admin',
          email: '', phone: '', avatar: '', deptId: 1, roles: [1],
          permissions: ['admin'],
          userType: 'SUPER_ADMIN',
        }})
      })
    })
  }

  test('页面加载显示查询表单和操作按钮', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/system/user/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })
    await page.route(url => url.toString().includes('/v1/system/user/roles'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })

    await page.goto(`${BASE}/system/user`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('button', { name: /新增用户/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /查询/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /重置/i })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '用户名' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '姓名' })).toBeVisible()
  })

  test('表格显示用户数据', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/system/user/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, username: 'admin', realName: '管理员', nickname: 'admin', deptName: '财务部', status: 'active' },
          { id: 2, username: 'zhangsan', realName: '张三', nickname: 'zs', deptName: '财务部', status: 'active' },
        ], total: 2, current: 1, size: 20 }})
      })
    })
    await page.route(url => url.toString().includes('/v1/system/user/roles'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })

    await page.goto(`${BASE}/system/user`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByText('admin').first()).toBeVisible()
    await expect(page.getByText('张三')).toBeVisible()
    await expect(page.getByText('管理员').first()).toBeVisible()
  })
})

test.describe('系统管理 - 角色管理', () => {
  async function mockAuth(page: any) {
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
      localStorage.setItem('huicai_current_enterprise_id', '1')
    })
    await page.route(url => url.toString().includes('/api/v1/auth/userinfo'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          id: 1, username: 'admin', realName: '管理员', nickname: 'admin',
          email: '', phone: '', avatar: '', deptId: 1, roles: [1],
          permissions: ['admin'],
          userType: 'SUPER_ADMIN',
        }})
      })
    })
  }

  test('页面加载显示查询表单和操作按钮', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/system/role/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/system/role`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('button', { name: /新增角色/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /默认角色模板/i })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '角色编码' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '角色名称' })).toBeVisible()
  })

  test('表格显示角色数据', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/system/role/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, code: 'ADMIN', name: '管理员', description: '系统管理员', status: 'active' },
          { id: 2, code: 'ACCOUNTANT', name: '会计', description: '普通会计', status: 'active' },
        ], total: 2, current: 1, size: 20 }})
      })
    })

    await page.goto(`${BASE}/system/role`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.el-table').getByText('ADMIN').first()).toBeVisible()
    await expect(page.locator('.el-table').getByText('会计').first()).toBeVisible()
  })
})

test.describe('系统管理 - 菜单管理', () => {
  async function mockAuth(page: any) {
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
      localStorage.setItem('huicai_current_enterprise_id', '1')
    })
    await page.route(url => url.toString().includes('/api/v1/auth/userinfo'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          id: 1, username: 'admin', realName: '管理员', nickname: 'admin',
          email: '', phone: '', avatar: '', deptId: 1, roles: [1],
          permissions: ['admin'],
          userType: 'SUPER_ADMIN',
        }})
      })
    })
  }

  test('页面加载显示菜单树表格', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/system/menu/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { id: 1, name: '基础数据', type: 'DIR', permission: '', path: '/basis', children: [
            { id: 11, name: '科目摘要', type: 'MENU', permission: 'subjects:manage', path: '/basis/account-and-summary' },
          ]},
          { id: 2, name: '财务管理', type: 'DIR', permission: '', path: '/finance', children: [] },
        ]})
      })
    })
    await page.route(url => url.toString().includes('/v1/system/menu/options'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })

    await page.goto(`${BASE}/system/menu`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('button', { name: /新增菜单/i })).toBeVisible()
    await expect(page.locator('.el-table').getByText('基础数据')).toBeVisible()
    await expect(page.locator('.el-table').getByText('科目摘要')).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '菜单名称' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '权限标识' })).toBeVisible()
  })

  test('空菜单树显示空表格', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/system/menu/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })
    await page.route(url => url.toString().includes('/v1/system/menu/options'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })

    await page.goto(`${BASE}/system/menu`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('button', { name: /新增菜单/i })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '菜单名称' })).toBeVisible()
  })
})

test.describe('系统管理 - 部门管理', () => {
  async function mockAuth(page: any) {
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
      localStorage.setItem('huicai_current_enterprise_id', '1')
    })
    await page.route(url => url.toString().includes('/api/v1/auth/userinfo'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          id: 1, username: 'admin', realName: '管理员', nickname: 'admin',
          email: '', phone: '', avatar: '', deptId: 1, roles: [1],
          permissions: ['admin'],
          userType: 'SUPER_ADMIN',
        }})
      })
    })
  }

  test('页面加载显示部门树表格', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/system/dept/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { id: 1, name: '总公司', leader: '王总', phone: '010-1234', email: 'boss@test.com', status: 'active', children: [
            { id: 11, name: '财务部', leader: '李会计', phone: '010-5678', email: 'caiwu@test.com', status: 'active' },
          ]},
        ]})
      })
    })

    await page.goto(`${BASE}/system/dept`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('button', { name: /新增部门/i })).toBeVisible()
    await expect(page.getByText('总公司')).toBeVisible()
    await expect(page.getByText('财务部')).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '部门名称' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '负责人' })).toBeVisible()
  })

  test('新增部门弹窗显示表单', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/system/dept/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })

    await page.goto(`${BASE}/system/dept`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await page.getByRole('button', { name: /新增部门/i }).click()
    await page.waitForTimeout(500)

    await expect(page.getByText('新增部门').first()).toBeVisible({ timeout: 5000 })
    await expect(page.locator('label').filter({ hasText: '部门名称' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '负责人' })).toBeVisible()
  })
})

test.describe('系统管理 - 操作日志', () => {
  async function mockAuth(page: any) {
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
      localStorage.setItem('huicai_current_enterprise_id', '1')
    })
    await page.route(url => url.toString().includes('/api/v1/auth/userinfo'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          id: 1, username: 'admin', realName: '管理员', nickname: 'admin',
          email: '', phone: '', avatar: '', deptId: 1, roles: [1],
          permissions: ['admin'],
          userType: 'SUPER_ADMIN',
        }})
      })
    })
  }

  test('页面加载显示查询表单和操作按钮', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/system/audit-log/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/system/audit-log`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('button', { name: /查询/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /重置/i })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '操作人' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '模块' })).toBeVisible()
  })

  test('表格显示审计日志数据', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/system/audit-log/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, module: 'system', action: '用户登录', operatorName: 'admin', operatorId: 1, status: 'SUCCESS', createdAt: '2026-07-30 10:00:00' },
          { id: 2, module: 'finance', action: '凭证过账', operatorName: 'zhangsan', operatorId: 2, status: 'SUCCESS', createdAt: '2026-07-30 11:00:00' },
        ], total: 2, current: 1, size: 20 }})
      })
    })

    await page.goto(`${BASE}/system/audit-log`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByText('用户登录')).toBeVisible()
    await expect(page.getByText('凭证过账')).toBeVisible()
  })
})
