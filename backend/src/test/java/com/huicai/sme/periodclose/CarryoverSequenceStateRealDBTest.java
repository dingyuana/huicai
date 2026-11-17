package com.huicai.sme.periodclose;

import com.huicai.base.voucher.dto.CarryoverStepResult;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.periodclose.service.CarryoverSequenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P154 切片三（后半）：结转序列<b>状态空间</b>探针 —— v4.0 §8「期末结转」红线。
 *
 * <p>把 P137 结转序列（DEPR→CLOSE→DISTRIB，单步 REQUIRES_NEW 失败不中断）的
 * 三步 × (GENERATED/SKIPPED) 状态组合落到真库，每状态锁「三步结果形态 + reason 文案」
 * ——reason 文案是操作员在结账工作台的唯一信息面，形态漂移即前端语义断。</p>
 *
 * <p><b>🔴 事务形态（本类与普通 L2 的关键差异）</b>：序列的每步经
 * {@code RequiresNewRunner} 在 REQUIRES_NEW 独立事务执行（P137）——<b>新事务读不到
 * 测试事务的未提交数据</b>。因此造数后必须 {@code TestTransaction.flagForCommit() +
 * end()} 提交，序列才看得见；副作用是<b>数据真落库</b>，@AfterEach 必须显式清理
 * （PROBE-/CLOSE-202612- 号段凭证 + 202612 期间与快照行），防污染复用容器。</p>
 *
 * <p><b>已锁状态</b>：①空账套三步全 SKIPPED（reason 与生产同形，P137 回归锁的家法延续）；
 * ②损益全链（CLOSE GENERATED + CLOSE- 号段凭证 POSTED 落库）；③幂等重放（二跑 CLOSE
 * SKIPPED「已存在」）；④期间已结账（全 SKIPPED 非 FAILED 非 500——runStep 兜底语义）；
 * ⑤DISTRIB 余额前置（4103 无过账余额时「请先完成损益结转过账」）。</p>
 */
@DisplayName("SPEC-P154 切片三：结转序列状态空间 —— 三步×(GENERATED/SKIPPED) 真库锁定")
class CarryoverSequenceStateRealDBTest extends AbstractMapperTest {

    private static final String PERIOD = "202612";

    @Autowired private CarryoverSequenceService carryoverSequenceService;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private VoucherEntryMapper voucherEntryMapper;
    @Autowired private VoucherService voucherService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long typeId;
    private Long subj6001;
    private Long subj6602;
    private Long subj4103;

    @BeforeEach
    void seedBase() {
        EnterpriseContextHolder.set(1L);
        // 期间行（opening_status=entered 防 validateOpeningBeforePost 拦截，chk 只认 none/entered/locked）
        jdbcTemplate.update("""
                INSERT INTO t_period(period_code, year, month, start_date, end_date, status, enterprise_id, opening_status)
                VALUES (?, 2026, 12, '2026-12-01', '2026-12-31', 'open', 1, 'entered')
                """, PERIOD);
        Map<String, Object> type = jdbcTemplate.queryForMap(
                "SELECT id FROM t_voucher_type WHERE enterprise_id = 1 ORDER BY id LIMIT 1");
        typeId = ((Number) type.get("id")).longValue();
        subj6001 = subjectId("6001", "主营业务收入", "credit");
        subj6602 = subjectId("6602", "管理费用", "debit");
        subj4103 = subjectId("4103", "本年利润", "credit");
    }

