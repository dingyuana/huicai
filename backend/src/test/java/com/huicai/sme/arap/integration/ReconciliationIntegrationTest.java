package com.huicai.sme.arap.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.common.context.EnterpriseContextHolder;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

    /** P106 / AT-106-2：非默认企业号，用于验证核销日志不写死租户 1 */
    private static final Long P106_ENTERPRISE = 990101L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Autowired
    private com.huicai.sme.arap.mapper.ReconciliationLogMapper reconciliationLogMapper;

    private static final Long USER_ID = 1L;
    private Long customerId;
    private Long businessDocId;

    @BeforeEach
    void setUp() {
        createFixtures();
    }

    /**
     * 在<b>当前企业上下文</b>下建夹具。
     *
     * <p><b>为何可复用</b>：P106 / AT-106-2 需要在非默认企业（990101）下造数，
     * 而 {@code EnterpriseDataPermissionInterceptor} 会按上下文过滤 ⇒ 沿用企业 1 的夹具
     * 在切到 990101 后 {@code selectById} 会返 null（症状是「业务单据不存在」，
     * 与真实原因毫无关系）。故夹具必须在目标上下文内重建。
     */
    private void createFixtures() {
        // 创建测试客户（auto-generated ID）
        CustomerEntity c = new CustomerEntity();
        c.setName("核销测试客户");
        c.setCode("WRTOFF-" + System.currentTimeMillis());
        c.setEnterpriseId(EnterpriseContextHolder.get());
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

        // 反核销走 ReconciliationService 入口（即 Controller 的 /{id}/reverse）：
        // 该入口按 sourceDocType=SETTLEMENT 识别为核销单生命周期日志，转委托红冲路径。
        // REQ-2026-125 前此入口是「静默不回滚却返回成功」：logReconciliationLog 写审批日志时
        // setTargetDocId(null) -> reverse() selectById(null)=null -> 整块跳过，金额纹丝不动。
        reconciliationService.reverse(confirmedLog.getId(), "测试反核销");

        ArapSettlementEntity reversedSettlement = arapSettlementService.getById(settlement.getId());
        assertEquals("REVERSED", reversedSettlement.getStatus(), "原核销单状态应为 REVERSED");

        BusinessDocEntity restored = businessDocMapper.selectById(businessDocId);
        assertEquals(0, new BigDecimal("10000.00").compareTo(restored.getUnsettledAmount()),
                "反核销后未结金额应恢复为 10000");
        assertEquals(0, BigDecimal.ZERO.compareTo(restored.getSettledAmount()),
                "反核销后已结金额应为 0");
        assertEquals("APPROVED", restored.getStatus(), "全额回滚后单据状态应恢复为 APPROVED");

        ReconciliationLogEntity cancelledLog = reconciliationLogMapper.selectById(confirmedLog.getId());
        assertEquals("CANCELLED", cancelledLog.getStatus(), "被反核销的审批日志应置 CANCELLED");
    }

    @Test
    @DisplayName("负向: 核销单已审批生效后不可驳回, 必须走红冲")
    void reject_afterApprove_shouldThrow_directToReverse() {
        ExecuteRequest request = new ExecuteRequest(
                "INVOICE_OUT", businessDocId,
                "INVOICE_OUT", businessDocId,
                new BigDecimal("1500.00"),
                new BigDecimal("9.5"),
                "MANUAL",
                customerId, null,
                "202607", "核销1500元-驳回负向测试");

        ReconciliationLogEntity log = reconciliationService.execute(request);
        assertEquals("SUBMITTED", log.getStatus());
        ArapSettlementEntity settlement = findSettlementBySource(businessDocId, "INVOICE_OUT");
        ReconciliationLogEntity confirmedLog = approveAndGetConfirmedLog(settlement);

        // ArapSettlementService.reject() 只接受 SUBMITTED 且从不回滚金额，
        // 对已生效的核销单放行会留下「已驳回」状态而金额不动。
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reconciliationService.reject(confirmedLog.getId(), "金额有误"),
                "已审批生效的核销单不允许驳回");
        assertTrue(ex.getMessage().contains("反核销"), "异常应指向反核销入口，实际: " + ex.getMessage());

        // 负向：驳回被拒后金额与状态都不得变动
        BusinessDocEntity unchanged = businessDocMapper.selectById(businessDocId);
        assertEquals(0, new BigDecimal("1500.00").compareTo(unchanged.getSettledAmount()),
                "驳回被拒后已结金额不得变动");
        assertEquals("PARTIALLY_RECONCILED", unchanged.getStatus(), "单据状态不得变动");
        assertEquals("CONFIRMED", arapSettlementService.getById(settlement.getId()).getStatus(),
                "核销单状态不得变动");
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

    /**
     * P106 / REQ-2026-133 / AT-106-2：核销日志的 tenant_id 必须随企业上下文，不得写死常量 1。
     *
     * <p><b>缺陷</b>：{@code t_reconciliation_log} 同时有 {@code tenant_id}（V94:189）
     * 与 {@code enterprise_id}（V105:23），而 RLS 的 enterprise_policy 只读后者；
     * {@code ReconciliationServiceImpl:364/:780} 写的是
     * {@code setTenantId(DEFAULT_TENANT_ID /* = 1L *&#47;/)} ⇒ 两列永久不一致。
     *
     * <p>本用例类必须是 {@code NOT_SUPPORTED}（execute 是 REQUIRES_NEW），
     * 故上下文由本用例自行设置，且 {@code enterprise_id} 只能由真实落库回读验证 ——
     * 内存对象的 enterpriseId 由 {@code MyMetaObjectHandler} 在 insert 时才写入。
     */
    @Test
    @DisplayName("AT-106-2 核销日志写入当前企业而非常量 1（两列须恒等）")
    void reconciliationLogMustFollowEnterpriseContext() {
        useEnterprise(P106_ENTERPRISE);
        final Long[] ctxBackup = { EnterpriseContextHolder.get() };

        try {
            // 夹具必须在目标上下文内重建（见 createFixtures 的注释）
            createFixtures();

            ExecuteRequest request = new ExecuteRequest(
                    "INVOICE_OUT", businessDocId,
                    "INVOICE_OUT", businessDocId,
                    new BigDecimal("1100.00"),
                    new BigDecimal("9.5"),
                    "MANUAL",
                    customerId, null,
                    "202607", "P106 AT-106-2 核销日志企业归属");

            ReconciliationLogEntity log = reconciliationService.execute(request);
            assertNotNull(log, "核销日志不应为 null");
            assertNotNull(log.getId(), "核销日志应有 ID");

            Long tenantId = jdbcTemplate.queryForObject(
                    "SELECT tenant_id FROM t_reconciliation_log WHERE id = ?", Long.class, log.getId());
            Long enterpriseId = jdbcTemplate.queryForObject(
                    "SELECT enterprise_id FROM t_reconciliation_log WHERE id = ?", Long.class, log.getId());

            assertEquals(P106_ENTERPRISE, enterpriseId,
                    "enterprise_id 应为当前企业上下文 " + P106_ENTERPRISE + "，实际 " + enterpriseId);
            assertEquals(enterpriseId, tenantId,
                    "两列必须恒等：ReconciliationServiceImpl:364/:780 写死 tenant_id=1（DEFAULT_TENANT_ID）"
                            + "而 RLS 谓词读 enterprise_id ⇒ 两列永久不一致。实际 tenant_id=" + tenantId
                            + " / enterprise_id=" + enterpriseId);
        } finally {
            if (ctxBackup[0] != null) {
                useEnterprise(ctxBackup[0]);
            }
        }
    }
}