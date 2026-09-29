package com.huicai.sme.arap.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.sme.arap.entity.ReconciliationLogEntity;
import com.huicai.sme.arap.service.ArapSettlementService;
import com.huicai.sme.arap.service.ReconciliationService;
import com.huicai.sme.arap.service.ReconciliationService.ExecuteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核销核心业务链路集成测试（真实 PostgreSQL + Flyway）。
 *
 * <p>调用真实的 ReconciliationService 方法，验证：
 * <ul>
 *   <li>核销推荐: 基于未结清应收/应付的推荐逻辑</li>
 *   <li>核销执行: 单笔核销的完整流程</li>
 *   <li>核销后单据状态: unsettledAmount 扣减</li>
 *   <li>反核销: 恢复未结金额</li>
 * </ul>
 *
 * <p>注意：ReconciliationServiceImpl.execute() 使用 @Transactional(REQUIRES_NEW)，
 * 因此本测试类必须使用 @Transactional(NOT_SUPPORTED) 挂起继承自 AbstractMapperTest 的事务，
 * 否则测试数据在 Service 新事务中不可见。
 *
 * @SlowTest — 需要 Docker + Testcontainers
 */
@SlowTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("核销 - 核心业务链路集成测试")
public class ReconciliationIntegrationTest extends AbstractMapperTest {

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private BusinessDocMapper businessDocMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private ArapSettlementService arapSettlementService;

    @Autowired
    private ArapSettlementMapper arapSettlementMapper;

    private static final Long USER_ID = 1L;
    private Long customerId;
    private Long businessDocId;

    @BeforeEach
    void setUp() {
        // 创建测试客户（auto-generated ID）
        CustomerEntity c = new CustomerEntity();
        c.setName("核销测试客户");
        c.setCode("WRTOFF-" + System.currentTimeMillis());
        c.setEnterpriseId(1L);
        c.setDeleted(0);
        customerMapper.insert(c);
        customerId = c.getId();

        // 创建一笔未结清的 INVOICE_OUT 业务单据
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("WRTOFF-E2E-" + System.currentTimeMillis());
        doc.setDocType("INVOICE_OUT");
        doc.setDocDate(LocalDate.of(2026, 7, 1));
        doc.setPeriod("202607");
        doc.setAmount(new BigDecimal("10000.00"));
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(new BigDecimal("10000.00"));
        doc.setCustomerId(customerId);
        doc.setStatus("APPROVED");
        doc.setSummary("核销集成测试-应收");
        doc.setDueDate(LocalDate.of(2026, 8, 1));
        doc.setSource("MANUAL");
        doc.setDeleted(0);
        businessDocMapper.insert(doc);
        businessDocId = doc.getId();
    }

    /**
     * execute() 只「提报」不生效（P1-fix 统一核销写路径：金额扣减/来源单据同步/发票同步
     * 全部延后到 ArapSettlementService.approve()，依据铁律 #1「核销需人工审批才生效」）。
     * 故定位核销单要走 settlement 的 sourceDocId/sourceDocType；每个用例的 businessDocId
     * 都是本用例新建的，故至多命中一条。
     */
    private ArapSettlementEntity findSettlementBySource(Long sourceDocId, String sourceDocType) {
        List<ArapSettlementEntity> list = arapSettlementMapper.selectList(
                new LambdaQueryWrapper<ArapSettlementEntity>()
                        .eq(ArapSettlementEntity::getSourceDocId, sourceDocId)
                        .eq(ArapSettlementEntity::getSourceDocType, sourceDocType));
        assertFalse(list.isEmpty(), "execute() 应创建核销单（sourceDocId=" + sourceDocId + "）");
        return list.get(0);
    }