    @AfterEach
    void cleanup() {
        // 造数经 commitSeed 真提交 ⇒ 测试事务回滚清不掉，必须提交清理（复用容器防污染）
        if (TestTransaction.isActive()) {
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
        TestTransaction.start();
        TestTransaction.flagForCommit();
        try {
            jdbcTemplate.update("DELETE FROM t_voucher_entry WHERE voucher_id IN "
                    + "(SELECT id FROM t_voucher WHERE voucher_no LIKE 'PROBE-%' OR voucher_no LIKE 'CLOSE-202612-%')");
            jdbcTemplate.update("DELETE FROM t_voucher WHERE voucher_no LIKE 'PROBE-%' OR voucher_no LIKE 'CLOSE-202612-%'");
            jdbcTemplate.update("DELETE FROM t_subject_balance WHERE period = ?", PERIOD);
            jdbcTemplate.update("DELETE FROM t_period WHERE period_code = ?", PERIOD);
        } finally {
            TestTransaction.end();
            EnterpriseContextHolder.clear();
        }
    }

    // ── 状态 0：自证前提 —— 序列恒返回 3 步（runStep 兜底不吞步）──

    @Test
    @DisplayName("状态0 自证：序列恒返回 3 步且 step 标识齐（防 runStep 吞步）")
    void sequenceAlwaysReturnsThreeSteps() {
        commitSeed();
        List<CarryoverStepResult> r = carryoverSequenceService.generateSequence(PERIOD, 1L);
        assertEquals(3, r.size(), "序列必须恒返回 3 步（P84：操作员要看完整序列而非中断在半路）");
        assertEquals(List.of("DEPR", "CLOSE", "DISTRIB"),
                r.stream().map(CarryoverStepResult::step).collect(Collectors.toList()),
                "步标识与顺序必须固定：DEPR → CLOSE → DISTRIB");
    }

    // ── 状态 1：空账套 —— 三步全 SKIPPED，reason 文案与生产同形 ──

    @Test
    @DisplayName("状态1 空账套：三步全 SKIPPED 且 reason 文案锁死（P137 生产同形）")
    void emptyAccountAllSkipped() {
        commitSeed(); // 期间行必须提交可见——否则步事务 findPeriod 抛「期间不存在」，reason 形态失真
        List<CarryoverStepResult> r = carryoverSequenceService.generateSequence(PERIOD, 1L);
        for (CarryoverStepResult step : r) {
            assertEquals("SKIPPED", step.status(), "空账套三步必须全 SKIPPED：" + step.step());
            assertTrue(step.reason() != null && !step.reason().isBlank(),
                    "SKIPPED 必须带 reason（工作台唯一信息面）：" + step.step());
        }
        assertTrue(r.get(0).reason().contains("折旧"),
                "DEPR reason 形态：实际=" + r.get(0).reason());
        assertTrue(r.get(1).reason().contains("损益") || r.get(1).reason().contains("结转"),
                "CLOSE reason 形态：实际=" + r.get(1).reason());
    }

    // ── 状态 2：损益全链 —— CLOSE GENERATED 且凭证真落库 POSTED ──

    @Test
    @DisplayName("状态2 损益全链：CLOSE GENERATED + CLOSE- 号段凭证真落库（DRAFT 待过账）")
    void profitDataGeneratesCloseVoucher() {
        postVoucher("PROBE-V1", new Object[][] {
                {subj6001, false, "10000"}, {subj6602, true, "10000"}});
        commitSeed();

        List<CarryoverStepResult> r = carryoverSequenceService.generateSequence(PERIOD, 1L);
        assertEquals("GENERATED", r.get(1).status(),
                "CLOSE 步必须 GENERATED：reason=" + r.get(1).reason());
        assertTrue(r.get(1).voucherId() != null, "GENERATED 必须回填 voucherId");

        // 凭证真落库（REQUIRES_NEW 已提交）；状态必须 DRAFT——结转凭证生成即草稿，
        // 过账是结账工作台第 2 步的独立动作（V1.107 家法，状态 5 的 DISTRIB 前置依赖此形态）
        VoucherEntity v = voucherMapper.selectById(r.get(1).voucherId());
        assertTrue(v != null && "DRAFT".equals(v.getStatus()),
                "结转凭证必须已落库且 DRAFT 待过账：id=" + r.get(1).voucherId()
                        + " status=" + (v == null ? "null" : v.getStatus()));
        assertTrue(v.getVoucherNo().startsWith("CLOSE-"),
                "结转凭证号必须 CLOSE- 号段（利润表排除口径依赖此前缀）：实际=" + v.getVoucherNo());
    }

    // ── 状态 3：幂等重放 —— 二跑 CLOSE SKIPPED「已存在」──

    @Test
    @DisplayName("状态3 幂等重放：二跑 CLOSE SKIPPED「已存在结转凭证」")
    void idempotentReplaySkipped() {
        postVoucher("PROBE-V1", new Object[][] {
                {subj6001, false, "10000"}, {subj6602, true, "10000"}});
        commitSeed();
        carryoverSequenceService.generateSequence(PERIOD, 1L);

        List<CarryoverStepResult> r2 = carryoverSequenceService.generateSequence(PERIOD, 1L);
        assertEquals("SKIPPED", r2.get(1).status(), "二跑 CLOSE 必须幂等 SKIPPED");
        assertTrue(r2.get(1).reason().contains("已存在"),
                "幂等 reason 形态「已存在 N 张结转凭证」：实际=" + r2.get(1).reason());
    }

    // ── 状态 4：期间已结账 —— 空数据全 SKIPPED 非 FAILED（runStep 兜底语义，绝不 500）──

    @Test
    @DisplayName("状态4 期间已结账：空数据三步全 SKIPPED（业务态非系统失败，绝不 FAILED/500）")
    void closedPeriodAllSkipped() {
        // 期间置 closed（generateProfitCarryOver 只 findPeriod 校存在，不校 status）——
        // 空账套下三步各自无数据 SKIPPED，序列不因期间状态整体 500（runStep 兜底）。
        jdbcTemplate.update("UPDATE t_period SET status = 'closed' WHERE period_code = ? AND enterprise_id = 1", PERIOD);
        commitSeed();

        List<CarryoverStepResult> r = carryoverSequenceService.generateSequence(PERIOD, 1L);
        assertEquals(3, r.size());
        for (CarryoverStepResult step : r) {
            assertEquals("SKIPPED", step.status(),
                    "期间已结账 + 空数据必须以业务 SKIPPED 呈现而非 FAILED/500：" + step.step()
                            + " → " + step.status() + "/" + step.reason());
        }
    }

    // ── 状态 5：DISTRIB 余额前置 —— 结转凭证 DRAFT 未过账，4103 无过账余额 ──

    @Test
    @DisplayName("状态5 DISTRIB 前置：结转凭证未过账时 4103 无过账余额 → DISTRIB SKIPPED")
    void distribRequiresPostedCarryover() {
        postVoucher("PROBE-V1", new Object[][] {
                {subj6001, false, "10000"}, {subj6602, true, "10000"}});
        commitSeed();

        // 序列生成 CLOSE 凭证为 DRAFT 未过账 ⇒ 4103 过账余额仍 0 ⇒ DISTRIB 应 SKIPPED
        List<CarryoverStepResult> r = carryoverSequenceService.generateSequence(PERIOD, 1L);
        assertEquals("SKIPPED", r.get(2).status(), "无 4103 过账余额时 DISTRIB 必须 SKIPPED");
        assertTrue(r.get(2).reason().contains("结转") || r.get(2).reason().contains("分配")
                        || r.get(2).reason().contains("配置") || r.get(2).reason().contains("利润"),
                "DISTRIB reason 形态（无余额/科目未配）：实际=" + r.get(2).reason());
    }

    // ── 造数工具 ──

    /** 提交当前测试事务，让后续 REQUIRES_NEW 步事务能读到造数。 */
    private void commitSeed() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    private Long subjectId(String code, String name, String direction) {
        var ids = jdbcTemplate.queryForList(
                "SELECT id FROM t_subject WHERE code = ? AND enterprise_id = 1 AND deleted = 0",
                Long.class, code);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        jdbcTemplate.update("""
                INSERT INTO t_subject(code, name, direction, is_leaf, enterprise_id, level)
                VALUES (?, ?, ?, TRUE, 1, 1)
                """, code, name, direction);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_subject WHERE code = ? AND enterprise_id = 1 AND deleted = 0 "
                        + "ORDER BY id DESC LIMIT 1", Long.class, code);
    }

    private void postVoucher(String no, Object[][] lines) {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo(no);
        v.setPeriod(PERIOD);
        v.setVoucherTypeId(typeId);
        v.setStatus("AUDITED");
        voucherMapper.insert(v);
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] line : lines) {
            VoucherEntryEntity e = new VoucherEntryEntity();
            e.setVoucherId(v.getId());
            e.setSubjectId((Long) line[0]);
            BigDecimal amount = new BigDecimal((String) line[2]);
            if ((Boolean) line[1]) {
                e.setDebit(amount);
            } else {
                e.setCredit(amount);
            }
            voucherEntryMapper.insert(e);
            total = total.add(amount);
        }
        v.setTotalDebit(total);
        v.setTotalCredit(total);
        voucherMapper.updateById(v);
        voucherService.post(v.getId(), 1L);
    }
}
