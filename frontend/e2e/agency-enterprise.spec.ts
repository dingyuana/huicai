import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('代理 - 客户管理', () => {
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
              permissions: ['admin', 'agency:enterprise:list'],
              userType: 'SUPER_ADMIN',
              agencyId: 1, enterpriseId: 1,
            }})
          })
        })
        await page.route(url => url.toString().includes('/api/v1/auth/agency-info'), async route => {
          await route.fulfill({ status: 200, contentType: 'application/json',
            body: JSON.stringify({ code: 200, msg: 'ok', data: { agencyId: 1, agencyName: '测试代理公司' } })
          })
        })
  }

  test('页面加载显示客户列表和统计卡片', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/agency/enterprises/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, enterpriseCode: 'ENT001', enterpriseName: '测试企业A', taxId: '91110108MA001', status: 'ACTIVE', seedDataDone: true, createdAt: '2026-07-01' },
          { id: 2, enterpriseCode: 'ENT002', enterpriseName: '测试企业B', taxId: '91110108MA002', status: 'PENDING', seedDataDone: false, createdAt: '2026-07-15' },
        ], total: 2, current: 1, size: 10 }})
      })
    })

    await page.goto(`${BASE}/agency/enterprise-list`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByText('客户企业列表')).toBeVisible()
    // 统计卡片
    await expect(page.getByText('总客户数')).toBeVisible()
    await expect(page.getByText('活跃客户')).toBeVisible()
    // 表格数据
    await expect(page.getByText('测试企业A')).toBeVisible()
    await expect(page.getByText('测试企业B')).toBeVisible()
    // 操作按钮
    await expect(page.getByRole('button', { name: /搜索/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /新增企业/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /批量操作/i })).toBeVisible()
  })

  test('企业状态标签显示正确', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/agency/enterprises/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, enterpriseCode: 'ENT001', enterpriseName: '测试企业A', status: 'ACTIVE', seedDataDone: true, createdAt: '2026-07-01' },
          { id: 2, enterpriseCode: 'ENT002', enterpriseName: '测试企业B', status: 'PENDING', seedDataDone: false, createdAt: '2026-07-15' },
          { id: 3, enterpriseCode: 'ENT003', enterpriseName: '测试企业C', status: 'SUSPENDED', seedDataDone: true, createdAt: '2026-06-01' },
        ], total: 3, current: 1, size: 10 }})
      })
    })

    await page.goto(`${BASE}/agency/enterprise-list`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 激活状态
    await expect(page.getByText('已激活').first()).toBeVisible()
    // 待激活状态
    await expect(page.getByText('待激活')).toBeVisible()
    // 已暂停状态
    await expect(page.getByText('已暂停')).toBeVisible()
    // 初始化状态
    await expect(page.getByText('已完成').first()).toBeVisible()
    await expect(page.getByText('未完成')).toBeVisible()
  })

  test('新增企业弹窗显示并创建', async ({ page }) => {
    await mockAuth(page)
    let enterprises: any[] = []

    await page.route(url => url.toString().includes('/v1/agency/enterprises/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: enterprises, total: enterprises.length, current: 1, size: 10 } })
      })
    })
    await page.route(url => url.toString().includes('/v1/agency/enterprises') && !url.toString().includes('/page'), async route => {
      const body = JSON.parse(route.request().postData() || '{}')
      enterprises = [{ id: 1, ...body, status: 'PENDING', seedDataDone: false, createdAt: '2026-07-28' }]
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { id: 1 } })
      })
    })

    await page.goto(`${BASE}/agency/enterprise-list`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 新增企业
    await page.getByRole('button', { name: /新增企业/i }).click()
    await page.waitForTimeout(500)

    await expect(page.getByText('新增企业').first()).toBeVisible({ timeout: 5000 })
    // 填写表单
    await page.locator('.el-dialog input[placeholder="企业编码"]').fill('ENT003')
    await page.locator('.el-dialog input[placeholder="企业名称"]').fill('新测试企业')
    await page.locator('.el-dialog input[placeholder="纳税人识别号"]').fill('91110108MA003')
    // 创建
    await page.getByRole('button', { name: /创建/i }).click()
    await page.waitForTimeout(1000)

    // 弹窗关闭
    await expect(page.locator('.el-dialog__title:has-text("新增企业")')).not.toBeVisible()
    // 列表刷新
    await expect(page.getByText('新测试企业')).toBeVisible()
  })
})