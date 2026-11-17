import { test, expect } from '@playwright/test'

/**
 * SPEC-P154 L5：工资薪酬页端到端冒烟（v4.0 L5 三项之「全链 UI 冒烟 + 弱网探针」）。
 *
 * 两种模式的分工（刻意）：
 *  - 冒烟组走【真后端】（:8000 生产 compose 栈 + ent2 演练数据 = V1.107 留存：
 *    bill 1 PAID / 202611 / 税 645 / 净 27355 / SALC-1/SALP-1）——L5 的价值在
 *    「前端+后端+DB 全链同构」，mock 掉后端就只剩组件测试的覆盖面。
 *  - 按钮矩阵组 = 真登录 + 仅 mock 列表数据：DRAFT/CONFIRMED 态的按钮可见性规则
 *    （编辑/确认计提 only DRAFT、发放 only CONFIRMED）需要三种状态的数据，
 *    真库只有 PAID 单；注入假 token 会被其他真接口 401 踢回登录页（踩坑留证），
 *    所以认证走真登录、数据走 route mock——认证与数据分离。
 *  - 弱网组：request.ts 对 5xx/网络中断上报 errorReporter（POST /system/audit-log/client，
 *    P152 链路）——route 注入 500/abort 断言上报请求真实发出，即「错误可见性」探针。
 *    🔴 上报走 10s 定时批（FLUSH_INTERVAL_MS=10_000），waitForRequest 超时必须 >10s。
 */

const BASE = 'http://localhost:3001'

async function login(page: any, username: string, password: string) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 20000 })
  await page.getByPlaceholder(/用户名|账号|username/i).fill(username)
  await page.getByPlaceholder(/密码|password/i).fill(password)
  await page.getByRole('button', { name: /登\s*录|登录|Login/i }).click()
  await page.waitForURL(/\/(dashboard|home|finance|$)/, { timeout: 15000 })
}

test.describe('工资薪酬 L5 冒烟（真后端 ent2 演练数据）', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, 'accountant01', 'admin123')
  })

  test('工资单列表渲染 ent2 演练数据（202611/已发放/税 645/净 27355）', async ({ page }) => {
    await page.goto(`${BASE}/salary/bill`, { waitUntil: 'networkidle', timeout: 20000 })
    await page.waitForTimeout(800)

    // 列表行：演练夹具 bill 1（PAID）
    await expect(page.getByRole('button', { name: /新建工资单/i })).toBeVisible()
    await expect(page.locator('table, .el-table').first()).toContainText('202611', { timeout: 10000 })
    const row = page.locator('.el-table__body tr').filter({ hasText: '202611' }).first()
    await expect(row).toContainText('已发放')
    await expect(row).toContainText('645')     // 个税（V1.107 生产实机同值）
    await expect(row).toContainText('27,355')  // 实发合计（money formatter 千分位）
    // PAID 态：不得出现 DRAFT/CONFIRMED 专属按钮
    await expect(row.getByRole('button', { name: /确认计提/i })).toHaveCount(0)
    await expect(row.getByRole('button', { name: /发放/i })).toHaveCount(0)
  })

  test('详情快照逐字段（SALC-1/SALP-1 凭证号 + 税 645）', async ({ page }) => {
    await page.goto(`${BASE}/salary/bill`, { waitUntil: 'networkidle', timeout: 20000 })
    await page.waitForTimeout(800)

    const row = page.locator('.el-table__body tr').filter({ hasText: '202611' }).first()
    await row.getByRole('button', { name: /详情/i }).click()
    await page.waitForTimeout(800)

    const dialog = page.locator('.el-dialog:visible').last()
    await expect(dialog).toBeVisible({ timeout: 8000 })
    await expect(dialog).toContainText('SALC-1')
    await expect(dialog).toContainText('SALP-1')
    await expect(dialog).toContainText('645')
  })
})

