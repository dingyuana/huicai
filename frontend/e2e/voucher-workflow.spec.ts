import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('凭证管理 - 全流程', () => {
  /** 注入 mock token + 通用 auth */
  async function mockAuth(page: any) {
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
      localStorage.setItem('huicai_current_enterprise_id', '1')
      // 模拟 auth store 状态，避免 fetchUserInfo API 调用
      localStorage.setItem('huicai_auth_mock', 'true')
    })
    // Mock auth API 和所有需要认证的 API
    await page.route(url => url.toString().includes('/api/v1/auth/userinfo'), async route => {
  }

  test('凭证列表页面加载显示标题和操作按钮', async ({ page }) => {
    await mockAuth(page)

    // Mock 分页查询（返回空列表）
    await page.route(url => url.toString().includes('/base/voucher/v1/vouchers/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/finance/voucher`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title')).toHaveText('凭证管理', { timeout: 10000 })
    await expect(page.getByRole('button', { name: /新增凭证/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /导出Excel/i })).toBeVisible()
    // 状态筛选标签
    await expect(page.locator('.el-radio-button').first()).toHaveText('全部')
    await expect(page.locator('.el-radio-button').last()).toContainText('已记账')
  })

  test('凭证列表显示数据行，点击进入详情', async ({ page }) => {
    await mockAuth(page)

    const mockRecords = [
      { id: 1, voucherNo: '记-202607-001', period: '202607', voucherTypeName: '记账凭证',
        totalDebit: 1000, totalCredit: 1000, summary: '采购办公用品', status: 'DRAFT',
        createdByName: '管理员', createdAt: '2026-07-28T10:00:00' },
      { id: 2, voucherNo: '记-202607-002', period: '202607', voucherTypeName: '记账凭证',
        totalDebit: 2500, totalCredit: 2500, summary: '支付货款', status: 'POSTED',
        createdByName: '管理员', createdAt: '2026-07-28T11:00:00' },
    ]

    await page.route(url => url.toString().includes('/base/voucher/v1/vouchers/page'), async route => {
      const body = JSON.parse(route.request().postData() || '{}')
      // 分页查询返回分页数据
      if (body.current === 1 && body.size === 9999) {
        // 统计汇总查询
        await route.fulfill({ status: 200, contentType: 'application/json',
          body: JSON.stringify({ code: 200, msg: 'ok', data: { records: mockRecords, total: 2, current: 1, size: 9999 } })
        })
      } else {
        await route.fulfill({ status: 200, contentType: 'application/json',
          body: JSON.stringify({ code: 200, msg: 'ok', data: { records: mockRecords, total: 2, current: 1, size: 20 } })
        })
      }
    })

    await page.goto(`${BASE}/finance/voucher`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 验证表格数据显示
    await expect(page.getByText('记-202607-001')).toBeVisible()
    await expect(page.getByText('记-202607-002')).toBeVisible()
    await expect(page.getByText('采购办公用品')).toBeVisible()

    // 点击第一条的查看按钮进入详情
    await page.getByRole('button', { name: /查看/i }).first().click()
    await page.waitForURL(/finance\/voucher\/detail/, { timeout: 10000 })
    expect(page.url()).toContain('/finance/voucher/detail')
  })

  test('新增凭证页面加载表单', async ({ page }) => {
    await mockAuth(page)

    // Mock 凭证类型列表
    await page.route(url => url.toString().includes('/v1/voucher-types/all'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { id: 1, code: 'JV', name: '记账凭证', sortOrder: 1, numberingRule: '年月序号', isActive: true },
          { id: 2, code: 'PV', name: '付款凭证', sortOrder: 2, numberingRule: '年月序号', isActive: true },
        ]})
      })
    })
    // Mock 科目树
    await page.route(url => url.toString().includes('/v1/subjects/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { id: '1001', code: '1001', name: '库存现金', parentId: null, level: 1, direction: 'debit', isLeaf: true, children: [] },
          { id: '1002', code: '1002', name: '银行存款', parentId: null, level: 1, direction: 'debit', isLeaf: true, children: [] },
        ]})
      })
    })

    await page.goto(`${BASE}/finance/voucher/edit`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)
    await expect(page.locator('.page-title')).toHaveText('新增凭证', { timeout: 10000 })
    // 表单字段
    await expect(page.locator('label').filter({ hasText: '会计期间' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '凭证类型' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '摘要' })).toBeVisible()
    // 操作按钮
    await expect(page.getByRole('button', { name: /保存草稿/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /保存并提交/i })).toBeVisible()
    // 分录表格列头
    await expect(page.locator('th').filter({ hasText: '科目' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '借方金额' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '贷方金额' })).toBeVisible()
  })

  test('凭证详情页显示完整信息', async ({ page }) => {
    await mockAuth(page)

    const mockVoucher = {
      id: 1, voucherNo: '记-202607-001', period: '202607', voucherTypeId: 1,
      voucherTypeName: '记账凭证', status: 'DRAFT', totalDebit: 1000, totalCredit: 1000,
      summary: '采购办公用品', source: 'MANUAL',
      createdByName: '管理员', createdAt: '2026-07-28T10:00:00',
      entries: [
        { id: 1, subjectId: 1001, subjectCode: '1001', subjectName: '库存现金',
          debit: 1000, credit: 0, summary: '采购办公用品', sortOrder: 1 },
        { id: 2, subjectId: 6001, subjectCode: '6001', subjectName: '主营业务收入',
          debit: 0, credit: 1000, summary: '采购办公用品', sortOrder: 2 },
      ],
    }

    await page.route(url => url.toString().includes('/base/voucher/v1/vouchers/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [mockVoucher], total: 1, current: 1, size: 20 } })
      })
    })

    // 详情页的 getVoucher API
    await page.route(url => url.toString().includes('/base/voucher/v1/vouchers/1'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: mockVoucher })
      })
    })

    // 先进入列表页，再点击进入详情
    await page.goto(`${BASE}/finance/voucher`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)
    // 点击查看按钮
    await page.getByRole('button', { name: /查看/i }).first().click()
    await page.waitForURL(/finance\/voucher\/detail/, { timeout: 10000 })
    await page.waitForTimeout(500)

    // 验证详情页关键信息
    await expect(page.locator('.page-title')).toHaveText('凭证详情', { timeout: 10000 })
    await expect(page.getByText('记-202607-001')).toBeVisible()
    await expect(page.getByText('采购办公用品').first()).toBeVisible()
    // 草稿状态应显示 提交 按钮
    await expect(page.getByRole('button', { name: /提交/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /编辑/i })).toBeVisible()
    // 分录明细表头
    await expect(page.locator('th').filter({ hasText: '科目编码' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '借方金额' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '贷方金额' })).toBeVisible()
  })

  test('凭证状态流转：草稿→提交→审核→记账', async ({ page }) => {
    await mockAuth(page)

    let voucherStatus = 'DRAFT'
    const mockVoucher = () => ({
      id: 1, voucherNo: '记-202607-001', period: '202607', voucherTypeId: 1,
      voucherTypeName: '记账凭证', status: voucherStatus,
      totalDebit: 1000, totalCredit: 1000, summary: '测试流转',
      source: 'MANUAL', createdByName: '管理员', createdAt: '2026-07-28T10:00:00',
      submittedByName: voucherStatus === 'SUBMITTED' ? '管理员' : undefined,
      submittedAt: voucherStatus === 'SUBMITTED' ? '2026-07-28T10:30:00' : undefined,
      auditedByName: voucherStatus === 'AUDITED' ? '审核员' : undefined,
      auditedAt: voucherStatus === 'AUDITED' ? '2026-07-28T11:00:00' : undefined,
      postedByName: voucherStatus === 'POSTED' ? '记账员' : undefined,
      postedAt: voucherStatus === 'POSTED' ? '2026-07-28T11:30:00' : undefined,
      entries: [
        { id: 1, subjectId: 1001, subjectCode: '1001', subjectName: '库存现金',
          debit: 1000, credit: 0, summary: '测试流转', sortOrder: 1 },
        { id: 2, subjectId: 6001, subjectCode: '6001', subjectName: '主营业务收入',
          debit: 0, credit: 1000, summary: '测试流转', sortOrder: 2 },
      ],
    })

    // Mock 列表页和详情页的 API
    await page.route(url => url.toString().includes('/base/voucher/v1/vouchers/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [mockVoucher()], total: 1, current: 1, size: 20 } })
      })
    })
    await page.route(url => url.toString().includes('/base/voucher/v1/vouchers/1'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: mockVoucher() })
      })
    })

    // 步骤1: 进入详情页，验证草稿状态
    await page.goto(`${BASE}/finance/voucher/detail?id=1`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)
    await expect(page.getByText(/草稿|DRAFT/i)).toBeVisible()
    await expect(page.getByRole('button', { name: /提交/i })).toBeVisible()

    // 步骤2: 提交 → 变为已提交
    await page.route(url => url.toString().includes('/base/voucher/v1/vouchers/1/submit'), async route => {
      voucherStatus = 'SUBMITTED'
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok' })
      })
    })
    await page.getByRole('button', { name: /提交/i }).click()
    await page.waitForTimeout(500)
    // 刷新页面模拟状态变更后重新加载
    await page.reload({ waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)
    await expect(page.getByText(/已提交|SUBMITTED/i)).toBeVisible()
    await expect(page.getByRole('button', { name: /审核/i })).toBeVisible()

    // 步骤3: 审核 → 变为已审核
    await page.route(url => url.toString().includes('/base/voucher/v1/vouchers/1/audit'), async route => {
      voucherStatus = 'AUDITED'
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok' })
      })
    })
    await page.getByRole('button', { name: /审核/i }).click()
    await page.waitForTimeout(500)
    await page.reload({ waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)
    await expect(page.getByText(/已审核|AUDITED/i)).toBeVisible()
    await expect(page.getByRole('button', { name: /记账/i })).toBeVisible()

    // 步骤4: 记账 → 变为已记账
    await page.route(url => url.toString().includes('/base/voucher/v1/vouchers/1/post'), async route => {
      voucherStatus = 'POSTED'
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok' })
      })
    })
    await page.getByRole('button', { name: /记账/i }).click()
    await page.waitForTimeout(500)
    await page.reload({ waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)
    await expect(page.getByText(/已记账|POSTED/i)).toBeVisible()
    // 已记账后不再显示提交/审核/记账按钮
    await expect(page.getByRole('button', { name: /提交/i })).not.toBeVisible()
    await expect(page.getByRole('button', { name: /审核/i })).not.toBeVisible()
    await expect(page.getByRole('button', { name: /记账/i })).not.toBeVisible()
  })
})