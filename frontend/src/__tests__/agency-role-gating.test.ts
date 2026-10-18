import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * SPEC-P120：代理端入口的角色标注一致性。
 *
 * <p><b>为什么需要它</b>：本 SPEC 修复前，且侧边栏
 * {@code AppSidebar.vue} 与路由 {@code routes/agency.ts} 各自独立维护
 * 「哪些入口限经理」—— 侧边栏漏 2 个（批量操作、服务进度），路由漏同样 2 个。
 * 两处不同步的后果：菜单看得见、点进去吃 403；或菜单看不见但 URL 直接可达。
 *
 * <p>本测试把两侧的口径钉成同一个集合，任一侧漏标即红。
 */

const ROOT = resolve(__dirname, '../..')

/**
 * 取包含 {@code idx} 的那个路由对象完整文本（按大括号配平）。
 *
 * <p>不能用固定长度窗口 —— 路由对象内常有多行注释，
 * 会把 meta 挤出窗口造成误报（本测试初版即踩此坑）。
 */
function routeBlock(src: string, idx: number): string {
  const open = src.lastIndexOf('{', idx)
  let depth = 0
  for (let k = open; k < src.length; k++) {
    if (src[k] === '{') depth++
    else if (src[k] === '}') {
      depth--
      if (depth <= 0) return src.slice(open, k + 1)
    }
  }
  return src.slice(open)
}

const SIDEBAR = 'src/layouts/AppSidebar.vue'
const ROUTES = 'src/router/routes/agency.ts'

/** 仅代理经理可访问的页面 */
const ADMIN_ONLY = [
  { path: '/agency/batch-operation', label: '批量操作', routeName: 'AgencyBatchOperation' },
  { path: '/agency/accountant-list', label: '会计管理', routeName: 'AgencyAccountantList' },
  { path: '/agency/assignment-manage', label: '客户分配', routeName: 'AgencyAssignmentManage' },
  { path: '/agency/dashboard', label: '主管仪表盘', routeName: 'AgencyDashboard' },
  {
    path: '/agency/service-progress',
    label: '服务进度与工作量',
    routeName: 'AgencyServiceProgress',
  },
]

/** 会计也可访问（后端按角色收敛可见集，SPEC-P120 的 page 修复） */
const OPEN_TO_ACCOUNTANT = [
  { path: '/agency/enterprise-list', label: '客户列表', routeName: 'AgencyEnterpriseList' },
]

