import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

/**
 * 实验室路由受 Feature Flag 控制，默认关闭。
 * 关闭时访问实验室路由必须跳转到 /404（路由守卫 S-26 行为）。
 */
test.describe('实验室路由 - Feature Flag 门禁', () => {
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

  const labRoutes = [
    { path: '/budget', name: '预算管理' },
    { path: '/budget/edit', name: '编辑预算' },
    { path: '/budget/adjustment', name: '预算调整' },
    { path: '/analysis/key-metrics', name: '关键指标' },
    { path: '/analysis/dupont', name: '杜邦分析' },
    { path: '/salary', name: '工资薪酬' },
    { path: '/ai/task', name: 'AI 任务' },
    { path: '/ai/anomaly', name: 'AI 异常' },
  ]

  for (const { path, name } of labRoutes) {
    test(`实验室路由 ${name} (${path}) 关闭时跳转 404`, async ({ page }) => {
      await mockAuth(page)
      await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle', timeout: 15000 })
      await page.waitForTimeout(500)

      expect(page.url()).toContain('/404')
      await expect(page.locator('body')).toContainText('404')
    })
  }
})
