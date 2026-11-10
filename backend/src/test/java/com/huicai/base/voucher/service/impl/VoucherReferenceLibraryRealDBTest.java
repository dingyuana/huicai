package com.huicai.base.voucher.service.impl;

import com.huicai.base.voucher.controller.VoucherTemplateController;
import com.huicai.base.voucher.dto.VoucherTemplateVO;
import com.huicai.base.voucher.service.VoucherTemplateService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.response.R;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P161：参考库（enterprise_id=0）放行通道守卫。
 *
 * <h2>背景</h2>
 * {@code /voucher-templates/reference-list} 与 {@code importFromReference}
 * 在生产（非超管 + GUC=租户）恒空/恒 0：JdbcTemplate 手写
 * {@code WHERE enterprise_id=0} 只绕过应用层过滤，拦不住 DB 层 RLS 谓词。
 * 修法 = 应用层上下文交换（{@code ReferenceContextSwap}，ctx=0 + GUC 刷新，
 * finally 还原），不改 RLS 策略。
 *
 * <h2>🔴 L2 视角诚实标注（家法）</h2>
 * 测试容器用户 {@code test} 是<b>超级用户</b>——Postgres 语义下 RLS
 * （包括 FORCE）压不住超级用户 ⇒ <b>DB 层行为在 L2 从未参战</b>（P154 同族勘误）。
 * 本类能验证的两面：
 * <ol>
 *   <li><b>应用层过滤</b>（{@code EnterpriseDataPermissionInterceptor} 是应用代码，
 *       ctx=租户时会对 MyBatis 查询注入 {@code enterprise_id=租户}）——
 *       参考模板分录/科目富集在无交换时被它杀成 null/空，交换后可见 ⇒ 反证可测；</li>
 *   <li><b>数据正确性</b>：导入后目标企业模板/分录成套出现、科目 remap、幂等。</li>
 * </ol>
 * DB 层（GUC='0' 命中标准谓词）由生产实机验证（rebuild 后 curl 实测），
 * 标准策略 {@code enterprise_policy} 语义自证。
 */
