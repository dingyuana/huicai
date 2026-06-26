/**
 * 前端状态机测试辅助工具.
 *
 * <p>对应后端 {@code com.huicai.common.test.StateMachineTestHelper} 的 TypeScript 版本，
 * 提供前端特有的"正向断言 + 负向断言"模式。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * import { StateMachineTestHelper } from '@/__tests__/helper'
 * import * as taxApi from '@/api/modules/tax'
 *
 * const mockRequest = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn() }))
 * vi.mock('@/api/request', () => ({ default: mockRequest }))
 *
 * test('confirmOutputInvoice 不应误调 markVouchered', async () => {
 *   mockRequest.post.mockResolvedValue(undefined)
 * 
 *   await taxApi.confirmOutputInvoice(1)
 *
 *   // 正向断言：正确的端点被调用了
 *   StateMachineTestHelper.assertApiCalled(mockRequest.post, '/tax/output-invoices/1/confirm')
 *   // 负向断言：不应该调用的端点没有被调用
 *   StateMachineTestHelper.assertApiNotCalled(mockRequest.post, '/mark-vouchered')
 * })
 * }</pre>
 */

export class StateMachineTestHelper {

  // ===================== 负向断言系列 =====================

  /**
   * 断言某个 spy 未被包含某关键字的参数调用.
   * 对应后端 verify(mapper, never()).insert(any()).
   *
   * @param spy 被 vi.spyOn 或 vi.fn() 包装的函数
   * @param urlPart URL 中的关键字，断言 spy 从未收到包含此关键字的调用
   *
   * @example
   * // 检测 confirm 没有误调 mark-vouchered 端点
   * StateMachineTestHelper.assertApiNotCalled(mockRequest.post, 'mark-vouchered')
   */
  static assertApiNotCalled(spy: any, urlPart: string): void {
    const calls: any[][] = spy.mock?.calls ?? []
    for (const call of calls) {
      const url = String(call[0] ?? '')
      if (url.includes(urlPart)) {
        throw new Error(
          `❌ 负向断言失败：spy 被调用了包含「${urlPart}」的参数\n` +
          `   完整调用: ${url}\n` +
          `   所有调用: ${calls.map((c: any[]) => String(c[0])).join(', ')}`
        )
      }
    }
  }

  /**
   * 断言指定 API 函数从未被调用.
   */
  static assertFunctionNotCalled(fn: any): void {
    if (fn.mock?.calls && fn.mock.calls.length > 0) {
      throw new Error(
        `❌ 负向断言失败：函数被调用了 ${fn.mock.calls.length} 次，预期从未调用`
      )
    }
  }

  /**
   * 断言 DOM 中不存在匹配选择器的元素.
   * 对应后端 verify(mapper, never()).insert(any()).
   *
   * @example
   * // CONFIRMED 状态下不应出现"审核通过"按钮
   * StateMachineTestHelper.assertElementNotExists(wrapper, '[data-action="confirm"]')
   */
  static assertElementNotExists(
    wrapper: { find: (selector: string) => { exists: () => boolean } },
    selector: string
  ): void {
    const exists = wrapper.find(selector).exists()
    if (exists) {
      throw new Error(`❌ 负向断言失败：元素「${selector}」存在，预期不应存在`)
    }
  }

  // ===================== 正向断言系列 =====================

  /**
   * 断言某个 API 被调用了指定的端点.
   */
  static assertApiCalled(spy: any, expectedUrl: string): void {
    const calls: any[][] = spy.mock?.calls ?? []
    const matched = calls.some((call: any[]) => {
      const url = String(call[0] ?? '')
      return url === expectedUrl || url.includes(expectedUrl)
    })
    if (!matched) {
      throw new Error(
        `❌ 正向断言失败：未找到调用「${expectedUrl}」\n` +
        `   实际调用: ${calls.map((c: any[]) => String(c[0])).join(', ') || '（无调用）'}`
      )
    }
  }

  /**
   * 断言组件的 data-test 属性值符合预期.
   */
  static assertTestAttribute(
    wrapper: { find: (selector: string) => { attributes: (attr: string) => string | undefined } | null },
    selector: string,
    expected: string
  ): void {
    const el = wrapper.find(selector)
    if (!el) {
      throw new Error(`❌ 正向断言失败：元素「${selector}」不存在`)
    }
    const actual = el.attributes('data-status')
    if (actual !== expected) {
      throw new Error(
        `❌ 正向断言失败：data-status 期望「${expected}」，实际「${actual}」`
      )
    }
  }

  // ===================== 工厂方法 =====================

  /**
   * 生成状态机接口的 mock 响应数据.
   */
  static mockInvoice(id: number, status: string, props?: Record<string, any>): Record<string, any> {
    return {
      id,
      status,
      invoiceNo: `INV-${String(id).padStart(5, '0')}`,
      customerName: '测试客户',
      amount: 1000,
      taxAmount: 130,
      totalAmount: 1130,
      invoiceDate: '2026-06-01',
      period: '202606',
      docId: null,
      voucherId: null,
      ...props,
    }
  }

  /**
   * 快速检查按钮是否可见且 text 匹配.
   */
  static buttonVisible(
    wrapper: { findComponent: (selector: string) => { text: () => string; isVisible: () => boolean } | null },
    buttonSelector: string,
    expectedText: string
  ): boolean {
    const btn = wrapper.findComponent(buttonSelector)
    if (!btn) return false
    return btn.isVisible() && btn.text().includes(expectedText)
  }
}
