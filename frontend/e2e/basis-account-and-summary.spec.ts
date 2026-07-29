import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('科目摘要 - 基础数据', () => {
  /** 注入 mock token + 通用 auth */
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

  test('页面加载显示科目管理和常用摘要两个 Tab', async ({ page }) => {
    await mockAuth(page)

    // Mock 科目树
    await page.route(url => url.toString().includes('/v1/subjects/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })
    // Mock 摘要列表
    await page.route(url => url.toString().includes('/v1/summary-lib'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/basis/account-and-summary`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 验证 Tab 标题
    await expect(page.locator('.el-tabs__item').first()).toHaveText('会计科目')
    await expect(page.locator('.el-tabs__item').last()).toHaveText('常用摘要')
  })

  test('科目管理 Tab 显示科目树和操作按钮', async ({ page }) => {
    await mockAuth(page)

    const mockTree = [
      { id: '1001', code: '1001', name: '库存现金', parentId: null, level: 1, direction: 'debit', isLeaf: true, isActive: true, auxCalcType: null, children: [] },
      { id: '1002', code: '1002', name: '银行存款', parentId: null, level: 1, direction: 'debit', isLeaf: false, isActive: true, auxCalcType: null, children: [
        { id: '100201', code: '1002.01', name: '工商银行', parentId: '1002', level: 2, direction: 'debit', isLeaf: true, isActive: true, auxCalcType: null, children: [] },
      ]},
      { id: '2001', code: '2001', name: '短期借款', parentId: null, level: 1, direction: 'credit', isLeaf: true, isActive: true, auxCalcType: null, children: [] },
    ]

    await page.route(url => url.toString().includes('/v1/subjects/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: mockTree })
      })
    })
    // Mock 摘要列表（非活动 Tab 也会加载）
    await page.route(url => url.toString().includes('/v1/summary-lib'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/basis/account-and-summary`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 默认显示科目 Tab
    await expect(page.locator('.page-title')).toHaveText('科目管理', { timeout: 10000 })
    // 操作按钮
    await expect(page.getByRole('button', { name: /新增一级科目/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /一键导入常用科目/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /导入科目/i })).toBeVisible()
    // 表格列头
    await expect(page.locator('th').filter({ hasText: '科目编码' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '科目名称' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '方向' })).toBeVisible()
    // 数据行
    await expect(page.getByText('库存现金')).toBeVisible()
    await expect(page.getByText('银行存款')).toBeVisible()
    await expect(page.getByText('短期借款')).toBeVisible()
    // 子科目
    await expect(page.getByText('工商银行')).toBeVisible()
  })

  test('切换到常用摘要 Tab 显示摘要列表', async ({ page }) => {
    await mockAuth(page)

    // Mock 科目树（空）
    await page.route(url => url.toString().includes('/v1/subjects/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })
    // Mock 摘要列表
    await page.route(url => url.toString().includes('/v1/summary-lib'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, summaryCode: 'CG', summaryText: '采购办公用品', category: '采购', sortOrder: 1, isActive: true },
          { id: 2, summaryCode: 'XS', summaryText: '销售商品收入', category: '销售', sortOrder: 2, isActive: true },
        ], total: 2, current: 1, size: 20 }})
      })
    })

    await page.goto(`${BASE}/basis/account-and-summary`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 切换到常用摘要 Tab
    await page.locator('.el-tabs__item').last().click()
    await page.waitForTimeout(500)

    // 验证摘要列表
    await expect(page.getByText('采购办公用品')).toBeVisible()
    await expect(page.getByText('销售商品收入')).toBeVisible()
    // 新增按钮
    await expect(page.getByRole('button', { name: /新增摘要/i })).toBeVisible()
  })
})