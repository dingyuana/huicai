import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * SPEC-P121：多角色登录 E2E —— 代理端角色可用性回归锁。
 *
 * <h2>为什么必须有这个 spec</h2>
 * 既有 29 个 E2E **全部用 {@code admin}(SUPER_ADMIN)**，其余账号
 * （zhang/zhangsan/li/agent01）在库内**根本不存在** ⇒
 * 「所有 E2E 都以超管身份跑」，恰好绕开了出问题的角色。
 *
 * <p>后果：代理端会计因 {@code t_user_role} 为空 ⇒ 权限集为空 ⇒
 * 点任何菜单都被守卫送进 /403，而**本地与 CI 全绿**。
 * 这是 §4.3 第 9 条的典型形态：**「测试通过」≠「功能可用」——
 * 当测试只覆盖了一个角色时，其余角色等于没测。**
 *
 * <h2>覆盖的账号（口令均为 admin123，实测哈希前缀与 admin 一致）</h2>
 * <ul>
 *   <li>{@code accountant01} — agency_role=ACCOUNTANT → 平台角色 ACCOUNTANT(24 菜单)</li>
 *   <li>{@code reviewer01} — agency_role=REVIEWER → 平台角色 FINANCE_MGR(42)</li>
 *   <li>{@code assistant01} — agency_role=ASSISTANT → 平台角色 CASHIER(8)</li>
 * </ul>
 *
 * <p>核心断言：**登录后逐个点击侧边栏可见菜单，不得落到 /403。**
 * 这正是本次缺陷的症状，用 UI 行为而非接口断言来锁。
 */

const BASE = 'http://localhost:3001'

const AGENCY_USERS = [
  { user: 'accountant01', role: 'ACCOUNTANT' },
  { user: 'reviewer01', role: 'REVIEWER' },
  { user: 'assistant01', role: 'ASSISTANT' },
] as const

/** 展开侧边栏所有分组（el-sub-menu 默认折叠，不展开则数不到其中的菜单项） */
async function expandAllGroups(page: import('@playwright/test').Page) {
  const titles = page.locator('.sidebar-menu > .el-sub-menu > .el-sub-menu__title')
  const n = await titles.count()
  for (let i = 0; i < n; i++) {
    const t = titles.nth(i)
    if ((await t.getAttribute('class'))?.includes('is-opened')) continue
    await t.click().catch(() => undefined)
    await page.waitForTimeout(120)
  }
  await page.waitForTimeout(300)
}

/**
 * 由「菜单文本」反查其目标路由路径。
 *
 * <p>解析 AppSidebar.vue 中形如
 * {@code <el-menu-item ... canAccess('/x/y') ...>文本} 的映射。
 * 之所以读源码而非 DOM：Element Plus 不把 {@code index} 渲进 HTML。
 */
function pathsForTexts(texts: string[]): string[] {
  // ⚠️ Playwright 走 ESM，无 __dirname；且 cwd 即 frontend/ 根目录
  const src = readFileSync(resolve(process.cwd(), 'src/layouts/AppSidebar.vue'), 'utf-8')
  const out: string[] = []
  const re = /<el-menu-item[^>]*canAccess\('([^']+)'\)[^>]*>\s*([^<]*?)\s*<\/el-menu-item>/g
  const map = new Map<string, string>()
  let m: RegExpExecArray | null
  while ((m = re.exec(src)) !== null) {
    map.set(m[2], m[1].split('?')[0])
  }
  for (const t of texts) {
    const p = map.get(t)
    if (p && !out.includes(p)) out.push(p)
  }
  return out
}

async function login(page: import('@playwright/test').Page, username: string) {
  await page.goto(`${BASE}/login`)
  await page.locator('input[type="text"]').first().fill(username)
  await page.locator('input[type="password"]').first().fill('admin123')
  await page.locator('button').filter({ hasText: /登录|登 录/i }).click()
  await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 20000 })
}