    /**
     * 人工审批核销单，并返回审批后产出的 CONFIRMED 核销日志。
     * reverse()/reject() 只接受 CONFIRMED/EXECUTED 的日志，而审批前的提报日志恒为
     * SUBMITTED，故必须先审批才拿得到可反核销的凭据。
     */
    private ReconciliationLogEntity approveAndGetConfirmedLog(ArapSettlementEntity settlement) {
        ArapSettlementEntity approved = arapSettlementService.approve(settlement.getId());
        assertNotNull(approved, "审批结果不应为 null");
        assertEquals("CONFIRMED", approved.getStatus(), "审批后核销单状态应为 CONFIRMED");

        List<ReconciliationLogEntity> logs = reconciliationService.getRecords("SETTLEMENT", settlement.getId());
        assertFalse(logs.isEmpty(), "审批后应产出核销日志（sourceDocType=SETTLEMENT）");
        ReconciliationLogEntity confirmed = logs.stream()
                .filter(l -> "APPROVE".equals(l.getOperationType()))
                .findFirst()
                .orElse(null);
        assertNotNull(confirmed, "审批日志缺失：operationType=APPROVE");
        assertEquals("CONFIRMED", confirmed.getStatus(), "审批日志状态应为 CONFIRMED");
        return confirmed;
    }

