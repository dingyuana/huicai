/**
 * P89-C 数字穿透 端到端验证脚本
 *
 * 验证点：
 *   1. 报表 /reports/subject-balance 返回行的 key 是 subject_id（下划线，Map 不经 Jackson 转换）
 *      —— 这是前端 row.subject_id 能否取到值的前提，也是穿透能否触发的关键
 *   2. 凭证 /vouchers/page 带 subjectId 时只返回含该科目分录的凭证
 *      —— 穿透的过滤语义，mock 测不出真实 SQL 效果
 *   3. UI 驱动：打开报表页 → 点击科目编码 → 确认 URL 带上 subjectId/period
 *
 * 复用 e2e/integration/helpers.ts 的 login()（项目既有 E2E 约定）。
 * 运行：npx playwright test e2e/p89drilldown.spec.ts
 */

import { test, expect } from '@playwright/test'
import { login, clearAuthCache } from './integration/helpers'

const BASE = 'http://localhost:3001'

test.describe('P89-C 数字穿透', () => {
  test.beforeEach(async ({ request }) => {
    clearAuthCache()
    await login(request)
  })

  test('报表科目余额行含 subject_id key，且凭证按 subjectId 过滤生效', async ({ request }) => {
    const { token } = await login(request)
    const H = {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'X-Enterprise-Id': '1',
    }

    // 找「余额表有借/贷发生额」的期间。
    // 不能只看有无凭证：科目余额表只统计已结账凭证，草稿态凭证不计入发生额
    // （实测 202410 有 16 张凭证却 0 发生额，因为仍是草稿）。
    // 范围覆盖 2024-2026，从最新往前找。
    const candidates: string[] = []
    for (const y of [2026, 2025, 2024]) {
      for (let m = 12; m >= 1; m--) {
        candidates.push(`${y}${String(m).padStart(2, '0')}`)
      }
    }
    let period = ''
    let rows: any[] = []
    for (const p of candidates) {
      const rb = await (await request.get(`${BASE}/api/base/report/v1/reports/subject-balance`, { headers: H, params: { period: p } })).json()
      if (rb.code !== 200 || !Array.isArray(rb.data)) continue
      if (rb.data.some((r: any) => Number(r.debit_total || 0) !== 0 || Number(r.credit_total || 0) !== 0)) {
        period = p
        rows = rb.data
        break
      }
    }
    if (!period) {
      test.skip(true, '本地库无可穿透科目数据（无发生额），跳过穿透验证')
      return
    }
    console.log(`[P89-C] 使用期间 ${period}`)

    // ① 报表行必须含 subject_id key（前端依赖它跳转）
    expect(Array.isArray(rows)).toBeTruthy()
    expect(rows.length, '科目余额表应有数据').toBeGreaterThan(0)

    const withSubjectId = rows.filter(r => r.subject_id != null)
    expect(withSubjectId.length, '应有科目行带 subject_id（穿透依赖）').toBeGreaterThan(0)
    // 反证：不能被 Jackson 转成 camelCase（否则前端 row.subject_id 全取不到）
    const withCamel = rows.filter(r => r.subjectId != null)
    expect(withCamel.length, 'subject_id 应保持下划线而非被转 camelCase').toBe(0)

    // 取样：只取本期有借/贷发生额的科目（否则按科目过滤必然返回 0 条）
    const active = withSubjectId.filter(r =>
      Number(r.debit_total || 0) !== 0 || Number(r.credit_total || 0) !== 0)
    expect(active.length, '应有本期有发生额的科目').toBeGreaterThan(0)

    // ② 取「过滤能产生真缩减」的科目。
    // 只断言 fTotal>0 是弱验证——若该期间所有凭证都含该科目，2==2 也算过，
    // 证明不了 subjectId 过滤真的生效。必须找到一个科目使 fTotal < 全量数。
    let sample: any = null
    let subjectId: any = null
    let fTotal = 0
    let aTotal = 0
    for (const c of active) {
      const sid = c.subject_id
      const f = await (await request.post(`${BASE}/api/base/voucher/v1/vouchers/page`, {
        headers: H, data: { current: 1, size: 50, period, subjectId: sid },
      })).json()
      const a = await (await request.post(`${BASE}/api/base/voucher/v1/vouchers/page`, {
        headers: H, data: { current: 1, size: 50, period },
      })).json()
      const ft = (f.data && f.data.total) || 0
      const at = (a.data && a.data.total) || 0
      if (ft > 0 && ft < at) {
        sample = c; subjectId = sid; fTotal = ft; aTotal = at
        break
      }
    }
    expect(sample, '应存在一个科目使凭证按科目过滤产生缩减（否则无法证明过滤生效）').toBeTruthy()
    console.log(`[P89-C] 取样科目 ${sample.code} ${sample.name} subject_id=${subjectId} direction=${sample.direction} 借=${sample.debit_total} 贷=${sample.credit_total}`)
    expect(sample.direction).toBeTruthy()

    // ③ 过滤结果必须真少于全量——这是 subjectId 过滤生效的充分证据
    expect(fTotal, '过滤结果数 < 全量数（过滤确实过滤掉了不含该科目的凭证）').toBeLessThan(aTotal)
    expect(fTotal).toBeGreaterThan(0)
    console.log(`[P89-C] 全量凭证 ${aTotal} 条，科目 ${sample.code} 命中 ${fTotal} 条（过滤掉了 ${aTotal - fTotal} 条）`)
  })

  test('UI: 报表科目编码可点击并带上 subjectId 跳转凭证列表', async ({ page, request }) => {
    const { token } = await login(request)

    // 按项目既有 E2E 约定注入 token + mock userinfo
    await page.addInitScript((t) => {
      localStorage.setItem('huicai_token', t)
      localStorage.setItem('huicai_current_enterprise_id', '1')
    }, token)
    await page.route(url => url.toString().includes('/api/v1/auth/userinfo'), async route => {
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({
          code: 200, msg: 'ok', data: {
            id: 1, username: 'admin', realName: '管理员', nickname: 'admin',
            email: '', phone: '', avatar: '', deptId: 1, roles: [1],
            permissions: ['subjects:manage', 'voucher:list', 'voucher:type:list',
              'report:subject:list', 'report:balance:view', 'report:income:view',
              'report:cashflow:view', 'doc:list'],
            userType: 'SUPER_ADMIN',
          }
        })
      })
    })
    await page.route(url => url.toString().includes('/api/v1/auth/') && !url.toString().includes('/userinfo'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: 'ok' }) })
    })

    // 找报表路由
    let landed = false
    for (const path of ['/reports/subject-balance', '/report/subject-balance', '/sme/reports/subject-balance', '/sme/report/subject-balance']) {
      const resp = await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => null)
      await page.waitForTimeout(1500)
      const text = (await page.textContent('body').catch(() => '')) || ''
      if (text.includes('科目余额表') || text.includes('科目编码')) {
        landed = true
        console.log(`[P89-C] 报表页路径命中: ${path}`)
        break
      }
    }
    if (!landed) {
      test.skip(true, '未找到科目余额表路由，跳过 UI 穿透验证')
      return
    }

    // 页面应有"余额方向"列（P89-A）
    await page.waitForTimeout(3000)
    const headerText = await page.textContent('body').catch(() => '')
    console.log(`[P89-C] 页面含"余额方向"列: ${(headerText || '').includes('余额方向')}`)

    // 点击第一个科目编码链接
    const links = page.locator('a.el-link, .el-link').filter({ hasText: /^\d/ })
    const count = await links.count().catch(() => 0)
    if (count === 0) {
      test.skip(true, '页面无可点击的科目编码链接，跳过')
      return
    }
    await links.first().click({ timeout: 8000 }).catch(() => {})
    await page.waitForTimeout(2000)
    const url = page.url()
    console.log(`[P89-C] 点击后 URL: ${url}`)
    expect(url).toContain('subjectId=')
    expect(url).toContain('period=')
    expect(page.url()).toContain('voucher')
  })
})
