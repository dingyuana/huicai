package com.huicai.base.voucher.service;

import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.impl.VoucherServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P116 / 缺陷 4 —— 凭证层制审分离守卫
 *
 * <h2>背景</h2>
 * 制审分离此前只存在于<b>业务单据层</b>（{@code BusinessDocServiceImpl#approve}：
 * 「制单人不能审核自己提交的单据」），凭证层没有 ⇒ 同为审核动作却一个拦一个不拦。
 *
 * <h2>🔴 诚实标注：对存量种子行不触发</h2>
 * {@code created_by} 由 {@code BaseEntity} 提供且<b>不加 fill 注解</b>
 * （类注释明写「由 Service 层手动 set」），而 {@code VoucherServiceImpl#create}
 * 确实会 set ⇒ <b>经 API 新建的凭证有值</b>。
 * 但实测开发库 37 张种子凭证 {@code created_by} <b>全为 NULL</b>
 * （种子走 Flyway、不经 Service）⇒ 本守卫对它们<b>不触发</b>。
 *
 * <p>这不是本 SPEC 引入的弱点，而是与业务单据层一致的既有行为：
 * 「不知道谁制的」不能反过来说「他自审」。已知局限已登记 SPEC-P116 §7。
 * ⇒ 本类用<b>新建</b>凭证（而非改种子行）来验证守卫，
 * 因为那才是它真正的适用场景。
 */
@DisplayName("SPEC-P116：凭证制审分离")
class VoucherMakerCheckerGuardTest extends AbstractMapperTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VoucherMapper voucherMapper;

    @Autowired
    private VoucherEntryMapper voucherEntryMapper;

    @Autowired
    private VoucherServiceImpl voucherService;

    private static final long MAKER = 900001L;
    private static final long REVIEWER = 900002L;

    /**
     * 造一张 SUBMITTED 且 created_by=makerId 的凭证。
     *
     * <p>⚠️ <b>三处夹具硬约束</b>（实测逐个踩过，都是 {@code BadSqlGrammar}/
     * {@code DuplicateKey} 之类的假失败，与被测逻辑无关）：
     * <ol>
     *   <li>{@code t_voucher.voucher_type_id} NOT NULL 无默认值，必须给；
     *       且该表<b>没有</b> {@code voucher_date} 列（日期在 {@code created_at}，有默认）</li>
     *   <li>{@code t_period} 在开发库是<b>空表</b>（0 行，与「录入期初按钮点不动」
     *       同一根因）⇒ {@code assertPeriodOpen} 会抛「会计期间不存在」，
     *       必须先确保期间存在</li>
     *   <li>{@code uq_voucher_no} 是唯一约束 ⇒ 多次造数必须用不同编号，
     *       否则第二个用例撞键</li>
     * </ol>
     *
     * <p>⚠️ <b>刻意不造分录</b>：{@code audit()} 只走
     * {@code assertAuditable + assertPeriodOpen + 制审分离}，<b>不校验分录平衡</b>
     * ⇒ 造分录纯属多余，且会引入 {@code t_voucher_entry} 的额外约束（direction CHECK 等）。
     */
    private long fixtureSubmittedVoucherBy(long makerId) {
        Long entId = jdbc.queryForObject(
                "SELECT id FROM t_enterprise WHERE deleted=0 ORDER BY id LIMIT 1", Long.class);
        assertNotNull(entId, "夹具前提：至少存在一个企业");
        Long typeId = jdbc.queryForObject(
                "SELECT id FROM t_voucher_type WHERE deleted=0 ORDER BY id LIMIT 1", Long.class);
        assertNotNull(typeId, "夹具前提：至少存在一个凭证类型");

        // 期间：不存在则建（幂等）。⚠️ t_period 的 year/month/start_date/end_date
        // 均为 NOT NULL 无默认值，漏给即报"null value in column year" —— 与制审分离无关的假失败。
        //
        // ⚠️ period_code 必须用**本类独占**编码，不能用 '202607'：PeriodMapperTest 等
        // 多处夹具共用它，且靠事务回滚清理；并发跑时同企业同期间撞 uq_period_code_ent
        // （实测把 PeriodMapperTest / DepreciationVoucherRealDBTest 打成红，
        //  报错是"期间编码已存在: 202607"，与本用例毫无关系 —— §4.5 第 47 条形态）。
        jdbc.update("INSERT INTO t_period (period_code, year, month, start_date, end_date, "
                        + "enterprise_id, status, deleted) "
                        + "SELECT 'P116C7', 2026, 7, DATE '2026-07-01', DATE '2026-07-31', "
                        + "?::bigint, 'open', 0 WHERE NOT EXISTS "
                        + "(SELECT 1 FROM t_period WHERE period_code='P116C7' AND enterprise_id=?)",
                entId, entId);

        String voucherNo = "P116-CK-" + makerId + "-" + System.nanoTime();
        return jdbc.queryForObject(
                "INSERT INTO t_voucher (voucher_no, period, voucher_type_id, enterprise_id, status, "
                        + "created_by, total_debit, total_credit, deleted, version) "
                        + "VALUES (?, 'P116C7', ?, ?::bigint, 'SUBMITTED', ?, 100, 100, 0, 0) "
                        + "RETURNING id",
                Long.class, voucherNo, typeId, entId, makerId);
    }

    private int countEntries(long voucherId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM t_voucher_entry WHERE voucher_id=?", Integer.class, voucherId);
    }

    // ───────────────── AT-116-6：自审必须被拒 ─────────────────

    @Test
    @DisplayName("AT-116-6 制单人审核自己提交的凭证必须被拒（含批量路径）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void selfReviewRejected() {
        long vid = fixtureSubmittedVoucherBy(MAKER);
        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.audit(vid, MAKER),
                    "制单人竟能审核自己的凭证 ⇒ 凭证层制审分离缺失（缺陷 4）");
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("制单人不能审核自己提交的凭证"),
                    "报错文案须指明原因，实际：" + ex.getMessage());

            // 批量路径同样必须拦（只拦单条不拦批量 = 半截守卫）
            BusinessException exBatch = assertThrows(BusinessException.class,
                    () -> voucherService.batchAudit(List.of(vid), MAKER),
                    "批量审核绕过了制审分离 ⇒ 半截守卫");
            assertEquals(400, exBatch.getCode());

            // 负向断言：被拒后状态不得变化
            String status = jdbc.queryForObject(
                    "SELECT status FROM t_voucher WHERE id=?", String.class, vid);
            assertEquals("SUBMITTED", status,
                    "自审被拒后凭证状态竟变了 ⇒ 实现是「先改后判」，数据已损坏");
        } finally {
            voucherEntryMapper.delete(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers
                            .<com.huicai.base.voucher.entity.VoucherEntryEntity>lambdaQuery()
                            .eq(com.huicai.base.voucher.entity.VoucherEntryEntity::getVoucherId, vid));
            voucherMapper.deleteById(vid);
        }
    }

    // ───────────────── 正向：他人可审 ─────────────────

    @Test
    @DisplayName("AT-116-6b 审核员审核他人制的凭证应成功")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void otherReviewerCanAudit() {
        long vid = fixtureSubmittedVoucherBy(MAKER);
        try {
            voucherService.audit(vid, REVIEWER);
            assertEquals("AUDITED", jdbc.queryForObject(
                    "SELECT status FROM t_voucher WHERE id=?", String.class, vid),
                    "他人审核应成功");
        } finally {
            voucherEntryMapper.delete(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers
                            .<com.huicai.base.voucher.entity.VoucherEntryEntity>lambdaQuery()
                            .eq(com.huicai.base.voucher.entity.VoucherEntryEntity::getVoucherId, vid));
            voucherMapper.deleteById(vid);
        }
    }

    // ───────────────── 遗留：created_by 为空时不触发（如实标注）─────────────────

    @Test
    @DisplayName("AT-116-7 已知局限：created_by 为 NULL（存量种子行）时守卫不触发")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void legacyRowWithoutCreatorNotBlocked() {
        // 刻意造一条 created_by=NULL 的凭证，确认守卫的 null 分支行为——
        // 这不是缺陷，是「不知道谁制的就不能反过来说他自审」的必要豁免，
        // 与 BusinessDocServiceImpl 既有口径一致。
        Long typeId = jdbc.queryForObject(
                "SELECT id FROM t_voucher_type WHERE deleted=0 ORDER BY id LIMIT 1", Long.class);
        Long entId = jdbc.queryForObject(
                "SELECT id FROM t_enterprise WHERE deleted=0 ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO t_period (period_code, year, month, start_date, end_date, "
                        + "enterprise_id, status, deleted) "
                        + "SELECT 'P116C7', 2026, 7, DATE '2026-07-01', DATE '2026-07-31', "
                        + "?::bigint, 'open', 0 WHERE NOT EXISTS "
                        + "(SELECT 1 FROM t_period WHERE period_code='P116C7' AND enterprise_id=?)",
                entId, entId);
        Long vid = jdbc.queryForObject(
                "INSERT INTO t_voucher (voucher_no, period, voucher_type_id, enterprise_id, status, "
                        + "total_debit, total_credit, deleted, version) "
                        + "VALUES ('P116-LEGACY', 'P116C7', ?, ?::bigint, 'SUBMITTED', 100, 100, 0, 0) "
                        + "RETURNING id", Long.class, typeId, entId);
        try {
            voucherService.audit(vid, MAKER);
            assertEquals("AUDITED", jdbc.queryForObject(
                    "SELECT status FROM t_voucher WHERE id=?", String.class, vid),
                    "created_by 为 NULL 时应放行（与业务单据层口径一致）");
        } finally {
            voucherMapper.deleteById(vid);
        }
    }
}