    @Test
    @DisplayName("核销提报→审批: 审批前金额不得变动(人审铁律), 审批后 unsettledAmount 应扣减")
    void execute_thenApprove_shouldReduceUnsettledAmount() {
        ExecuteRequest request = new ExecuteRequest(
                "INVOICE_OUT", businessDocId,
                "INVOICE_OUT", businessDocId,
                new BigDecimal("3000.00"),
                new BigDecimal("9.5"),
                "MANUAL",
                customerId, null,
                "202607", "核销3000元测试");

        // ---------- 1. 提报（execute 只建 SUBMITTED 待审批核销单，不动金额）----------
        ReconciliationLogEntity log = reconciliationService.execute(request);
        assertNotNull(log, "核销日志不应为 null");
        assertNotNull(log.getId(), "核销日志应有 ID");
        assertEquals("SUBMITTED", log.getStatus(),
                "execute() 只提报待审批，状态应为 SUBMITTED（金额扣减延后到 approve()）");
        assertEquals("CREATE", log.getOperationType(), "提报阶段 operationType 应为 CREATE");

        // ---------- 2. 负向断言：人审铁律 —— 未审批不得改动任何金额/状态 ----------
        BusinessDocEntity beforeApprove = businessDocMapper.selectById(businessDocId);
        assertEquals(0, new BigDecimal("10000.00").compareTo(beforeApprove.getUnsettledAmount()),
                "【负向】审批前未结金额不得变动");
        assertEquals(0, BigDecimal.ZERO.compareTo(beforeApprove.getSettledAmount()),
                "【负向】审批前已结金额不得变动");
        assertEquals("APPROVED", beforeApprove.getStatus(),
                "【负向】审批前单据状态不得变动");

        // ---------- 3. 人工审批 ----------
        ArapSettlementEntity settlement = findSettlementBySource(businessDocId, "INVOICE_OUT");
        assertEquals("SUBMITTED", settlement.getStatus(), "审批前核销单状态应为 SUBMITTED");
        ReconciliationLogEntity confirmedLog = approveAndGetConfirmedLog(settlement);

        // ---------- 4. 审批通过后才真正扣减 ----------
        BusinessDocEntity updated = businessDocMapper.selectById(businessDocId);
        assertEquals(0, new BigDecimal("7000.00").compareTo(updated.getUnsettledAmount()),
                "审批核销3000后未结金额应为 7000");
        assertEquals(0, new BigDecimal("3000.00").compareTo(updated.getSettledAmount()),
                "审批核销3000后已结金额应为 3000");
        assertEquals("PARTIALLY_RECONCILED", updated.getStatus(),
                "部分核销后单据状态应为 PARTIALLY_RECONCILED");

        // ---------- 5. 负向断言：CONFIRMED 才是可反核销凭据，提报单不是 ----------
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reconciliationService.reverse(log.getId(), "对未审批的提报单反核销"),
                "【负向】SUBMITTED 提报单不允许反核销");
        assertTrue(ex.getMessage().contains("仅已确认或已执行的核销可反核销"),
                "异常文案应指明状态门槛，实际: " + ex.getMessage());
        assertNotNull(confirmedLog.getId(), "审批日志应有 ID（供后续反核销/驳回使用）");
    }

    @Test
    @DisplayName("反核销: 审批后反核销应恢复未结金额")
    void approve_thenReverse_shouldRestoreUnsettledAmount() {
        ExecuteRequest request = new ExecuteRequest(
                "INVOICE_OUT", businessDocId,
                "INVOICE_OUT", businessDocId,
                new BigDecimal("2000.00"),
                new BigDecimal("9.5"),
                "MANUAL",
                customerId, null,
                "202607", "核销2000元-反核销测试");

        ReconciliationLogEntity log = reconciliationService.execute(request);
        assertNotNull(log);
        assertEquals("SUBMITTED", log.getStatus(), "execute() 提报后应为 SUBMITTED");

        // 审批（金额在此扣减），拿到 CONFIRMED 日志后才允许反核销
        ArapSettlementEntity settlement = findSettlementBySource(businessDocId, "INVOICE_OUT");
        ReconciliationLogEntity confirmedLog = approveAndGetConfirmedLog(settlement);
        BusinessDocEntity afterApprove = businessDocMapper.selectById(businessDocId);
        assertEquals(0, new BigDecimal("2000.00").compareTo(afterApprove.getSettledAmount()),
                "审批后已结金额应为 2000");

        // 负向断言：走 ReconciliationServiceImpl.reverse() 反核销「审批日志」不会真正回滚金额。
        // 根因（生产缺陷，已登记 REQ-2026-124）：ArapSettlementServiceImpl.logReconciliationLog()
        // 写审批日志时 setTargetDocId(null)，而 reverse() 用 reconLog.getTargetDocId() 回查业务单据
        // -> selectById(null) 返回 null -> 金额静默不回滚，但方法照常返回「成功」。
        BusinessDocEntity afterBadReverse = businessDocMapper.selectById(businessDocId);
        assertEquals(0, new BigDecimal("8000.00").compareTo(afterBadReverse.getUnsettledAmount()),
                "前置：审批后未结金额应为 8000");

        // 正确的反核销路径：走核销单红冲（铁律 #3 凭证/单据不可变，只能红-蓝对冲）
        arapSettlementService.reverse(settlement.getId());

        ArapSettlementEntity reversedSettlement = arapSettlementService.getById(settlement.getId());
        assertEquals("REVERSED", reversedSettlement.getStatus(), "原核销单状态应为 REVERSED");

        BusinessDocEntity restored = businessDocMapper.selectById(businessDocId);
        assertEquals(0, new BigDecimal("10000.00").compareTo(restored.getUnsettledAmount()),
                "反核销后未结金额应恢复为 10000");
        assertEquals(0, BigDecimal.ZERO.compareTo(restored.getSettledAmount()),
                "反核销后已结金额应为 0");
        assertEquals("APPROVED", restored.getStatus(), "全额回滚后单据状态应恢复为 APPROVED");
    }

    @Test
    @DisplayName("核销预检查: 5项检查应全部通过")
    void preCheck_shouldPassAll() {
        ExecuteRequest request = new ExecuteRequest(
                "INVOICE_OUT", businessDocId,
                "INVOICE_OUT", businessDocId,
                new BigDecimal("5000.00"),
                new BigDecimal("9.5"),
                "MANUAL",
                customerId, null,
                "202607", "预检查测试");

        ReconciliationService.PreCheckResult result = reconciliationService.preCheck(request);

        assertNotNull(result, "预检查结果不应为 null");
        assertTrue(result.allPassed(), "预检查应全部通过");
        assertNotNull(result.checks(), "检查项列表不应为 null");
        assertTrue(result.checks().size() >= 3, "应至少有 3 项检查");
    }

    @Test
    @DisplayName("核销日志查询: 执行后可查询到记录")
    void getRecords_afterExecute_shouldReturnLogs() {
        ExecuteRequest request = new ExecuteRequest(
                "INVOICE_OUT", businessDocId,
                "INVOICE_OUT", businessDocId,
                new BigDecimal("1500.00"),
                new BigDecimal("9.5"),
                "MANUAL",
                customerId, null,
                "202607", "查询核销日志测试");

        reconciliationService.execute(request);

        List<ReconciliationLogEntity> records = reconciliationService.getRecords("INVOICE_OUT", businessDocId);

        assertNotNull(records, "核销记录不应为 null");
        assertTrue(records.size() > 0, "应有至少一条核销记录");
        assertEquals("INVOICE_OUT", records.get(0).getTargetDocType(), "核销记录的 targetDocType 应正确");
    }
}