test.describe('工资薪酬按钮状态矩阵（真登录 + mock 态数据）', () => {
  test('DRAFT→编辑/确认计提；CONFIRMED→发放；PAID→只读详情', async ({ page }) => {
    await login(page, 'accountant01', 'admin123')
    const mk = (id: number, period: string, status: string) => ({
      id, period, status, totalGross: 30000, totalSocial: 2000, totalSpecialAdd: 0,
      totalOtherDeduct: 0, totalTax: 645, totalNet: 27355,
      voucherNo: status === 'DRAFT' ? null : `SALC-${id}`,
      payVoucherNo: status === 'PAID' ? `SALP-${id}` : null,
      remark: '', createdAt: '2026-11-01 10:00:00',
    })
    await page.route(url => url.toString().includes('/salary/v1/salary-bills') && !url.toString().includes('/items'),
      async route => {
        await route.fulfill({ status: 200, contentType: 'application/json',
          body: JSON.stringify({ code: 200, msg: 'ok', data: [
            mk(101, '202609', 'DRAFT'), mk(102, '202610', 'CONFIRMED'), mk(103, '202611', 'PAID'),
          ] }) })
      })

    await page.goto(`${BASE}/salary/bill`, { waitUntil: 'networkidle', timeout: 20000 })
    await page.waitForTimeout(800)

    const draft = page.locator('.el-table__body tr').filter({ hasText: '202609' }).first()
    await expect(draft.getByRole('button', { name: /编辑/i })).toBeVisible()
    await expect(draft.getByRole('button', { name: /确认计提/i })).toBeVisible()

    const confirmed = page.locator('.el-table__body tr').filter({ hasText: '202610' }).first()
    await expect(confirmed.getByRole('button', { name: /发放/i })).toBeVisible()
    await expect(confirmed.getByRole('button', { name: /确认计提/i })).toHaveCount(0)

    const paid = page.locator('.el-table__body tr').filter({ hasText: '202611' }).first()
    await expect(paid.getByRole('button', { name: /详情/i })).toBeVisible()
    await expect(paid.getByRole('button', { name: /编辑/i })).toHaveCount(0)
    await expect(paid.getByRole('button', { name: /发放/i })).toHaveCount(0)
  })
})

test.describe('弱网/服务端故障探针（errorReporter 上报链路，P152）', () => {
  test('API 500 触发前端上报 /audit-log/client', async ({ page }) => {
    await login(page, 'accountant01', 'admin123')

    // 工资单列表接口注入 500 —— request.ts 拦截器对 5xx 走 errorReporter 上报
    await page.route(url => url.toString().includes('/salary/v1/salary-bills'), async route => {
      await route.fulfill({ status: 500, contentType: 'application/json',
        body: JSON.stringify({ code: 500, msg: 'internal error', data: null }) })
    })

    const reported = page.waitForRequest(
      req => req.url().includes('/system/audit-log/client') && req.method() === 'POST',
      { timeout: 16000 })

    await page.goto(`${BASE}/salary/bill`, { waitUntil: 'domcontentloaded', timeout: 20000 })

    const req = await reported
    const payload = JSON.parse(req.postData() || '{}')
    // 上报体必须带可定位信息（P152 六件套：message 含 [http:500] + 接口路径）
    expect(JSON.stringify(payload)).toMatch(/500|salary/i)
  })

  test('网络中断（abort）同样触发上报且页面不白屏', async ({ page }) => {
    await login(page, 'accountant01', 'admin123')

    await page.route(url => url.toString().includes('/salary/v1/salary-bills'),
      route => route.abort('failed'))

    const reported = page.waitForRequest(
      req => req.url().includes('/system/audit-log/client') && req.method() === 'POST',
      { timeout: 16000 })

    await page.goto(`${BASE}/salary/bill`, { waitUntil: 'domcontentloaded', timeout: 20000 })
    await reported

    // 断网 ≠ 白屏：页面骨架仍在
    await expect(page.getByRole('button', { name: /新建工资单/i })).toBeVisible({ timeout: 8000 })
  })
})
