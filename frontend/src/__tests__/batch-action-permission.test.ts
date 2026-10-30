import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  actionTooltip,
  isNativelyDisabled,
  isPermissionLocked,
  type ActionGate,
} from '@/components/batch/batchActionPermission'
import type { BatchActionDef } from '@/composables/useBatchOperation'

/**
 * SPEC-P136 —— 批量操作权限门禁（AT-136-3 / AT-136-9）。
 *
 * <p>两件事：
 * <ol>
 *   <li><b>行为</b>：无权限 / 空选 / 状态不符 三种禁用原因互不混淆（纯函数直接断言，
 *       不必依赖 happy-dom 下取不到的 tooltip DOM）；</li>
 *   <li><b>防漂移（G-1/G-2）</b>：三个批量条页面的每个动作都必须声明 {@code permission}，
 *       且字面量必须真实存在于后端权限清单 —— 否则按钮会**静默永久禁用**，
 *       症状形似「正常没配权限」（P130/P131 同一机制的 UI 层再现）。</li>
 * </ol>
 */

const ROOT = resolve(__dirname, '../..')
/** frontend/src/__tests__ → 仓库根 */
const REPO_ROOT = resolve(ROOT, '..')

const PAGES = [
  'src/views/finance/voucher/VoucherList.vue',
  'src/views/tax/output-invoice/OutputInvoiceList.vue',
  'src/views/finance/bank-statement/BankStatementView.vue',
]

/** D-4 裁定：棘轮值 = 三个页面的动作数之和。只许随新增动作同步上调。 */
const EXPECTED_ACTION_COUNT = 12

/** 从源码里取 `const BATCH_ACTIONS ... [` 到配平的 `]` 的整段文本。
 *  🔴 声明行是 `const BATCH_ACTIONS: BatchActionDef[] = [`，其**类型注解**里也有 `[]`；
 *  从 `start` 开始找第一个 `[` 会配对到注解的 `]` ⇒ 提取出空块（本轮实测踩坑）。
 *  故取**声明行行内最后一个 `[`**（无注解时同样是数组字面量的 `[`）。 */
function batchActionsBlock(src: string): string {
  const start = src.indexOf('const BATCH_ACTIONS')
  expect(start).toBeGreaterThanOrEqual(0)
  const lineEnd = src.indexOf('\n', start)
  const declLine = src.slice(start, lineEnd === -1 ? undefined : lineEnd)
  const open = start + declLine.lastIndexOf('[')
  let depth = 0
  for (let k = open; k < src.length; k++) {
    if (src[k] === '[') depth++
    else if (src[k] === ']') {
      depth--
      if (depth === 0) return src.slice(open, k + 1)
    }
  }
  throw new Error('BATCH_ACTIONS 数组未闭合')
}

/** 取出块内每个 `{ ... }` 动作对象（动作对象不含嵌套大括号） */
function actionLiterals(block: string): string[] {
  return [...block.matchAll(/\{[^{}]*\}/g)].map((m) => m[0])
}

function readPage(rel: string): string {
  return readFileSync(resolve(ROOT, rel), 'utf-8')
}

function allActionLiterals(): string[] {
  return PAGES.flatMap((p) => actionLiterals(batchActionsBlock(readPage(p))))
}

function gate(overrides: Partial<ActionGate> & { action: BatchActionDef }): ActionGate {
  return {
    selectedCount: 1,
    maxCount: 100,
    statusOk: true,
    hasPermission: () => true,
    ...overrides,
  }
}

describe('SPEC-P136 批量操作权限门禁 — 纯函数判据', () => {
  const audit: BatchActionDef = { key: 'audit', label: '批量审核', permission: 'voucher:audit' }

  it('AT-136-1 无权限：视觉禁用但**不**原生 disabled（要能点出解释）', () => {
    const g = gate({ action: audit, hasPermission: () => false })
    expect(isPermissionLocked(audit, g.hasPermission)).toBe(true)
    // 关键：原生 disabled 会吞掉 click ⇒ 用户点不出弹窗，只剩 hover tooltip
    expect(isNativelyDisabled(g)).toBe(false)
  })

  it('AT-136-2 有权限 + 状态匹配：可点且提示为空', () => {
    const g = gate({ action: audit })
    expect(isPermissionLocked(audit, g.hasPermission)).toBe(false)
    expect(isNativelyDisabled(g)).toBe(false)
    expect(actionTooltip(g)).toBe('')
  })

  it('AT-136-3 tooltip 区分「无权限」与「状态不符」，且无权限优先', () => {
    const noPerm = gate({ action: audit, hasPermission: () => false, statusOk: false })
    expect(actionTooltip(noPerm)).toContain('权限')
    expect(actionTooltip(noPerm)).toContain('voucher:audit')

    const badStatus = gate({ action: audit, statusOk: false })
    expect(actionTooltip(badStatus)).toContain('不支持')
    expect(actionTooltip(badStatus)).not.toContain('权限')

    // 权限优先：权限与状态同时不满足时，先说权限（找管理员，而非找别的记录）
    expect(actionTooltip(noPerm)).not.toContain('不支持')
  })

  it('未声明 permission 的动作一律视为有权限（向后兼容）', () => {
    const legacy: BatchActionDef = { key: 'legacy', label: '旧动作' }
    const g = gate({ action: legacy, hasPermission: () => false })
    expect(isPermissionLocked(legacy, g.hasPermission)).toBe(false)
    expect(isNativelyDisabled(g)).toBe(false)
  })

  it('有权限但空选/超上限：原生 disabled + 说明（不弹权限窗）', () => {
    expect(actionTooltip(gate({ action: audit, selectedCount: 0 }))).toBe('请先勾选记录')
    expect(isNativelyDisabled(gate({ action: audit, selectedCount: 0 }))).toBe(true)
    expect(actionTooltip(gate({ action: audit, selectedCount: 101 }))).toBe('单次最多批量操作 100 条')
  })
})

describe('SPEC-P136 G-1 — 三个批量条动作全部声明 permission（棘轮）', () => {
  it('allBatchActionsDeclarePermission', () => {
    const literals = allActionLiterals()
    expect(literals).toHaveLength(EXPECTED_ACTION_COUNT)

    const missing = literals.filter((l) => !/\bpermission:\s*['"`]/.test(l))
    expect(missing).toEqual([])
  })
})

describe('SPEC-P136 G-2 — 前端 permission 字面量必须存在于后端权限清单', () => {
  it('permissionCodesExistInManifest', () => {
    const manifest = readFileSync(
      resolve(REPO_ROOT, 'backend/src/main/resources/permission-manifest.yml'),
      'utf-8',
    )
    const known = new Set(
      [...manifest.matchAll(/permission:\s*([A-Za-z0-9:_-]+)/g)].map((m) => m[1]),
    )
    expect(known.size).toBeGreaterThan(0)

    const used = allActionLiterals()
      .map((l) => l.match(/\bpermission:\s*['"`]([^'"`]+)['"`]/)?.[1])
      .filter((c): c is string => !!c)

    expect(used).toHaveLength(EXPECTED_ACTION_COUNT)

    const unknown = used.filter((c) => !known.has(c))
    expect(unknown).toEqual([])
  })
})
