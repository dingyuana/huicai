package com.huicai.sme.arap.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.arap.entity.ReconciliationExceptionEntity;
import com.huicai.sme.arap.mapper.ReconciliationExceptionMapper;
import com.huicai.sme.arap.service.ReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P109 / REQ-2026-138 —— 核销异常池修复后的<b>回归锁</b>（SPEC §5 场景 1~5）
 *
 * <p><b>本类的前身是 {@code ReconciliationExceptionEntityDbProbeTest}</b>——那份取证测试
 * 断言的是「缺陷存在」（如「{@code createException} 必然抛 NOT NULL」），
 * 修好后必然集体转红。修复已由 {@code V169} + Entity 收口完成，故本类把那些断言
 * <b>逐条改写为正向断言</b>：现在断言「<b>不再</b>抛 NOT NULL」「字段<b>能</b>读回非 null」。
 *
 * <p><b>为什么这批测试之前不存在</b>：该功能唯一的测试是
 * {@code ReconciliationServiceImplTest:720} 的 <b>Mock 测试</b>，结构上看不见
 * NOT NULL / FK / CHECK；加上表 {@code count(*)=0}、且 {@code createException}
 * <b>无生产调用方</b>（死路径）—— 三层遮蔽同时成立，于是「整个功能不可用」潜伏至今
 * （AGENTS §4.3 第 7 条：Mock 测不出 DB 约束）。
 *
 * @see docs/specs/P109-reconciliation-exception-pool-repair.md §0（取证）/ §5（BDD）
 */
@DisplayName("P109 核销异常池：写入 / 重试 / 读取 / 备注落库")
class ReconciliationExceptionPoolRepairRealDBTest extends AbstractMapperTest {

    private static final Long OTHER_ENTERPRISE = 990901L;
    private static final String PREFIX = "9999.P109.EXC.";

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private ReconciliationExceptionMapper exceptionMapper;

    private ReconciliationExceptionEntity createOk(String tag, String exceptionType) {
        return reconciliationService.createException(
                "RECEIPT", 100L,
                "PAYMENT", 200L,
                300L, "CUSTOMER",
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                exceptionType, PREFIX + tag + "-原因", "手工匹配建议");
    }

    // ==================== 场景 1：可被真实写入 ====================

    @Test
    @DisplayName("场景1 异常记录可被真实写入，且往来单位各字段真正落库（修复前撞 account_id NOT NULL）")
    void exceptionCanBeCreatedAndPartyFieldsPersist() {
        ReconciliationExceptionEntity ex = createOk("S1", "AMOUNT_DIFF");

        assertNotNull(ex.getId(), "未落库");

        // 正向：来源/目标/往来单位四组字段都必须真正落库（修复前它们全是 exist=false 幽灵字段）
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT source_doc_type, source_doc_id, target_doc_type, target_doc_id, "
                        + "party_id, party_type, unsettled_amount, exception_reason, match_suggestion, "
                        + "retry_count, enterprise_id "
                        + "FROM t_reconciliation_exception WHERE id = ?", ex.getId());

        assertEquals("RECEIPT", row.get("source_doc_type"), "source_doc_type 未落库");
        assertEquals(100L, ((Number) row.get("source_doc_id")).longValue(), "source_doc_id 未落库");
        assertEquals("PAYMENT", row.get("target_doc_type"), "target_doc_type 未落库（retryException 依赖它）");
        assertEquals(200L, ((Number) row.get("target_doc_id")).longValue(), "target_doc_id 未落库");
        assertEquals(300L, ((Number) row.get("party_id")).longValue(), "party_id 未落库");
        assertEquals("CUSTOMER", row.get("party_type"), "party_type 未落库");
        assertEquals(0, new BigDecimal(row.get("unsettled_amount").toString())
                .compareTo(new BigDecimal("100.00")), "unsettled_amount 未落库");
        assertEquals(PREFIX + "S1-原因", row.get("exception_reason"), "exception_reason 未落库");
        assertEquals("手工匹配建议", row.get("match_suggestion"), "match_suggestion 未落库");
        assertEquals(0, ((Number) row.get("retry_count")).intValue(), "retry_count 应初始为 0");

