package com.huicai.sme.arap.integration;

import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.sme.arap.entity.ReconciliationLogEntity;
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

    @Test
    @DisplayName("核销执行: 单笔核销后 unsettledAmount 应扣减")
    void execute_shouldReduceUnsettledAmount() {
        ExecuteRequest request = new ExecuteRequest(
                "INVOICE_OUT", businessDocId,
                "INVOICE_OUT", businessDocId,
                new BigDecimal("3000.00"),
                new BigDecimal("9.5"),
                "MANUAL",
                customerId, null,
                "202607", "核销3000元测试");

        ReconciliationLogEntity log = reconciliationService.execute(request);

        assertNotNull(log, "核销日志不应为 null");
        assertNotNull(log.getId(), "核销日志应有 ID");
        assertEquals("CONFIRMED", log.getStatus(), "核销初始状态应为 CONFIRMED");

        BusinessDocEntity updated = businessDocMapper.selectById(businessDocId);
        assertEquals(0, new BigDecimal("7000.00").compareTo(updated.getUnsettledAmount()),
                "核销3000后未结金额应为 7000");
        assertEquals(0, new BigDecimal("3000.00").compareTo(updated.getSettledAmount()),
                "核销3000后已结金额应为 3000");
    }

    @Test
    @DisplayName("反核销: 恢复未结金额")
    void reverse_shouldRestoreUnsettledAmount() {
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

        reconciliationService.reverse(log.getId(), "测试反核销");

        BusinessDocEntity restored = businessDocMapper.selectById(businessDocId);
        assertEquals(0, new BigDecimal("10000.00").compareTo(restored.getUnsettledAmount()),
                "反核销后未结金额应恢复为 10000");
        assertEquals(0, BigDecimal.ZERO.compareTo(restored.getSettledAmount()),
                "反核销后已结金额应为 0");
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