@DisplayName("SPEC-P161：参考库放行通道")
class VoucherReferenceLibraryRealDBTest extends AbstractMapperTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VoucherTemplateController templateController;

    @Autowired
    private VoucherTemplateService templateService;

    private Long targetEntId;

    @BeforeEach
    void setUpTargetEnterprise() {
        // 全新容器库只有迁移种子企业（已自带模板数据，import 会全数跳过）——
        // 造一个运行时企业作为导入目标（V183 同款「运行时注册租户」形态）
        targetEntId = jdbc.queryForObject("""
            INSERT INTO t_enterprise (enterprise_code, enterprise_name, mode, status)
            VALUES ('P161-TARGET', '参考库导入目标企业', 'SME', 'ACTIVE')
            RETURNING id
            """, Long.class);
        assertNotNull(targetEntId);
    }

    @AfterEach
    void clearContext() {
        EnterpriseContextHolder.clear();
    }

    // ───────────────── ① reference-list：交换后分录行与科目富集可见 ─────────────────

    @Test
    @DisplayName("ctx=租户下 reference-list 应见全部 ent0 模板且分录行科目富集非空")
    void referenceListVisibleWithLinesAndSubjects() {
        int seedCount = jdbc.queryForObject(
            "SELECT count(*) FROM t_voucher_template WHERE enterprise_id=0 AND deleted=0", Integer.class);
        assertTrue(seedCount > 0, "前提：迁移种子了 ent0 参考模板（V116/V131）");

        EnterpriseContextHolder.set(targetEntId);
        R<List<VoucherTemplateVO>> resp = templateController.listReferenceTemplates();

        assertNotNull(resp.getData(), "参考库列表不得为 null");
        assertEquals(seedCount, resp.getData().size(),
            "ctx=租户下应完整看到 ent0 参考库（应用层上下文交换生效）");

        VoucherTemplateVO first = resp.getData().get(0);
        assertFalse(first.getLines() == null || first.getLines().isEmpty(),
            "getLines 走 MyBatis（ctx 交换后注入 enterprise_id=0），分录行不得为空");
        boolean anySubjectEnriched = first.getLines().stream()
            .anyMatch(l -> l.getSubjectCode() != null);
        assertTrue(anySubjectEnriched,
            "enrichLines 直调 subjectMapper（t_subject 是 RLS 表），ctx=0 交换后科目代码必须富集——"
                + "无交换时应用层过滤把它杀成 null（生产同形缺陷）");
    }

    // ───────────────── ② import：成套导入 + 科目 remap + 幂等 ─────────────────

    @Test
    @DisplayName("import：目标企业成套导入模板/分录，行科目按 code remap，二次导入幂等 0")
    void importImportsRemapsAndIsIdempotent() {
        int seedCount = jdbc.queryForObject(
            "SELECT count(*) FROM t_voucher_template WHERE enterprise_id=0 AND deleted=0", Integer.class);
        int seedLines = jdbc.queryForObject(
            "SELECT count(*) FROM t_voucher_template_line l JOIN t_voucher_template t ON t.id=l.template_id "
                + "WHERE t.enterprise_id=0 AND l.deleted=0", Integer.class);

        // remap 夹具：目标企业先有一个与 ent0 某 reference 分录科目同 code 的科目 ⇒
        // 导入后该 code 的分录行 subject_id 必须 remap 到目标科目（原 COALESCE 语义）
        String refCode = jdbc.queryForObject(
            "SELECT s.code FROM t_voucher_template_line l JOIN t_voucher_template t ON t.id=l.template_id "
                + "JOIN t_subject s ON s.id=l.subject_id WHERE t.enterprise_id=0 AND l.deleted=0 LIMIT 1",
            String.class);
        assertTrue(refCode != null && !refCode.isEmpty(), "前提：ent0 分录行科目可取到 code");
        Long remappedSubjectId = jdbc.queryForObject(
            "INSERT INTO t_subject (enterprise_id, code, name, level, direction, is_leaf, is_active) "
                + "VALUES (?, ?, 'P161-REMAP-科目', 1, 'debit', TRUE, TRUE) RETURNING id",
            Long.class, targetEntId, refCode);

        EnterpriseContextHolder.set(targetEntId);
        int imported = templateService.importFromReference(targetEntId);
        assertEquals(seedCount, imported, "全新目标企业应把 ent0 参考模板全数导入（生产此前恒 0 = 静默失效）");

        int tgtTpl = jdbc.queryForObject(
            "SELECT count(*) FROM t_voucher_template WHERE enterprise_id=? AND deleted=0 AND created_at > NOW() - INTERVAL '1 day'",
            Integer.class, targetEntId);
        // created_at 过滤是防御性的：全新库 target 表本来就没有历史行
        assertEquals(seedCount, tgtTpl, "target 企业模板数应等于 ent0 参考模板数");

        int tgtLines = jdbc.queryForObject(
            "SELECT count(*) FROM t_voucher_template_line l JOIN t_voucher_template t ON t.id=l.template_id "
                + "WHERE t.enterprise_id=? AND l.deleted=0",
            Integer.class, targetEntId);
        assertEquals(seedLines, tgtLines, "target 企业分录行数应等于 ent0 分录行数（成套导入）");

        // remap 断言：存在分录行 subject_id 指向 target 的 remap 夹具科目
        int remapped = jdbc.queryForObject(
            "SELECT count(*) FROM t_voucher_template_line l JOIN t_voucher_template t ON t.id=l.template_id "
                + "WHERE t.enterprise_id=? AND l.deleted=0 AND l.subject_id=?",
            Integer.class, targetEntId, remappedSubjectId);
        assertTrue(remapped > 0,
            "同 code 科目必须 remap 到目标企业科目（code=" + refCode + "）——原 SQL 的 COALESCE remap 语义保留");

        // 幂等：二次导入 = 0（template_code + enterprise_id 查重）
        int second = templateService.importFromReference(targetEntId);
        assertEquals(0, second, "二次导入必须幂等返回 0（原 NOT EXISTS 语义）");
        int afterSecond = jdbc.queryForObject(
            "SELECT count(*) FROM t_voucher_template WHERE enterprise_id=? AND deleted=0",
            Integer.class, targetEntId);
        assertEquals(seedCount, afterSecond, "幂等导入不得翻倍");
    }
}