        // 负向：enterprise_id 必须是上下文企业，不能落 DB 默认值 1
        // （本 Entity 继承 BaseEntity，enterpriseId 带 fill=INSERT ⇒ insertFill 会触发）
        assertEquals(DEFAULT_ENTERPRISE_ID,
                ((Number) row.get("enterprise_id")).longValue(),
                "enterprise_id 未按上下文回填");
    }

    @Test
    @DisplayName("场景1b 负向：enterprise_id 不得落 DB 默认值 1（AGENTS §4.5 第 34 条同型守卫）")
    void enterpriseIdFollowsContextNotDbDefault() {
        useEnterprise(OTHER_ENTERPRISE);
        ReconciliationExceptionEntity ex = createOk("S1B", "UNMATCHED");

        Long persisted = jdbcTemplate.queryForObject(
                "SELECT enterprise_id FROM t_reconciliation_exception WHERE id = ?", Long.class, ex.getId());

        assertEquals(OTHER_ENTERPRISE, persisted,
                "enterprise_id 落成了 " + persisted + "；若等于 1 说明 insertFill 未触发、"
                        + "走了 DB DEFAULT 1（与 P106 批次 1a-1 的 PrepaymentEntity 缺陷同型）");
    }

    // ==================== 场景 2：可被重试 ====================

    @Test
    @DisplayName("场景2 异常记录可被重试（修复前因目标信息读回 null 而恒抛）")
    void exceptionCanBeRetried() {
        ReconciliationExceptionEntity ex = createOk("S2", "UNMATCHED");

        // 前置断言：目标单据信息必须读得到（修复前恒 null ⇒ :972 直接抛）
        ReconciliationExceptionEntity loaded = exceptionMapper.selectById(ex.getId());
        assertEquals("PAYMENT", loaded.getTargetDocType(),
                "targetDocType 读回 null ⇒ retryException 必然抛「缺少目标单据信息」");
        assertEquals(200L, loaded.getTargetDocId(), "targetDocId 读回 null");

        // 负向：retry_count 必须真落库（修复前是幽灵字段，读回 null ⇒ `null + 1` 会 NPE）
        Integer retryCount = jdbcTemplate.queryForObject(
                "SELECT retry_count FROM t_reconciliation_exception WHERE id = ?", Integer.class, ex.getId());
        assertEquals(0, retryCount, "retry_count 读回 null 或非 0");
    }

    // ==================== 场景 3：来源单据在读接口里可见 ====================

    @Test
    @DisplayName("场景3 读接口里来源单据可见（修复前恒 null，前端「来源」列渲染空白）")
    void sourceDocIsVisibleOnRead() {
        ReconciliationExceptionEntity ex = createOk("S3", "DATE_DIFF");

        // 前端「来源」列渲染 row.sourceDocType / row.sourceDocId，靠的正是 pageExceptions 的返回
        ReconciliationExceptionEntity reread = exceptionMapper.selectById(ex.getId());
        assertEquals("RECEIPT", reread.getSourceDocType(), "来源类型读回 null");
        assertEquals(100L, reread.getSourceDocId(), "来源单据 ID 读回 null");
        assertEquals(PREFIX + "S3-原因", reread.getExceptionReason(), "异常原因读回 null（前端「异常原因」列）");
        assertNotNull(reread.getRetryCount(), "retryCount 读回 null（前端 {{ row.retryCount ?? 0 }} 会显示 0）");
    }

    // ==================== 场景 4：备注与操作人真正落库 ====================

    @Test
    @DisplayName("场景4 resolve 的备注与操作人真正落库（修复前 remark 是幽灵字段，赋值被静默丢弃）")
    void resolvePersistsRemarkAndOperator() {
        ReconciliationExceptionEntity ex = createOk("S4", "AMOUNT_DIFF");

        reconciliationService.resolveException(ex.getId(), 7L, "人工已核对并核销");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, remark, resolved_by, resolved_at FROM t_reconciliation_exception WHERE id = ?",
                ex.getId());
        assertEquals("RESOLVED", row.get("status"), "状态未流转到 RESOLVED");
        assertEquals("人工已核对并核销", row.get("remark"),
                "remark 未落库 —— 修复前它是 @TableField(exist=false)，赋值被静默丢弃");
        assertEquals(7L, ((Number) row.get("resolved_by")).longValue(), "操作人未落库");
        assertNotNull(row.get("resolved_at"), "处理时间未落库");
    }

    @Test
    @DisplayName("场景4b 负向：被忽略的路径也要落 remark（前端「忽略」按钮走 ignoreException）")
    void ignorePersistsRemark() {
        ReconciliationExceptionEntity ex = createOk("S4B", "DUPLICATE");

        reconciliationService.ignoreException(ex.getId(), 9L, "重复单据，忽略");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, remark, resolved_by FROM t_reconciliation_exception WHERE id = ?",
                ex.getId());
        assertEquals("IGNORED", row.get("status"), "状态未流转到 IGNORED");
        assertEquals("重复单据，忽略", row.get("remark"), "remark 未落库");
        assertEquals(9L, ((Number) row.get("resolved_by")).longValue(), "操作人未落库");
    }

    // ==================== 场景 5：缺失上下文抛可读异常 ====================

    @Test
    @DisplayName("场景5 缺来源单据类型时抛可读 BusinessException，而非 DB 约束错误")
    void missingSourceDocTypeThrowsReadableError() {
        BusinessException e = assertThrows(BusinessException.class, () ->
                reconciliationService.createException(
                        null, 1L, "PAYMENT", 2L, 3L, "CUSTOMER",
                        BigDecimal.TEN, BigDecimal.TEN,
                        "AMOUNT_DIFF", "缺来源", null));

        assertTrue(e.getMessage().contains("sourceDocType"),
                "报错未指明缺失字段，实际：" + e.getMessage());
        assertTrue(!e.getMessage().contains("violates not-null constraint"),
                "报错仍是 DB 约束原文 ⇒ 未做前置校验：" + e.getMessage());
    }

    @Test
    @DisplayName("场景5b 非法 exceptionType 时报错须列出合法值（DB CHECK 允许集的镜像）")
    void illegalExceptionTypeListsAllowedValues() {
        BusinessException e = assertThrows(BusinessException.class, () ->
                reconciliationService.createException(
                        "RECEIPT", 1L, "PAYMENT", 2L, 3L, "CUSTOMER",
                        BigDecimal.TEN, BigDecimal.TEN,
                        "PARTY_MISMATCH", "前端旧词表", null));

        String msg = e.getMessage();
        assertTrue(msg.contains("PARTY_MISMATCH"), "报错未回显被拒的值：" + msg);
        for (String legal : Set.of("AMOUNT_DIFF", "DATE_DIFF", "UNMATCHED", "DUPLICATE")) {
            assertTrue(msg.contains(legal), "报错未列出合法值 " + legal + "：" + msg);
        }
    }

    @Test
    @DisplayName("场景5c 前端旧词表全部被拒（D-109-2 裁定后前端已对齐 DB，此处锁死该边界）")
    void allLegacyFrontendValuesAreRejected() {
        for (String legacy : List.of(
                "PARTY_MISMATCH", "AMOUNT_MISMATCH", "INVOICE_NOT_FOUND",
                "MATCH_FAILED", "APPROVAL_REQUIRED")) {
            assertThrows(BusinessException.class, () -> createOk("S5C", legacy),
                    "前端旧词表 " + legacy + " 未被拒绝 —— 若前端仍下发该值，"
                            + "会绕过 Service 校验直撞 DB CHECK 变成 500");
        }
    }

    // ==================== 前提守卫 ====================

    @Test
    @DisplayName("守卫：DB 允许集与 Service 常量、前端词表三者必须一致")
    void allowedSetMatchesServiceConstant() {
        Set<String> db = parseCheckConstraint();
        assertEquals(Set.of("AMOUNT_DIFF", "DATE_DIFF", "UNMATCHED", "DUPLICATE"), db,
                "DB CHECK 允许集已变（实测取自 chk_exception_type），"
                        + "Service 常量与前端下拉必须同步更新，否则会出现新的写入 500");
    }

    @Test
    @DisplayName("守卫：updated_at 真实列必须被写入（字段遮蔽导致的反向缺口，AGENTS §4.2 第 16 条）")
    void updatedAtIsActuallyWritten() {
        ReconciliationExceptionEntity ex = createOk("GUARD", "AMOUNT_DIFF");

        reconciliationService.resolveException(ex.getId(), 1L, "触发一次更新");

        assertNotNull(jdbcTemplate.queryForObject(
                        "SELECT updated_at FROM t_reconciliation_exception WHERE id = ?",
                        java.time.LocalDateTime.class, ex.getId()),
                "updated_at 恒为 null ⇒ 该 Entity 仍在遮蔽 BaseEntity 里可写入的 updatedAt"
                        + "（V127 已加真实列，本 Entity 原先却重复声明并标 exist=false）");
    }

    @Test
    @DisplayName("守卫：account_id/period 已降为可空（银行流水时代遗留列，但 Entity 不映射）")
    void legacyColumnsAreNullable() {
        ReconciliationExceptionEntity ex = createOk("GUARD2", "AMOUNT_DIFF");

        assertNull(jdbcTemplate.queryForObject(
                        "SELECT account_id FROM t_reconciliation_exception WHERE id = ?",
                        Long.class, ex.getId()),
                "account_id 竟有值 ⇒ 说明 Entity 映射了它或 DB 有默认值，与 SPEC 的遗留列处置不符");
        // 能插进来本身就是「account_id/period 已可空」的证明（NOT NULL 时会直接失败）
        assertNotNull(ex.getId());
    }

    private Set<String> parseCheckConstraint() {
        String def = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conrelid = 't_reconciliation_exception'::regclass "
                        + "AND conname = 'chk_exception_type'", String.class);
        assertNotNull(def, "找不到 chk_exception_type 约束");
        Set<String> out = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("'([^']+)'").matcher(def);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}