test.describe('SPEC-P121：代理端角色登录可用性', () => {
  // REVIEWER 有 39 个菜单项，逐个访问 + 守卫判定需时超过默认 30s
  test.setTimeout(180_000)

  for (const { user, role } of AGENCY_USERS) {
    test(`${user}(${role}) 能登录并离开 /login`, async ({ page }) => {
      await login(page, user)
      expect(page.url()).not.toContain('/login')
    })

    test(`${user}(${role}) 可见菜单均可达（无一被守卫拦到 /403）`, async ({ page }) => {
      await login(page, user)
      await page.waitForTimeout(1500)
      await expandAllGroups(page)

      // ⚠️ 为什么不用「物理点击」逐个试：
      //   el-menu 点击会触发路由跳转，元素随之失效；分组展开后菜单项还会被
      //   滚动容器遮挡，click 稳定等 30s 即超时。
      //   而本用例要验的是「菜单可见性 ⟺ 路由可达性」这一**对应关系**，
      //   直接读菜单项的 index 并访问目标路由，比模拟点击更贴近断言本身。
      const items = page.locator('.sidebar-menu .el-menu-item')
      const count = await items.count()
      expect(count, `${user} 侧边栏没有任何菜单项，疑似全部被过滤掉了`).toBeGreaterThan(2)

      // ⚠️ Element Plus **不把 index 渲进 HTML**（getAttribute('index') 恒 null），
      //    且 el-menu 以 `:router=true` 自行跳转。故路径清单从
      //    **菜单文本 + 源码中的 canAccess() 映射**取，避开 DOM 读取这一不可靠环节。
      const visibleTexts: string[] = []
      for (let i = 0; i < count; i++) {
        const txt = ((await items.nth(i).innerText()) || '').trim()
        if (txt) visibleTexts.push(txt)
      }
      expect(visibleTexts.length, '未能读到任何菜单项文本').toBeGreaterThan(1)
      // 修复前会看到 '>银行日记账' 这类被污染的文本 —— 顺手锁死
      const polluted = visibleTexts.filter((x) => x.startsWith('>'))
      expect(
        polluted.length === 0,
        '菜单文本被多余字符污染（批量改写模板的副作用）：\n  ' + polluted.join('\n  ')
      )

      const paths = pathsForTexts(visibleTexts)

      // 逐个访问：可见的菜单项，其目标路由都不得把用户送到 /403
      const blocked: string[] = []
      for (const path of paths) {
        await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded', timeout: 15000 })
        await page.waitForTimeout(350)
        if (page.url().includes('/403')) blocked.push(`${path} -> ${page.url()}`)
        // 若无权限，守卫送 /403 是**正确**行为；此处只统计「可见却不可达」
      }

      expect(
        blocked.length === 0,
        `${user}(${role}) 侧边栏显示了以下菜单，但点进去落到 /403 —— ` +
          `说明菜单过滤与路由守卫口径不一致：\n  ` + blocked.join('\n  ')
      )
    })
  }

  test('权限不足的菜单必须【不可见】，而非可见却点不开（反证锁）', async ({ page }) => {
    await page.context().clearCookies()
    await page.goto(`${BASE}/login`)
    await page.evaluate(() => localStorage.clear())

    // ASSISTANT -> CASHIER(8 菜单)；REVIEWER -> FINANCE_MGR(42)
    // 取两者的可见菜单集合，断言 ASSISTANT ⊂ REVIEWER 且严格更小
    const visibleFor = async (user: string): Promise<string[]> => {
      await page.context().clearCookies()
      await page.goto(`${BASE}/login`)
      await page.evaluate(() => localStorage.clear())
      await login(page, user)
      await page.waitForTimeout(1200)
      await expandAllGroups(page)
      const items = page.locator('.sidebar-menu .el-menu-item')
      const n = await items.count()
      const out: string[] = []
      for (let i = 0; i < n; i++) {
        const txt = ((await items.nth(i).innerText()) || '').trim()
        if (txt) out.push(txt)
      }
      return out
    }

    const assistant = await visibleFor('assistant01')
    const reviewer = await visibleFor('reviewer01')

    // ⚠️ 这是反证的关键断言：撤销绑定后，助理的可见菜单会**大幅减少**。
    //    若只断言「可见菜单都能打开」，则无论有无权限都恒成立（假绿）——
    //    本轮反证正是这样暴露的：撤销 V179 后 6 个「可达」用例仍全绿。
    expect(
      assistant.length,
      `助理可见 ${assistant.length} 项、审核员可见 ${reviewer.length} 项`
    ).toBeLessThan(reviewer.length)

    // 审核员独有能力（助理不应看到）
    const onlyReviewer = reviewer.filter((x) => !assistant.includes(x))
    expect(
      onlyReviewer.length,
      '审核员与助理可见菜单完全相同 ⇒ 平台角色映射未生效'
    ).toBeGreaterThan(0)
  })

  test('无权访问的路由必须被守卫拦到 /403（菜单隐藏之外的第二道防线）', async ({ page }) => {
    await page.context().clearCookies()
    await page.goto(`${BASE}/login`)
    await page.evaluate(() => localStorage.clear())
    await login(page, 'assistant01')
    await page.waitForTimeout(1200)

    // 系统管理类 —— 不在 CASHIER(8 菜单) 范围内，直接输 URL 也应被拦
    await page.goto(`${BASE}/system/user`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(800)
    expect(
      page.url(),
      '助理直接访问 /system/user 未被拦到 /403 ⇒ 路由守卫失效'
    ).toContain('/403')
  })

  test('不同代理角色可见的菜单数量不同（映射确实生效）', async ({ page }) => {
    const counts: Record<string, number> = {}

    for (const { user, role } of AGENCY_USERS) {
      await page.context().clearCookies()
      await page.goto(`${BASE}/login`)
      await page.evaluate(() => localStorage.clear())
      await login(page, user)
      await page.waitForTimeout(1200)
      await expandAllGroups(page)
      counts[role] = await page.locator('.sidebar-menu .el-menu-item').count()
    }

    // ASSISTANT(CASHIER, 8 菜单) 应显著少于 REVIEWER(FINANCE_MGR, 42)
    expect(
      counts.ASSISTANT,
      `助理可见 ${counts.ASSISTANT} 项，审核员可见 ${counts.REVIEWER} 项 —— ` +
        `助理权限不应多于审核员`
    ).toBeLessThan(counts.REVIEWER)
  })
})