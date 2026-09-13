import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useBatchOperation, normalizeBatchResult } from '@/composables/useBatchOperation'

// Mock element-plus（P67 批量操作的二次确认 / 原因输入 / 消息提示）
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: {
    confirm: vi.fn().mockResolvedValue('confirm'),
    prompt: vi.fn().mockResolvedValue({ value: '驳回原因' }),
  },
}))

describe('normalizeBatchResult 三种后端契约归一化', () => {
  it('全有全无契约：raw 为 null/undefined 时视为全部成功', () => {
    expect(normalizeBatchResult(null, 3)).toEqual({ total: 3, successCount: 3, failures: [] })
    expect(normalizeBatchResult(undefined, 2)).toEqual({ total: 2, successCount: 2, failures: [] })
  })

  it('销项发票契约：{ success: number[], failure: [{id, reason}] }', () => {
    const raw = { success: [1, 2], failure: [{ id: 3, reason: '发票已审核', invoiceNo: 'OUT003' }] }
    const r = normalizeBatchResult(raw, 3)
    expect(r.successCount).toBe(2)
    expect(r.failures).toHaveLength(1)
    expect(r.failures[0]).toMatchObject({ id: 3, no: 'OUT003', reason: '发票已审核' })
  })

  it('银行流水契约：{ total, success: number, failed: [{id, reason}] }', () => {
    const raw = { total: 3, success: 1, failed: [{ id: 2, reason: '已生成单据' }, { id: 3, reason: 'X' }] }
    const r = normalizeBatchResult(raw, 3)
    expect(r.total).toBe(3)
    expect(r.successCount).toBe(1)
    expect(r.failures).toHaveLength(2)
  })

  it('失败原因缺省时回退为「未知原因」', () => {
    const r = normalizeBatchResult({ success: [], failure: [{ id: 9 }] }, 1)
    expect(r.failures[0].reason).toBe('未知原因')
  })
})

describe('useBatchOperation.run 门槛与交互', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function setup() {
    const refresh = vi.fn().mockResolvedValue(undefined)
    const clearer = vi.fn()
    const op = useBatchOperation({ refresh, clearer })
    return { op, refresh, clearer }
  }

  it('空选时 warning 且不执行 executor', async () => {
    const { op } = setup()
    const executor = vi.fn()
    await op.run({ key: 'submit', label: '提交' }, executor)
    expect(ElMessage.warning).toHaveBeenCalledWith('请先勾选记录')
    expect(executor).not.toHaveBeenCalled()
  })

  it('超过 100 条上限时拦截（默认上限）', async () => {
    const { op } = setup()
    op.onSelectionChange(Array.from({ length: 101 }, (_, i) => ({ id: i + 1 })))
    const executor = vi.fn()
    await op.run({ key: 'audit', label: '审核' }, executor)
    expect(ElMessage.warning).toHaveBeenCalledWith('单次最多批量操作 100 条')
    expect(executor).not.toHaveBeenCalled()
  })

  it('自定义 maxCount 生效', async () => {
    const { op } = setup()
    op.onSelectionChange(Array.from({ length: 11 }, (_, i) => ({ id: i + 1 })))
    const executor = vi.fn()
    await op.run({ key: 'x', label: 'X', maxCount: 10 }, executor)
    expect(ElMessage.warning).toHaveBeenCalledWith('单次最多批量操作 10 条')
    expect(executor).not.toHaveBeenCalled()
  })

  it('needConfirm：用户取消确认则不执行', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce(new Error('cancel'))
    const { op } = setup()
    op.onSelectionChange([{ id: 1 }, { id: 2 }])
    const executor = vi.fn()
    await op.run({ key: 'post', label: '记账', needConfirm: true }, executor)
    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(executor).not.toHaveBeenCalled()
  })

  it('needReason：用户关闭 prompt（无原因）则不执行', async () => {
    vi.mocked(ElMessageBox.prompt).mockRejectedValueOnce(new Error('close'))
    const { op } = setup()
    op.onSelectionChange([{ id: 1 }])
    const executor = vi.fn()
    await op.run({ key: 'reject', label: '驳回', needReason: true }, executor)
    expect(executor).not.toHaveBeenCalled()
  })

  it('全部成功：携带全部 ID 调用 executor，归一化结果弹窗并清空选择+refresh', async () => {
    const { op, refresh, clearer } = setup()
    op.onSelectionChange([{ id: 10 }, { id: 20 }])
    const executor = vi.fn().mockResolvedValue(undefined) // 全有全无契约
    await op.run({ key: 'submit', label: '提交' }, executor)
    expect(executor).toHaveBeenCalledWith([10, 20], '')
    expect(op.result.value).toEqual({ total: 2, successCount: 2, failures: [] })
    expect(op.resultVisible.value).toBe(true)
    expect(ElMessage.success).toHaveBeenCalled()
    expect(op.selectedRows.value).toHaveLength(0)
    expect(clearer).toHaveBeenCalled()
    expect(refresh).toHaveBeenCalled()
    expect(op.running.value).toBe(false)
  })

  it('部分失败：返回销项契约时弹 warning 并保留失败明细', async () => {
    const { op } = setup()
    op.onSelectionChange([{ id: 1 }, { id: 2 }])
    const executor = vi
      .fn()
      .mockResolvedValue({ success: [1], failure: [{ id: 2, reason: '状态不符' }] })
    await op.run({ key: 'audit', label: '审核' }, executor)
    expect(op.result.value?.failures).toHaveLength(1)
    expect(ElMessage.warning).toHaveBeenCalled()
  })

  it('executor 整批抛错（全有全无）：走 error 提示且不清空选择', async () => {
    const { op, clearer } = setup()
    op.onSelectionChange([{ id: 1 }])
    const executor = vi.fn().mockRejectedValue(new Error('整批失败'))
    await op.run({ key: 'submit', label: '提交' }, executor)
    expect(ElMessage.error).toHaveBeenCalledWith('整批失败')
    expect(clearer).not.toHaveBeenCalled()
    expect(op.running.value).toBe(false)
  })
})
