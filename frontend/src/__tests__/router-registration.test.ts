/**
 * @vitest-environment node
 *
 * 🔴 本文件是**源码扫描 + 路由表解析**双面守卫（P153 复盘产物）。
 *
 * <p><b>为什么需要它</b>：SPEC-P143 迁移工资路由时，把 smeSalaryRoutes 挂进了
 * {@code router/index.ts} 里一个**从未被引用的死变量** baseSmeRoutes——
 * vue-tsc 不报（语法合法、noUnusedLocals 未开）、vitest 组件测试不走路由表、
 * 后端 API 实测全 200 ⇒ 三道既有门禁全部放行，只有真浏览器访问才见 404。
 * 业务路由的**唯一生效挂载点**是 {@code sme-base.ts} 的 / 父路由 children。
 *
 * <p><b>锁死三面</b>：
 * <ol>
 *   <li>index.ts 必须原样展开 sme-base/agency 路由组，且**不许再出现死变量**——
 *       抓「路由组定义了但没挂进生效链」（本轮缺陷形态）；</li>
 *   <li>侧边栏每个菜单入口（canAccess/hasVisible 提取）必须 resolve 到真实路由，
 *       不许落进 NotFound catch-all —— 抓「菜单死链」（本轮 /salary/bill 404、
 *       上轮 /salary 迁移死链同族）；</li>
 *   <li>routes/*.ts 每个声明 path（含相对路径按父前缀拼接）必须 resolve 成功
 *       —— 抓「路由文件孤儿」。</li>
 * </ol>
 *
 * <p><b>反证</b>：从 sme-base.ts 摘掉 {@code ...smeSalaryRoutes}（保持 import 存在）⇒
 * 第 2、3 两面恰红；还原即绿。
 *
 * <p>⚠️ 不直接 import {@code @/router/index}：其顶层 createWebHistory 在 node
 * 环境炸 window（家法 {@code agency-role-gating.test.ts} 同因切 node 环境）。
 * 生效路由表在此等价重组为 [...smeBaseRoutes, ...agencyRoutes]，
 * 并由第 1 面静态断言钉住 index.ts 的组装与该重组一致。
 */
import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { createRouter, createMemoryHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import smeBaseRoutes from '@/router/routes/sme-base'
import agencyRoutes from '@/router/routes/agency'

const ROOT = resolve(__dirname, '../..')
const SIDEBAR = 'src/layouts/AppSidebar.vue'
const ROUTES_DIR = 'src/router/routes'
const INDEX = 'src/router/index.ts'

/** 与 index.ts 的 routes 等价（第 1 面静态断言钉住一致性） */
const routes = [...smeBaseRoutes, ...agencyRoutes] as RouteRecordRaw[]

/** NotFound catch-all 的 name（sme-base.ts 中定义）——resolve 命中它即视为未注册 */
const CATCH_ALL = 'NotFound'

function makeRouter() {
  return createRouter({ history: createMemoryHistory(), routes })
}

/** resolve 并判定「是否注册过」（命中 catch-all 即未注册） */
function isRegistered(router: ReturnType<typeof makeRouter>, path: string): boolean {
  const matched = router.resolve(path).matched
  if (matched.length === 0) return false
  return !matched.some((m) => m.name === CATCH_ALL)
}

/** 提取参数路径里的 :xxx 为占位，便于 resolve */
function concretePath(p: string): string {
  return p.replace(/:[^/]+/g, 'x')
}

describe('P153 复盘：路由注册与侧边栏入口一致性', () => {
  it('第 1 面：index.ts 原样展开 sme-base/agency，且无死变量（路由组不得定义而未挂载）', () => {
    const src = readFileSync(resolve(ROOT, INDEX), 'utf-8')
    expect(src).toContain('...smeBaseRoutes')
    expect(src).toContain('...agencyRoutes')
    // 死变量缺陷形态：定义了汇总数组却从未被展开（baseSmeRoutes 事件复发器）
    expect(src).not.toMatch(/const\s+\w+Routes[^=]*=\s*\[/)
  })

  it('自证：提取清单非空且含本轮缺陷路径 /salary/bill（防提取逻辑坏掉后恒绿）', () => {
    const src = readFileSync(resolve(ROOT, SIDEBAR), 'utf-8')
    const paths = extractSidebarPaths(src)
    expect(paths.length).toBeGreaterThan(20)
    expect(paths).toContain('/salary/bill')
  })

  it('第 2 面：侧边栏每个菜单入口都必须 resolve 到真实路由（不许死链 404）', () => {
    const src = readFileSync(resolve(ROOT, SIDEBAR), 'utf-8')
    const paths = extractSidebarPaths(src)
    const router = makeRouter()
    const dead = paths.filter((p) => !isRegistered(router, p))
    expect(dead, `侧边栏存在死链入口（看得见点不开 404）：${dead.join(', ')}`).toEqual([])
  })

  it('第 3 面：routes/*.ts 声明的每个 path（按父前缀拼接）都必须 resolve 成功', () => {
    const router = makeRouter()
    const files = readdirSync(resolve(ROOT, ROUTES_DIR)).filter((f) => f.endsWith('.ts'))
    expect(files.length).toBeGreaterThanOrEqual(8) // sme-base/business/tax/asset/salary/report/lab/agency

    const orphans: string[] = []
    for (const f of files) {
      const src = readFileSync(resolve(ROOT, ROUTES_DIR, f), 'utf-8')
      let prefix = '' // 相对 path 的父前缀
      // 按出现顺序扫描 path: '...'（顶层绝对路径同时充当后续相对路径的前缀）
      const re = /path:\s*'([^']+)'/g
      let m: RegExpExecArray | null
      while ((m = re.exec(src)) !== null) {
        const raw = m[1]
        // catch-all / 正则形态（/:pathMatch(.*)*）不是可 resolve 的普通路径，跳过
        if (raw.includes(':pathMatch') || raw.includes('(')) continue
        const p = concretePath(raw)
        if (p.startsWith('/')) {
          prefix = p
          if (!isRegistered(router, p)) orphans.push(`${f}: ${raw}`)
        } else {
          const full = prefix.replace(/\/$/, '') + '/' + p
          if (!isRegistered(router, full)) orphans.push(`${f}: ${raw} (→${full})`)
        }
      }
    }
    expect(orphans, `路由文件存在从未生效的孤儿 path：${orphans.join(', ')}`).toEqual([])
  })
})

/**
 * 从 AppSidebar.vue 源码提取全部菜单入口路径：
 * {@code canAccess('/x')} 与 {@code hasVisible(['/x', '/y'])} 中的字符串字面量。
 * 去掉查询串（如 /finance/voucher-setup?tab=type → /finance/voucher-setup）。
 */
function extractSidebarPaths(src: string): string[] {
  const out = new Set<string>()
  const re = /(?:canAccess|hasVisible)\((\[[^\]]*\]|'[^']*')\)/g
  let m: RegExpExecArray | null
  while ((m = re.exec(src)) !== null) {
    const lit = /'([^']+)'/g
    let s: RegExpExecArray | null
    while ((s = lit.exec(m[1])) !== null) {
      out.add(s[1].split('?')[0])
    }
  }
  return [...out]
}
