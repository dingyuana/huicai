package com.huicai.base.voucher.mapper;

import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VoucherTemplateLineMapper 自定义方法真库测试（REQ-2026-131 / P104 第 4 批）
 *
 * <p>取代原 {@code VoucherTemplateLineMapperTest}（mock 被测对象本身）。
 *
 * <p>该表是凭证模板的<b>规范化分录行</b>（与 {@code t_voucher_template.entries}
 * 遗留 JSON 列并存）：4 个生产调用方拿到模板 id 后都靠
 * {@code voucherTemplateService.getLines(id)} 从这里取数，故其正确性直接
 * 影响自动制证。mock 版对此零覆盖。
 */
@DisplayName("P104 VoucherTemplateLineMapper 自定义方法真库")
class VoucherTemplateLineMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private VoucherTemplateLineMapper lineMapper;

    private Long templateId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_voucher_template WHERE deleted = 0 ORDER BY id LIMIT 1",
                Long.class);
    }

    private Long subjectId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_subject WHERE deleted = 0 ORDER BY id LIMIT 1", Long.class);
    }

    private Long insertLine(Long templateId, Long subjectId, int lineOrder) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_voucher_template_line (template_id, subject_id, line_order, "
                        + "dr_amount_template, cr_amount_template, summary_template, "
                        + "enterprise_id, deleted) "
                        + "VALUES (?, ?, ?, 100.00, 0.00, 'P104行', 1, 0) RETURNING id",
                Long.class, templateId, subjectId, lineOrder);
    }

    @Test
    @DisplayName("selectByTemplateId 返回该模板的全部分录行")
    void selectByTemplateIdReturnsLines() {
        Long tplId = templateId();
        Long sid = subjectId();
        Integer before = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher_template_line WHERE template_id = ?",
                Integer.class, tplId);
        insertLine(tplId, sid, 901);
        insertLine(tplId, sid, 902);

        var lines = lineMapper.selectByTemplateId(tplId);
        assertEquals(before + 2, lines.size(),
                "返回行数不符：种子 " + before + " + 新增 2");
    }

    @Test
    @DisplayName("selectByTemplateId 不返回其他模板的行")
    void selectByTemplateIdIsScoped() {
        Long tplId = templateId();
        Long otherTpl = jdbcTemplate.queryForObject(
                "SELECT id FROM t_voucher_template WHERE deleted = 0 AND id <> ? ORDER BY id LIMIT 1",
                Long.class, tplId);
        Long sid = subjectId();
        Long mine = insertLine(tplId, sid, 903);
        insertLine(otherTpl, sid, 904);

        var lines = lineMapper.selectByTemplateId(tplId);
        assertTrue(lines.stream().anyMatch(l -> mine.equals(l.getId())), "未返回本模板的分录行");
        assertTrue(lines.stream().noneMatch(l -> l.getLineOrder() != null && l.getLineOrder() == 904),
                "返回了其他模板的分录行");
    }

    @Test
    @DisplayName("deleteByTemplateId 是软删除（方法名像物理删，实现是 UPDATE deleted=1）")
    void deleteByTemplateIdIsSoftDelete() {
        Long tplId = templateId();
        Long otherTpl = jdbcTemplate.queryForObject(
                "SELECT id FROM t_voucher_template WHERE deleted = 0 AND id <> ? ORDER BY id LIMIT 1",
                Long.class, tplId);
        Long sid = subjectId();
        Long mine = insertLine(tplId, sid, 905);
        Long otherLine = insertLine(otherTpl, sid, 906);

        int affected = lineMapper.deleteByTemplateId(tplId);
        assertTrue(affected >= 1, "未软删任何行");

        // 行仍在，只是 deleted=1 —— 符合铁律 #12
        Integer physical = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher_template_line WHERE id = ?", Integer.class, mine);
        assertEquals(1, physical, "分录行被物理删除 —— 违反逻辑删除铁律 #12");
        Integer deletedFlag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_voucher_template_line WHERE id = ?", Integer.class, mine);
        assertEquals(1, deletedFlag, "deleted 未置 1");

        // 默认查询过滤掉已删除行
        assertTrue(lineMapper.selectByTemplateId(tplId).stream().noneMatch(l -> mine.equals(l.getId())),
                "软删后仍被 selectByTemplateId 查出");

        // 其他模板的行不受影响
        Integer otherLeft = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher_template_line WHERE id = ?",
                Integer.class, otherLine);
        assertEquals(1, otherLeft, "误删了其他模板的分录行");
    }
}