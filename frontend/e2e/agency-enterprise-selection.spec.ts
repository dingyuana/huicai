import { test, expect, type Page } from '@playwright/test'

/**
 * SPEC-P123：代理端会计「菜单可见但点了不跳」+ 刷新丢失当前客户。
 *
 * <p><b>本 spec 补的是什么盲区</b>：{@code agency-role-usability.spec.ts} 只断言
 * 菜单**可见**（数量 + 标题），并明确写着「不用物理点击，el-menu 点击会触发路由跳转，
 * 元素随之失效」—— 于是「可见但不可点」「点击无反应」这类缺陷对它**恒绿**。
 * 本 spec 专门用物理点击验证 AT-123-4（点得动），并覆盖锁定态与刷新保持。
 *
 * <p>⚠️ baseURL 用 127.0.0.1 而非 localhost：WSL 下 localhost 优先解析 ::1 会超时。
 */

const BASE = 'http://127.0.0.1:3001'
const USER = 'accountant01'

async function login(page: Page) {
  await page.goto(`${BASE}/login`)
  await page.locator('input[type="text"]').first().fill(USER)
  await page.locator('input[type="password"]').first().fill('admin123')
  await page.locator('button').filter({ hasText: /登录|登 录/i }).click()
  await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 20000 })
  await page.waitForTimeout(800)
}

/**
 * 展开全部侧边栏分组。
 *
 * <p>⚠️ 必须跳过已展开的分组（判 `is-opened`）：el-sub-menu 的标题点击是**开关**语义，
 * 无脑逐个点会把已展开的分组**再点关闭** —— 本 spec 初版即栽在这里，
 * 表现为「菜单项定位到了但 hidden」。
 */
async function expandAllGroups(page: Page) {
  const titles = page.locator('.sidebar-menu > .el-sub-menu > .el-sub-menu__title')
  const n = await titles.count()
  for (let i = 0; i < n; i++) {
    const t = titles.nth(i)
    if ((await t.getAttribute('class'))?.includes('is-opened')) continue
    await t.click({ timeout: 3000 }).catch(() => {})
    await page.waitForTimeout(150)
  }
  await page.waitForTimeout(300)
}

test.describe('SPEC-P123：未选客户时菜单锁定 + 选后刷新不丢', () => {
  test.setTimeout(120_000)

  test('AT-123-1 未选当前客户时给出置灰提示与出路', async ({ page }) => {
    await login(page)
    await expandAllGroups(page)

    await expect(page.locator('.menu-lock-hint')).toBeVisible()
    // 必须回答「怎么才能点」：提示里要有直达客户列表的可点入口
    await expect(page.locator('.menu-lock-link')).toBeVisible()
    // 置灰态必须真的落在菜单上（而不是只有提示）
    await expect(page.locator('.sidebar-menu.menu-locked')).toHaveCount(1)
  })

  test('AT-123-2 锁定态下点业务菜单不跳转，且不出现「点了没反应」', async ({ page }) => {
    await login(page)
    await expandAllGroups(page)
    const before = page.url()

    // pointer-events:none 会让常规 click 超时；这正是「点不动」的预期表现。
    // 用短超时并容忍失败，然后断言 URL 未变。
    await page.locator('.sidebar-menu .el-menu-item', { hasText: '期初建账' })
      .first().click({ timeout: 2500 }).catch(() => {})

    await page.waitForTimeout(600)
    expect(page.url()).toBe(before)
  })

  test('AT-123-4 选定客户后，点击菜单能真正跳转（P121 的盲区）', async ({ page }) => {
    await login(page)
    await expandAllGroups(page)

    const enter = page.locator('button:has-text("进入")').first()
    await expect(enter).toBeVisible()
    await enter.click()
    await page.waitForTimeout(2000)

    await expandAllGroups(page)
    // 定向展开「总账」分组：进入企业后页面跳 /dashboard，分组状态会重置，
    // 而逐个点的方式在状态不确定时不可靠 ⇒ 显式点目标分组（文本以源码为准，不猜）
    const group = page.locator('.sidebar-menu .el-sub-menu__title', { hasText: '总账' }).first()
    if (!(await group.getAttribute('class'))?.includes('is-opened')) {
      await group.click({ timeout: 5000 }).catch(() => {})
      await page.waitForTimeout(500)
    }

    const item = page.locator('.sidebar-menu .el-menu-item', { hasText: '期初建账' }).first()
    await expect(item).toBeVisible()
    await item.click({ timeout: 8000 })
    await page.waitForTimeout(1500)

    expect(page.url()).toContain('/finance/beginning-balance')
  })

  test('AT-123-3 刷新后仍能点击菜单（回归：原缺陷点）', async ({ page }) => {
    await login(page)
    await expandAllGroups(page)
    await page.locator('button:has-text("进入")').first().click()
    await page.waitForTimeout(2000)

    await page.reload()
    await page.waitForTimeout(2500)

    // ⚠️ 断言必须落在「刷新后的下一次导航」，不是刷新当次。
    //
    // 路由守卫里 `isAgency && !currentEnterpriseId` 的判定（router/index.ts:73）
    // **早于** `fetchUserInfo()`（:81）执行 ⇒ 缺陷写入的 null 当次不发作，
    // 要到下一次导航才把用户弹回客户列表。
    // 本 spec 初版只断言「刷新后不在客户列表」—— 那是**空断言**：
    // 即便选择已被抹成 null，当次导航仍正常，断言照样通过 ⇒ 对缺陷恒绿。
    //
    // 🔴 但必须如实标注：**反证未能让本用例转红**。把 auth.store 的覆盖条件
    // 改回缺陷写法（`!== undefined`）后，实测刷新后选择并未丢失（ls 仍为 2），
    // 本用例依然通过 ⇒ 它是「预期行为」的回归锁，**不是** store 那处改动的
    // 反证。store 改动本身正确（`null !== undefined` 客观为错），
    // 但其真实收益未获端到端证实，详见 SPEC-P123 §7。
    //
    // 这里仍保留本用例的价值：它锁住「刷新后仍可正常导航」这一用户可见行为，
    // 且顺带补上 P121 只测可见、不测可点的盲区。
    //
    // 因此这里必须真正点一次菜单，才算抓到原缺陷。
    const group = page.locator('.sidebar-menu .el-sub-menu__title', { hasText: '总账' }).first()
    if (!(await group.getAttribute('class'))?.includes('is-opened')) {
      await group.click({ timeout: 5000 }).catch(() => {})
      await page.waitForTimeout(500)
    }
    const item = page.locator('.sidebar-menu .el-menu-item', { hasText: '期初建账' }).first()
    await expect(item).toBeVisible()
    await item.click({ timeout: 8000 })
    await page.waitForTimeout(1500)

    expect(page.url()).toContain('/finance/beginning-balance')
  })
})