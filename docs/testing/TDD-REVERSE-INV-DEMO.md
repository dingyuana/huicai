/**
 * TDD 实践示例：OutputInvoiceStateMachineService.reverseInvoice() 的 remark 格式验证
 * 
 * 本文件用于演示 TDD（Test-Driven Development）开发流程：Red → Green → Refactor
 * 不应提交到生产代码库，仅作为学习材料。实际测试应加入 OutputInvoiceStateMachineServiceImplTest。
 * 
 * @author Hermes (TDD Guide)
 */
package com.huicai.sme.tax.service.impl;

import com.huicai.sme.tax.constant.InvoiceStatus;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Demo: Test-first development of reverseInvoice remark formatting.
 * 
 * Scenario: When an invoice already has a remark, after reversal the new remark should be
 * "existingRemark | [userId] reason"
 */
class ReverseInvoiceRemarkTDDDemo {

    private OutputInvoiceStateMachineImpl service;
    private Long USER_ID = 123L;

    @BeforeEach
    void setup() {
        // 初始化 mock 服务（简化版，实际需用 Mockito 正确 mock 依赖）
        // service = new OutputInvoiceStateMachineImpl(mocks);
    }

    // =========================================================================
    // PHASE 1: RED — 先写测试，即使当前实现没有这行逻辑，测试会失败
    // =========================================================================

    @Test
    @DisplayName("reverseInvoice_备注格式_原有备注时追加新原因|userId")
    void reverseInvoice_existingRemark_formatsCorrectly() {
        // GIVEN — 准备一条已有备注的发票
        OutputInvoiceEntity original = new OutputInvoiceEntity();
        original.setId(1L);
        original.setStatus(InvoiceStatus.CONFIRMED);
        original.setRemark("原始备注内容"); // 预先存在备注
        original.setInvoiceNo("INV-001");

        // 当调用 reverseInvoice 时...
        // WHEN: service.reverseInvoice(1L, USER_ID, "金额更正");

        // THEN — 期望新红冲发票的备注格式为："原始备注内容 | [123] 金额更正"
        // But wait — we haven't implemented this yet, so this test will FAIL (RED).
        
        // TODO: Implement the assertion AFTER writing the test first.
        // Assert.fail("IMPLEMENT IN GREEN PHASE");
    }

    // =========================================================================
    // PHASE 2: GREEN — 写最少代码让测试通过，不考虑完美设计
    // =========================================================================

    @Test
    @DisplayName("reverseInvoice_备注格式_无原有备注时只记新原因|userId")
    void reverseInvoice_noExistingRemark_setsJustReason() {
        // GIVEN — 准备一条无备注的发票
        OutputInvoiceEntity original = new OutputInvoiceEntity();
        original.setId(1L);
        original.setStatus(InvoiceStatus.CONFIRMED);
        original.setInvoiceNo("INV-001");
        // original.setRemark(null); // 默认 null

        // WHEN: Call reverseInvoice (implementation added in Phase 1)

        // THEN — 新发票的备注应为 "[123] 金额更正"（无前缀）
        // Assert.assertEquals("[123] 金额更正", redInvoice.getRemark());
        // This will pass after implementing the minimal fix.
    }

    // =========================================================================
    // PHASE 3: REFACTOR — 提炼工具方法，消除重复逻辑，保持测试全绿
    // =========================================================================

    @Test
    @DisplayName("reverseInvoice_备注格式_空原备注时正确处理")
    void reverseInvoice_emptyOriginalRemarkHandlesCorrectly() {
        // GIVEN — 原发票备注为空字符串
        OutputInvoiceEntity original = new OutputInvoiceEntity();
        original.setId(1L);
        original.setRemark(""); // empty string, not null
        original.setStatus(InvoiceStatus.CONFIRMED);

        // WHEN & THEN — 应生成 "[123] 金额更正"（不是 "| [123] 金额更正"）
        // Assert.assertEquals("[123] 金额更正", redInvoice.getRemark());
    }

    /**
     * 📝 TDD 实践说明：
     * 
     * 1. RED 阶段：先写出测试（包含断言），哪怕需要暂时用 Assert.fail() 或注释掉实现。
     *    - 确保测试能编译通过
     *    - 测试应表达明确的行为预期
     *    - 此时实现可能不存在或不正确，测试会失败
     * 
     * 2. GREEN 阶段：修改实现让测试通过，只改最小必要代码
     *    - 不要优化、不要加新功能
     *    - 目标是让红色的测试变绿
     * 
     * 3. REFACTOR 阶段：在测试全绿的前提下重构代码
     *    - 提取公共方法（如 appendReason）
     *    - 消除重复条件判断（null vs empty）
     *    - 重命名变量提高可读性
     *    - ⚠️ 任何时候如果测试变红，立即回退到上一个绿色状态
     * 
     * 关键原则：**测试先行，实现跟随；小步快跑，频繁验证**
     */
}
