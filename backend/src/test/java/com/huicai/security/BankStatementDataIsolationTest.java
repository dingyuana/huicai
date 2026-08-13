package com.huicai.security;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.business.constant.StatementStatus;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.base.business.mapper.BankStatementMapper;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.sme.cash.service.BankStatementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BankStatement 数据隔离测试 — 验证 enterprise_id 多租户隔离在 Service 层和 Mapper 层是否生效。
 *
 * <p>覆盖 6 类数据访问路径共 17 个测试用例：
 * <ul>
 *   <li>Service 层状态机方法（review/audit/approve/processManual/classifySingle/updateClassification）</li>
 *   <li>Service 层查询方法（pageQuery/getDetail）</li>
 *   <li>批量操作（batchReview/batchAudit/batchGenerateVouchers）</li>
 *   <li>导入操作（importFromCsv）</li>
 *   <li>删除操作（deleteStatement）</li>
 *   <li>拦截器注入验证（EnterpriseContextHolder 未设置/设置的行为差异）</li>
 * </ul>
 *
 * <p>基于 {@link AbstractMapperTest}，启动真实 PostgreSQL 16 + Flyway 迁移。
 * 通过 {@link EnterpriseContextHolder#set(Long)} 模拟不同企业上下文。
 * 依赖 {@link com.huicai.common.context.EnterpriseDataPermissionInterceptor} 自动注入 enterprise_id 条件。
 *
 * <p>⚠️ 跨企业访问 Service 方法时，拦截器在 selectById 阶段注入 enterprise_id 条件，
 * 导致查询返回 null，进而触发 BusinessException.notFound("对账单记录不存在")。
 *
 * @SlowTest — 需要 Docker + Testcontainers
 */
@SlowTest
@DisplayName("BankStatement 数据隔离测试 — enterprise_id 多租户隔离检查")
public class BankStatementDataIsolationTest extends AbstractMapperTest {

    private static final Long ENTERPRISE_A = 1L;
    private static final Long ENTERPRISE_B = 2L;

    @Autowired
    private BankStatementService bankStatementService;

    @Autowired
    private BankStatementMapper bankStatementMapper;

    @AfterEach
    void clearContext() {
        EnterpriseContextHolder.clear();
    }

    /**
     * 创建测试银行流水数据。
     *
     * @param enterpriseId 企业 ID
     * @param txType       交易类型（INCOME/EXPENSE）
     * @param amount       金额
     * @param classification 业务分类（可为 null）
     * @param reviewStatus 审核状态（可为 null）
     * @return 创建的实体（含自动生成的 ID）
     */
    private BankStatementEntity createStatement(Long enterpriseId, String txType, BigDecimal amount,
                                                 String classification, String reviewStatus) {
        BankStatementEntity entity = new BankStatementEntity();
        entity.setAccountId(enterpriseId); // 用企业 ID 作为 accountId 方便区分
        entity.setTxDate(LocalDate.of(2026, 8, 1));
        entity.setTxType(txType);
        entity.setAmount(amount);
        entity.setCounterAccount("客户" + enterpriseId);
        entity.setSummary("货款");
        entity.setClassification(classification);
        entity.setReviewStatus(reviewStatus);
        entity.setEnterpriseId(enterpriseId);
        bankStatementMapper.insert(entity);
        return entity;
    }

    private BankStatementEntity createStatement(Long enterpriseId, String txType, BigDecimal amount) {
        return createStatement(enterpriseId, txType, amount, "BUSINESS_RECEIPT", StatementStatus.PENDING);
    }

    // ==================== 3.1 Service 层状态机方法数据隔离 ====================

    @Test
    @DisplayName("pageQuery: 企业A查询不应返回企业B的流水")
    void pageQuery_企业隔离_仅返回本企业数据() {
        // 创建企业 A 的流水
        EnterpriseContextHolder.set(ENTERPRISE_A);
        BankStatementEntity bsA = createStatement(ENTERPRISE_A, "INCOME", new BigDecimal("1000"));

        // 创建企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"));

        // 企业 A 查询
        EnterpriseContextHolder.set(ENTERPRISE_A);
        IPage<BankStatementEntity> page = bankStatementService.pageQuery(
                null, null, null, null, 1, 100);

        assertTrue(page.getRecords().stream()
                        .anyMatch(s -> s.getId().equals(bsA.getId())),
                "企业A的流水应该被查到");
        assertTrue(page.getRecords().stream()
                        .noneMatch(s -> s.getId().equals(bsB.getId())),
                "⚠️ 漏洞：企业B的流水被企业A查到（enterprise_id 过滤缺失）");
    }

    @Test
    @DisplayName("getDetail: 企业A查询企业B的流水ID应返回空")
    void getDetail_企业隔离_跨企业ID返回空() {
        // 创建企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"));

        // 企业 A 查询企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_A);
        try {
            bankStatementService.getDetail(bsB.getId());
            fail("⚠️ 漏洞：企业A应该无法查询企业B的流水详情");
        } catch (BusinessException e) {
            assertEquals(404, e.getCode(), "应返回 notFound 异常");
        }
    }

    @Test
    @DisplayName("review: 企业A审核企业B的流水应被拒绝")
    void review_企业隔离_跨企业流水被拒() {
        // 创建企业 B 的流水（含分类，review 要求分类已设置）
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"),
                "BUSINESS_RECEIPT", StatementStatus.PENDING);

        // 企业 A 尝试审核企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_A);
        try {
            bankStatementService.review(bsB.getId(), 1L);
            fail("⚠️ 漏洞：企业A应该无法审核企业B的流水");
        } catch (BusinessException e) {
            assertEquals(404, e.getCode(), "应返回 notFound 异常（selectById 被拦截器过滤）");
        }
    }

    @Test
    @DisplayName("audit: 企业A审核企业B的流水应被拒绝")
    void audit_企业隔离_跨企业审核被拒() {
        // 创建企业 B 的流水（audit 要求 CONFIRMED 状态）
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"),
                "BUSINESS_RECEIPT", StatementStatus.CONFIRMED);

        // 企业 A 尝试审核企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_A);
        try {
            bankStatementService.audit(bsB.getId(), 1L);
            fail("⚠️ 漏洞：企业A应该无法审核企业B的流水");
        } catch (BusinessException e) {
            assertEquals(404, e.getCode(), "应返回 notFound 异常（selectById 被拦截器过滤）");
        }
    }

    @Test
    @DisplayName("approve: 企业A核准企业B的流水应被拒绝")
    void approve_企业隔离_跨企业核准被拒() {
        // 创建企业 B 的流水（approve 要求 voucher_generated 或 payment_created 状态）
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"),
                "BUSINESS_RECEIPT", StatementStatus.VOUCHER_GENERATED);

        // 企业 A 尝试核准企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_A);
        try {
            bankStatementService.approve(bsB.getId());
            fail("⚠️ 漏洞：企业A应该无法核准企业B的流水");
        } catch (BusinessException e) {
            assertEquals(404, e.getCode(), "应返回 notFound 异常（selectById 被拦截器过滤）");
        }
    }

    @Test
    @DisplayName("processManual: 企业A处理企业B的C类流水应被拒绝")
    void processManual_企业隔离_跨企业处理被拒() {
        // 创建企业 B 的流水（processManual 要求 manual_pending 状态）
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"),
                "BUSINESS_RECEIPT", StatementStatus.MANUAL_PENDING);

        // 企业 A 尝试处理企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_A);
        try {
            bankStatementService.processManual(bsB.getId(), "A", null, 1L);
            fail("⚠️ 漏洞：企业A应该无法处理企业B的流水");
        } catch (BusinessException e) {
            assertEquals(404, e.getCode(), "应返回 notFound 异常（selectById 被拦截器过滤）");
        }
    }

    @Test
    @DisplayName("classifySingle: 企业A分类企业B的流水应被拒绝")
    void classifySingle_企业隔离_跨企业分类被拒() {
        // 创建企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"),
                null, null);

        // 企业 A 尝试分类企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_A);
        try {
            bankStatementService.classifySingle(bsB.getId());
            fail("⚠️ 漏洞：企业A应该无法分类企业B的流水");
        } catch (BusinessException e) {
            assertEquals(404, e.getCode(), "应返回 notFound 异常（selectById 被拦截器过滤）");
        }
    }

    @Test
    @DisplayName("updateClassification: 企业A修改企业B的流水分类应被拒绝")
    void updateClassification_企业隔离_跨企业修改被拒() {
        // 创建企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"),
                "BUSINESS_RECEIPT", StatementStatus.PENDING);

        // 企业 A 尝试修改企业 B 的流水分类
        EnterpriseContextHolder.set(ENTERPRISE_A);
        try {
            bankStatementService.updateClassification(bsB.getId(), "SALARY_SOCIAL");
            fail("⚠️ 漏洞：企业A应该无法修改企业B的流水分类");
        } catch (BusinessException e) {
            assertEquals(404, e.getCode(), "应返回 notFound 异常（selectById 被拦截器过滤）");
        }
    }

    // ==================== 3.2 批量操作数据隔离 ====================

    @Test
    @DisplayName("batchReview: 企业A批量确认不应影响企业B的流水")
    void batchReview_企业隔离_仅处理本企业流水() {
        // 创建企业 A 的流水（可确认状态）
        EnterpriseContextHolder.set(ENTERPRISE_A);
        BankStatementEntity bsA = createStatement(ENTERPRISE_A, "INCOME", new BigDecimal("1000"),
                "BUSINESS_RECEIPT", StatementStatus.PENDING);

        // 创建企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"),
                "BUSINESS_RECEIPT", StatementStatus.PENDING);

        // 企业 A 批量确认，传入企业 A 和企业 B 的 ID
        EnterpriseContextHolder.set(ENTERPRISE_A);
        var result = bankStatementService.batchReview(List.of(bsA.getId(), bsB.getId()), 1L);

        // 企业 A 的流水应被确认，企业 B 的流水不应被影响
        assertTrue(result.success() >= 1, "企业A的流水应被确认");
        // 验证企业 B 的流水状态未改变
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsBafter = bankStatementMapper.selectById(bsB.getId());
        assertEquals(StatementStatus.PENDING, bsBafter.getReviewStatus(),
                "企业B的流水状态不应被改变");
    }

    @Test
    @DisplayName("batchAudit: 企业A批量审核不应影响企业B的流水")
    void batchAudit_企业隔离_仅审核本企业流水() {
        // 创建企业 A 的流水（audit 要求 CONFIRMED 状态）
        EnterpriseContextHolder.set(ENTERPRISE_A);
        BankStatementEntity bsA = createStatement(ENTERPRISE_A, "INCOME", new BigDecimal("1000"),
                "BUSINESS_RECEIPT", StatementStatus.CONFIRMED);

        // 创建企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"),
                "BUSINESS_RECEIPT", StatementStatus.CONFIRMED);

        // 企业 A 批量审核，传入企业 A 和企业 B 的 ID
        EnterpriseContextHolder.set(ENTERPRISE_A);
        bankStatementService.batchAudit(List.of(bsA.getId(), bsB.getId()), 1L);

        // 验证企业 B 的流水状态未改变（审核逻辑依赖 AutoGenerationService，只验证隔离性）
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsBafter = bankStatementMapper.selectById(bsB.getId());
        assertEquals(StatementStatus.CONFIRMED, bsBafter.getReviewStatus(),
                "企业B的流水状态不应被改变");
    }

    @Test
    @DisplayName("batchGenerateVouchers: 企业A批量制证不应影响企业B的流水")
    void batchGenerateVouchers_企业隔离_仅生成本企业() {
        // 创建企业 A 的流水（generateVoucher 要求 CONFIRMED 或 AUDITED 状态）
        EnterpriseContextHolder.set(ENTERPRISE_A);
        BankStatementEntity bsA = createStatement(ENTERPRISE_A, "INCOME", new BigDecimal("1000"),
                "BUSINESS_RECEIPT", StatementStatus.CONFIRMED);

        // 创建企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"),
                "BUSINESS_RECEIPT", StatementStatus.CONFIRMED);

        // 企业 A 批量制证，传入企业 A 和企业 B 的 ID
        EnterpriseContextHolder.set(ENTERPRISE_A);
        bankStatementService.batchGenerateVouchers(List.of(bsA.getId(), bsB.getId()), 1L);

        // 验证企业 B 的流水状态未改变（制证逻辑依赖 AutoGenerationService，只验证隔离性）
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsBafter = bankStatementMapper.selectById(bsB.getId());
        assertEquals(StatementStatus.CONFIRMED, bsBafter.getReviewStatus(),
                "企业B的流水状态不应被改变");
    }

    // ==================== 3.3 导入操作数据隔离 ====================

    @Test
    @DisplayName("importFromCsv: 导入数据应正确设置 enterprise_id")
    void importFromCsv_企业隔离_导入数据enterpriseId正确() {
        String csv = "交易日期,金额,摘要\n2026-08-01,1000,货款\n2026-08-02,2000,服务费";

        // 企业 A 导入
        EnterpriseContextHolder.set(ENTERPRISE_A);
        int count = bankStatementService.importFromCsv(ENTERPRISE_A, csv);
        assertEquals(2, count, "应导入2条记录");

        // 验证导入的数据 enterprise_id 全部为企业 A
        List<BankStatementEntity> allA = bankStatementMapper.selectList(null);
        assertTrue(allA.stream().allMatch(s -> ENTERPRISE_A.equals(s.getEnterpriseId())),
                "⚠️ 漏洞：导入数据的 enterprise_id 不正确");
    }

    @Test
    @DisplayName("importFromCsv: 不同企业导入不互相影响")
    void importFromCsv_企业隔离_不同企业导入不互相影响() {
        EnterpriseContextHolder.set(ENTERPRISE_A);
        bankStatementService.importFromCsv(ENTERPRISE_A, "交易日期,金额,摘要\n2026-08-01,1000,货款A");

        EnterpriseContextHolder.set(ENTERPRISE_B);
        bankStatementService.importFromCsv(ENTERPRISE_B, "交易日期,金额,摘要\n2026-08-01,2000,货款B");

        // 验证企业 A 的数据只属于企业 A
        EnterpriseContextHolder.set(ENTERPRISE_A);
        List<BankStatementEntity> allA = bankStatementMapper.selectList(null);
        assertTrue(allA.stream().allMatch(s -> ENTERPRISE_A.equals(s.getEnterpriseId())),
                "企业A导入的数据应全部属于企业A");
        assertTrue(allA.stream().noneMatch(s -> "货款B".equals(s.getSummary())),
                "企业A不应看到企业B导入的数据");

        // 验证企业 B 的数据只属于企业 B
        EnterpriseContextHolder.set(ENTERPRISE_B);
        List<BankStatementEntity> allB = bankStatementMapper.selectList(null);
        assertTrue(allB.stream().allMatch(s -> ENTERPRISE_B.equals(s.getEnterpriseId())),
                "企业B导入的数据应全部属于企业B");
        assertTrue(allB.stream().noneMatch(s -> "货款A".equals(s.getSummary())),
                "企业B不应看到企业A导入的数据");
    }

    // ==================== 3.4 删除操作数据隔离 ====================

    @Test
    @DisplayName("deleteStatement: 企业A删除企业B的流水应被拒绝")
    void deleteStatement_企业隔离_跨企业删除被拒() {
        // 创建企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"),
                "BUSINESS_RECEIPT", StatementStatus.PENDING);

        // 企业 A 尝试删除企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_A);
        try {
            bankStatementService.deleteStatement(bsB.getId());
            fail("⚠️ 漏洞：企业A应该无法删除企业B的流水");
        } catch (BusinessException e) {
            assertEquals(404, e.getCode(), "应返回 notFound 异常（selectById 被拦截器过滤）");
        }

        // 验证企业 B 的流水未被删除
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsBafter = bankStatementMapper.selectById(bsB.getId());
        assertNotNull(bsBafter, "企业B的流水不应被删除");
        assertEquals(0, bsBafter.getDeleted().intValue(), "企业B的流水不应被逻辑删除");
    }

    // ==================== 3.5 拦截器注入验证 ====================

    @Test
    @DisplayName("EnterpriseContextHolder未设置时，查询返回所有企业数据（超级管理员模式）")
    void interceptor_企业隔离_EnterpriseContextHolder未设置_不拦截() {
        // 不设置 EnterpriseContextHolder
        BankStatementEntity bsA = createStatement(ENTERPRISE_A, "INCOME", new BigDecimal("1000"));
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"));

        // 不设置 context holder，直接查询（超级管理员模式）
        List<BankStatementEntity> all = bankStatementMapper.selectList(null);

        assertTrue(all.stream().anyMatch(s -> s.getId().equals(bsA.getId())),
                "企业A的流水应在列表中");
        assertTrue(all.stream().anyMatch(s -> s.getId().equals(bsB.getId())),
                "企业B的流水应在列表中（超级管理员应看到所有数据）");
    }

    @Test
    @DisplayName("EnterpriseContextHolder设置后，查询仅返回本企业数据")
    void interceptor_企业隔离_EnterpriseContextHolder设置_过滤生效() {
        BankStatementEntity bsA = createStatement(ENTERPRISE_A, "INCOME", new BigDecimal("1000"));
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"));

        // 设置企业 A 上下文
        EnterpriseContextHolder.set(ENTERPRISE_A);
        List<BankStatementEntity> all = bankStatementMapper.selectList(null);

        assertTrue(all.stream().anyMatch(s -> s.getId().equals(bsA.getId())),
                "企业A的流水应在列表中");
        assertTrue(all.stream().noneMatch(s -> s.getId().equals(bsB.getId())),
                "⚠️ 漏洞：企业B的流水也被查到（拦截器注入 enterprise_id 条件未生效）");
    }

    // ==================== 3.6 自定义 SQL 查询 ====================

    @Test
    @DisplayName("自定义Mapper查询（selectByAccountAndStatus）应过滤enterprise_id")
    void customSql_企业隔离_自定义查询也过滤enterpriseId() {
        // 创建企业 A 的流水
        EnterpriseContextHolder.set(ENTERPRISE_A);
        BankStatementEntity bsA = createStatement(ENTERPRISE_A, "INCOME", new BigDecimal("1000"));
        bsA.setMatchStatus("UNMATCHED");
        bankStatementMapper.updateById(bsA);

        // 创建企业 B 的流水
        EnterpriseContextHolder.set(ENTERPRISE_B);
        BankStatementEntity bsB = createStatement(ENTERPRISE_B, "INCOME", new BigDecimal("2000"));
        bsB.setMatchStatus("UNMATCHED");
        bankStatementMapper.updateById(bsB);

        // 企业 A 通过自定义查询（selectByAccountAndStatus）查询
        EnterpriseContextHolder.set(ENTERPRISE_A);
        List<BankStatementEntity> results = bankStatementMapper.selectByAccountAndStatus(ENTERPRISE_A, "UNMATCHED");

        // 验证结果中仅包含企业 A 的数据
        // 注意：selectByAccountAndStatus 使用 accountId 过滤，不直接由拦截器注入 enterprise_id
        // 但拦截器机制仍会注入 AND enterprise_id = ENTERPRISE_A
        // 如果 accountId 和 enterpriseId 一致，则自然过滤
        assertTrue(results.stream().anyMatch(s -> s.getId().equals(bsA.getId())),
                "企业A的流水应在结果中");
        assertTrue(results.stream().noneMatch(s -> s.getId().equals(bsB.getId())),
                "⚠️ 漏洞：企业B的流水也被自定义查询查到（拦截器注入 enterprise_id 条件未生效）");
    }
}