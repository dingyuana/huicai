package com.huicai.common.test;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.sme.arap.entity.PrepaymentEntity;
import com.huicai.sme.arap.mapper.PrepaymentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DIR-004 守卫：MyBatis-Plus 分页的 {@code total} 必须与 {@code records} 同为企业，
 * 不能返回全表条数。
 *
 * <p><b>缺陷机理</b>（2026-10-07 实测）：{@code MyBatisPlusConfig} 原把
 * {@code PaginationInnerInterceptor} 注册在 {@code EnterpriseDataPermissionInterceptor}
 * <b>之前</b>。MP 按注册顺序调用内层拦截器的
 * {@code willDoQuery}→{@code beforeQuery}，而分页插件在自己的 {@code willDoQuery} 里
 * <b>自行拼 COUNT 并直接用 executor 执行</b> —— 那一刻企业拦截器尚未对本次查询注入条件，
 * COUNT 拿到的 {@code boundSql} 不含 {@code enterprise_id}
 * ⇒ {@code total} 是全表条数，而 {@code records} 是本企业的。
 *
 * <p><b>为什么以前无人发现</b>：多数 Service 在应用层已自己带 {@code enterprise_id}，
 * 应用层条件把 COUNT 也带对了 ⇒ 缺陷被掩盖。本守卫一律<b>不加任何应用层企业条件</b>、
 * 直接调 mapper，故对「应用层是否恰好带了条件」不敏感。
 *
 * <p><b>断言口径</b>：{@code total} 与 {@code records.size()} 必须同时等于本企业行数；
 * 只断言 records 等于没断言一半（AGENTS §4.5 第 40 条）。
 */
@DisplayName("DIR-004 守卫：分页 total 不得跨租户泄漏")
class PaginationTotalTenantIsolationRealDBTest extends AbstractMapperTest {

    /** 独立企业号：该企业在两表下均无数据，与基类默认的 1 明确区分 */
    private static final Long EMPTY_ENTERPRISE = 990002L;

    private static final String PREFIX = "9999.DIR004.";

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private PrepaymentMapper prepaymentMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * 场景一：上下文企业<b>无任何数据</b>时，total 与 records 必须同时为 0。
     *
     * <p>修复前实测：{@code total=43}（企业 1 的种子条数）/ {@code records=0}。
     */
    @Test
    @DisplayName("空企业的 total 必须为 0（不得返回全表条数）")
    void totalMustNotLeakWhenCurrentEnterpriseHasNoRows() {
        useEnterprise(EMPTY_ENTERPRISE);

        Page<Subject> page = subjectMapper.selectPage(new Page<>(1, 10), new LambdaQueryWrapper<>());

        long records = page.getRecords().size();
        long total = page.getTotal();
        assertEquals(0L, records,
                "上下文企业 " + EMPTY_ENTERPRISE + " 无科目，records 应为 0，实际 " + records);
        assertEquals(0L, total,
                "total 跨租户泄漏：上下文企业无任何科目，total 却为 " + total
                        + "（应是全表条数）。根因见 DIR-004：分页插件排在企业隔离拦截器之前，COUNT 未被注入 enterprise_id");
    }

    /**
     * 场景二：上下文企业<b>有数据</b>、别的企业也有数据时，total 必须只数到本企业。
     *
     * <p>⚠️ **为什么必须自己造数而不能用 {@code t_subject}**：它的 43 条种子**全部属于企业 1**，
     * 于是「本企业行数」恰好等于「全表行数」，用例在缺陷存在时也会通过 —— **无鉴别力**。
     * （这正是 AGENTS §4.4 第 16 条「断言前先取基线、确认二者确实不同」的适用场景。）
     */
    @Test
    @DisplayName("多企业并存时 total 只数本企业，不得含别家企业行数")
    void totalMustCountOnlyCurrentEnterpriseRows() {
        // 企业 1 造 2 行
        useEnterprise(1L);
        for (int i = 1; i <= 2; i++) {
            prepaymentMapper.insert(draft(PREFIX + "E1." + i));
        }
        // 企业 E 造 3 行
        useEnterprise(ENTERPRISE_WITH_DATA);
        for (int i = 1; i <= 3; i++) {
            prepaymentMapper.insert(draft(PREFIX + "EX." + i));
        }

        // 基线：确认两个企业的行数确实不同，否则本用例无鉴别力
        useEnterprise(1L);
        long rowsOfE1 = countOwn(PREFIX + "E1.");
        useEnterprise(ENTERPRISE_WITH_DATA);
        long rowsOfEx = countOwn(PREFIX + "EX.");
        assertEquals(2L, rowsOfE1, "企业 1 夹具应为 2 行");
        assertEquals(3L, rowsOfEx, "企业 E 夹具应为 3 行");
        assertEquals(5L, rowsOfE1 + rowsOfEx, "两企业合计 5 行 —— 与任何单一企业的行数都不同");

        // 关键断言：切到企业 E，total 必须是 3（而非全表 5 或别的数）
        useEnterprise(ENTERPRISE_WITH_DATA);
        Page<PrepaymentEntity> page = prepaymentMapper.selectPage(
                new Page<>(1, 100), new LambdaQueryWrapper<>());

        long records = page.getRecords().size();
        long total = page.getTotal();
        assertEquals(rowsOfEx, total,
                "total 跨租户泄漏：企业 " + ENTERPRISE_WITH_DATA + " 只有 " + rowsOfEx
                        + " 行，total 却为 " + total + "（本用例夹具合计 " + (rowsOfE1 + rowsOfEx) + " 行）");
        assertEquals(rowsOfEx, records,
                "records 应等于本企业行数 " + rowsOfEx + "，实际 " + records);
    }

    /** 造一行预付款；enterprise_id 由 insertFill 按上下文写入，故此处不设 */
    private PrepaymentEntity draft(String tag) {
        PrepaymentEntity e = new PrepaymentEntity();
        e.setTxDate(java.time.LocalDate.now());
        e.setPeriod("209912");
        e.setVendorId(1L);
        e.setAmount(new java.math.BigDecimal("100.00"));
        e.setSummary(tag);
        e.setCreatedAt(java.time.LocalDate.now());
        e.setUpdatedAt(java.time.LocalDate.now());
        return e;
    }

    /** 用唯一前缀把行数收敛到本用例夹具，避免全表计数越界（AGENTS §4.4 第 16 条） */
    private long countOwn(String namePrefix) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_prepayment WHERE summary LIKE ?",
                Long.class, namePrefix + "%");
        return n == null ? 0L : n;
    }

    /** 另一个有数据的独立企业号 */
    private static final Long ENTERPRISE_WITH_DATA = 990003L;
}