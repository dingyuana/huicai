package com.huicai.module.xxx.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.xxx.entity.XxxEntity;
import com.huicai.module.xxx.mapper.XxxMapper;
import com.huicai.module.xxx.service.XxxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XXX 业务流程 E2E 测试模板
 * 
 * 说明：
 * 1. 跨模块全链路流程验证
 * 2. 使用真实数据库（Testcontainers）
 * 3. 事务自动回滚，测试数据互不影响
 * 4. 可以发现的问题：
 *    - 跨模块状态流转错误
 *    - 数据一致性问题（发票金额 != 应收金额 != 凭证金额）
 *    - 外键关联错误
 *    - 审计字段填充不一致
 * 
 * 使用方法：
 * 1. 替换 XXX 为实际业务流程名（如 SalesFlow、PurchaseFlow、ExpenseFlow）
 * 2. 根据实际业务流程补充测试步骤
 * 3. 验证每个环节的数据一致性
 * 
 * 典型业务流程示例：
 * - 销售流程：创建销售发票 → 审核 → 生成应收单 → 收款核销 → 生成凭证
 * - 采购流程：创建采购发票 → 审核 → 生成应付单 → 付款核销 → 生成凭证
 * - 费用流程：创建费用报销单 → 审核 → 生成付款单 → 支付 → 生成凭证
 */
@SpringBootTest
@Testcontainers
@Transactional
public class XxxFlowE2ETest extends AbstractMapperTest {

    @Autowired
    private XxxService xxxService;

    @Autowired
    private XxxMapper xxxMapper;

    // 注入其他需要的 Mapper/Service
    // @Autowired
    // private ReceivableMapper receivableMapper;
    // 
    // @Autowired
    // private VoucherMapper voucherMapper;

    /**
     * 测试前准备：预置基础数据
     * 客户、科目、部门等基础数据统一在这里准备
     */
    @BeforeEach
    void setupBaseData() {
        // TODO: 根据业务需要预置基础数据
        // 示例：
        // - 创建客户
        // - 创建科目
        // - 创建部门
        // - 创建员工
    }

    // ========================================================================
    // 场景 1：完整正向流程
    // ========================================================================

    /**
     * 场景 1：完整正向流程
     * 步骤：创建 → 审核 → 后续操作 → 完成
     * 验证：每个环节状态正确，数据一致
     */
    @Test
    void fullFlow_shouldCompleteSuccessfully() {
        // ====================================================================
        // 步骤 1：创建单据
        // ====================================================================
        XxxEntity entity = new XxxEntity();
        entity.setCode("FLOW-TEST-001");
        entity.setName("E2E流程测试-完整流程");
        entity.setAmount(new BigDecimal("5000.00"));
        entity.setTaxAmount(new BigDecimal("650.00"));
        entity.setTotalAmount(new BigDecimal("5650.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        // 验证：单据已创建
        assertNotNull(entity.getId());
        assertEquals("PENDING_CONFIRM", entity.getStatus());

        Long xxxId = entity.getId();

        // ====================================================================
        // 步骤 2：审核单据
        // ====================================================================
        boolean confirmed = xxxService.confirm(xxxId, 2L);  // 2L = 审核人ID
        assertTrue(confirmed);

        // 验证：状态已更新
        XxxEntity confirmedEntity = xxxMapper.selectById(xxxId);
        assertEquals("CONFIRMED", confirmedEntity.getStatus());
        assertEquals(2L, confirmedEntity.getAuditedBy());
        assertNotNull(confirmedEntity.getAuditedAt());

        // ====================================================================
        // 步骤 3：验证自动生成的下游单据（如应收单、应付单）
        // ====================================================================
        // TODO: 根据实际业务验证
        // 示例：
        // ReceivableEntity receivable = receivableMapper.selectByDocId(xxxId);
        // assertNotNull(receivable);
        // assertEquals(xxxId, receivable.getDocId());
        // assertEquals(0, new BigDecimal("5650.00").compareTo(receivable.getAmount()));

        // ====================================================================
        // 步骤 4：执行后续操作（如核销、生凭证）
        // ====================================================================
        // TODO: 根据实际业务补充

        // ====================================================================
        // 最终验证：全链路数据一致性
        // ====================================================================
        // 示例：
        // 1. 发票金额 = 应收金额
        // 2. 应收金额 = 凭证借贷方金额
        // 3. 关联关系正确
        // 4. 审计字段完整
    }

    // ========================================================================
    // 场景 2：状态流转边界 - 重复操作
    // ========================================================================

    /**
     * 场景 2：重复审核
     * 验证：已审核的单据不能再次审核
     */
    @Test
    void confirmTwice_shouldFail() {
        // 创建并审核
        XxxEntity entity = new XxxEntity();
        entity.setCode("FLOW-TEST-002");
        entity.setName("E2E流程测试-重复审核");
        entity.setAmount(new BigDecimal("1000.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        xxxService.confirm(entity.getId(), 2L);

        // 尝试第二次审核
        boolean secondConfirm = xxxService.confirm(entity.getId(), 2L);

        // 验证：第二次审核失败
        assertFalse(secondConfirm);
    }

    // ========================================================================
    // 场景 3：状态流转边界 - 逆向操作
    // ========================================================================

    /**
     * 场景 3：取消已审核的单据
     * 验证：取消后状态正确回退
     */
    @Test
    void cancelConfirmed_shouldRollbackStatus() {
        // 创建并审核
        XxxEntity entity = new XxxEntity();
        entity.setCode("FLOW-TEST-003");
        entity.setName("E2E流程测试-取消审核");
        entity.setAmount(new BigDecimal("1000.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        xxxService.confirm(entity.getId(), 2L);

        // 取消审核
        boolean cancelled = xxxService.cancel(entity.getId(), 3L);
        assertTrue(cancelled);

        // 验证：状态回退
        XxxEntity cancelledEntity = xxxMapper.selectById(entity.getId());
        assertEquals("CANCELLED", cancelledEntity.getStatus());

        // TODO: 验证下游数据也被正确回滚（如应收单作废、凭证删除）
    }

    // ========================================================================
    // 场景 4：数据一致性边界
    // ========================================================================

    /**
     * 场景 4：金额精度验证
     * 验证：DECIMAL 类型没有精度丢失
     */
    @Test
    void amountPrecision_shouldNotLosePrecision() {
        // 使用高精度金额
        BigDecimal preciseAmount = new BigDecimal("1234567.89");

        XxxEntity entity = new XxxEntity();
        entity.setCode("FLOW-TEST-004");
        entity.setName("E2E流程测试-精度验证");
        entity.setAmount(preciseAmount);
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        // 查询并验证精度
        XxxEntity found = xxxMapper.selectById(entity.getId());
        assertEquals(0, preciseAmount.compareTo(found.getAmount()));
    }

    // ========================================================================
    // 场景 5：并发场景边界
    // ========================================================================

    /**
     * 场景 5：并发审核
     * 验证：并发操作不会产生重复的下游单据
     * 
     * 注意：此测试需要单独的并发测试基类，不放在普通 E2E 中
     * 参考：ConcurrencyLoadTest.java
     */
    // @Test
    // void concurrentConfirm_shouldNotCreateDuplicate() {
    //     // 使用线程池模拟并发请求
    //     // 验证最终只有一个下游单据被创建
    // }
}