describe('SPEC-P120：代理端入口角色标注一致性', () => {
  const sidebar = readFileSync(resolve(ROOT, SIDEBAR), 'utf-8')
  const routes = readFileSync(resolve(ROOT, ROUTES), 'utf-8')

  for (const item of ADMIN_ONLY) {
    it(`侧边栏「${item.label}」必须仅对 AGENCY_ADMIN 显示`, () => {
      // 形如：<el-menu-item v-if="authStore.isAgencyAdmin" index="/agency/xxx">
      const re = new RegExp(
        `el-menu-item[^>]*index="${item.path}"[^>]*>`
      )
      expect(re.test(sidebar), `侧边栏应包含 ${item.path}`).toBe(true)

      const guarded = new RegExp(
        `el-menu-item[^>]*v-if="authStore\\.isAgencyAdmin"[^>]*index="${item.path}"`
          + `|el-menu-item[^>]*index="${item.path}"[^>]*v-if="authStore\\.isAgencyAdmin"`
      )
      expect(
        guarded.test(sidebar),
        `「${item.label}」(${item.path}) 未对 isAgencyAdmin 隐藏 —— 会计会在侧边栏看到越权入口`
      ).toBe(true)
    })

    it(`路由「${item.label}」必须带 meta.agencyRole=AGENCY_ADMIN`, () => {
      const start = routes.indexOf(`name: '${item.routeName}'`)
      expect(start, `未找到路由 ${item.routeName}`).toBeGreaterThan(0)

      const block = routeBlock(routes, start)
      expect(
        block.includes("agencyRole: 'AGENCY_ADMIN'"),
        `路由 ${item.routeName} 缺少 meta.agencyRole —— 直接输入 URL 可绕过侧边栏`
      ).toBe(true)
    })
  }

  for (const item of OPEN_TO_ACCOUNTANT) {
    it(`侧边栏「${item.label}」必须存在且不限于经理角色（后端按角色收敛可见集）`, () => {
      // SPEC-P121 更新：本项此前断言「无条件显示」，但侧边栏已改为按
      // `canAccess` 过滤（P121 消除「看得见却点不开」）。
      // ⇒ 断言改为：菜单项存在，且**没有** isAgencyAdmin 限制。
      const exists = sidebar.includes(`index="${item.path}"`)
      expect(exists, `侧边栏应包含 ${item.path}`).toBe(true)

      const guardedByAdmin = new RegExp(
        `el-menu-item[^>]*v-if="authStore\\.isAgencyAdmin"[^>]*index="${item.path}"` +
          `|el-menu-item[^>]*index="${item.path}"[^>]*v-if="authStore\\.isAgencyAdmin"`
      )
      expect(
        guardedByAdmin.test(sidebar),
        `「${item.label}」被限定为 isAgencyAdmin —— 该页对所有代理角色可见（后端收敛可见集）`
      ).toBe(false)

      // 且必须有权限过滤（与路由守卫同口径），否则回归成「看得见点不开」
      expect(
        sidebar.includes(`canAccess('${item.path}')`),
        `「${item.label}」缺少 canAccess 过滤 ⇒ 会计会看到却点不开（SPEC-P121）`
      ).toBe(true)
    })

    it(`路由「${item.label}」不得带 agencyRole 限制`, () => {
      const start = routes.indexOf(`name: '${item.routeName}'`)
      expect(start).toBeGreaterThan(0)
      const block = routeBlock(routes, start)
      expect(block.includes('agencyRole')).toBe(false)
    })
  }

  it('角色守卫必须早于「是否已选企业」门槛', () => {
    const index = readFileSync(resolve(ROOT, 'src/router/index.ts'), 'utf-8')
    const roleGuard = index.indexOf('代理角色路由守卫')
    const enterpriseGate = index.indexOf('检查是否已选择企业')
    expect(roleGuard, '未找到代理角色守卫').toBeGreaterThan(0)
    expect(enterpriseGate, '未找到企业选择门槛').toBeGreaterThan(0)
    expect(
      roleGuard,
      '角色守卫晚于企业门槛 ⇒ 会计是否被正确拒绝会取决于「是否选了企业」'
    ).toBeLessThan(enterpriseGate)
  })

  // ── SPEC-P121：菜单可见性必须与路由守卫同口径 ──

  it('侧边栏菜单项必须按 canAccess 过滤（消除「看得见却点不开」）', () => {
    // 取所有 el-menu-item，断言每一个都带 canAccess
    const items = sidebar.match(/<el-menu-item[^>]*index="[^"]+"[^>]*>/g) || []
    expect(items.length, '未解析到任何菜单项').toBeGreaterThan(0)

    const unguarded = items.filter((t) => !t.includes('canAccess('))
    expect(
      unguarded.length === 0,
      `以下菜单项没有权限过滤，代理端会计会看到却点不开（SPEC-P121）：\n  ` +
        unguarded.map((t) => t.replace(/\s+/g, ' ')).join('\n  ')
    )
  })

  it('canAccess 必须从路由表反查权限码，不得在菜单里手写字符串', () => {
    const store = readFileSync(resolve(ROOT, 'src/stores/auth.store.ts'), 'utf-8')
    expect(store).toContain('canAccess')
    // 手写权限码会与路由脱节（改路由忘改菜单 = 又一个恒绿缺陷）
    expect(
      /getRoutes\(\)\s*\.find/.test(store),
      'canAccess 未从路由表反查 meta.permission —— 菜单里手写权限码必然与路由脱节'
    )
  })

  it('分组菜单必须在无可见子项时整体隐藏（不留空壳）', () => {
    expect(sidebar, '缺少 hasVisible 辅助函数').toContain('hasVisible')
    const groups = sidebar.match(/<el-sub-menu[^>]*>/g) || []
    const noGuard = groups.filter(
      (g) =>
        !g.includes('v-if') &&
        !g.includes('isSuperAdmin') &&
        !g.includes('isAgency') &&
        !g.includes('hasVisible')
    )
    expect(
      noGuard.length === 0,
      '以下分组无任何显示条件，且未做空壳过滤：\n  ' + noGuard.join('\n  ')
    )
  })
})
