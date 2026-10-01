package com.huicai.base.voucher.mapper;

import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VoucherTemplateMapper 自定义方法真库测试（REQ-2026-131 / P104 第 4 批）
 *
 * <p>取代原 {@code VoucherTemplateMapperTest}（mock 被测对象本身，零信号）。
 * 该 Mapper 有 3 个自定义方法，且实体存在多处 {@code exist = false} 幽灵字段
 * （{@code classification}/{@code source}/{@code direction}/{@code description}）
 * —— 这些字段在 DB 里无对应列，mock 版完全无法暴露由此产生的映射问题。
 */
@DisplayName("P104 VoucherTemplateMapper 自定义方法真库")
class VoucherTemplateMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private VoucherTemplateMapper voucherTemplateMapper;

    @Test
    @DisplayName("selectAllActive 只返回 is_active=true 的模板")
    void selectAllActiveFiltersInactive() {
        List<VoucherTemplateEntity> active = voucherTemplateMapper.selectAllActive();
        assertNotNull(active, "selectAllActive 返回 null");
        assertTrue(active.stream().allMatch(t -> Boolean.TRUE.equals(t.getIsActive())),
                "返回了非激活模板");
        assertTrue(!active.isEmpty(), "种子应至少有一个激活模板");
    }

    @Test
    @DisplayName("matchByDimensions 返回的实体 id 必须有值（4 个调用方全靠它取分录行）")
    void matchByDimensionsReturnsUsableId() {
        VoucherTemplateEntity t =
                voucherTemplateMapper.matchByDimensions("BANK", "OUTPUT_INVOICE", "debit");
        assertNotNull(t, "按 OUTPUT_INVOICE 未匹配到种子模板");
        assertNotNull(t.getId(), "返回实体 id 为 null ⇒ TaxServiceImpl/BusinessDocServiceImpl/"
                + "AutoGenerationService 四处 getLines(template.getId()) 全部拿不到分录");

        // 命中的确实是种子行 TPL_SALES
        String code = jdbcTemplate.queryForObject(
                "SELECT template_code FROM t_voucher_template WHERE id = ?",
                String.class, t.getId());
        assertEquals("TPL_SALES", code, "命中的不是种子模板 TPL_SALES");
    }

    @Test
    @DisplayName("⚠️ 记录现状：SELECT * + 实体返回导致别名列未映射（businessType 为 null）")
    void aliasedColumnsAreNotMappedBySelectStar() {
        VoucherTemplateEntity t =
                voucherTemplateMapper.matchByDimensions("BANK", "OUTPUT_INVOICE", "debit");
        assertNotNull(t);
        // @Select("SELECT * ...") 走 MyBatis 自动映射，按**列名**找属性；
        // 实体属性叫 businessType 而列名是 doc_type ⇒ 不匹配 ⇒ null。
        // 同理未映射的还有 template_code / entries。
        // 当前**不是活缺陷**：4 个调用方只用 getId()，分录走 t_voucher_template_line。
        // 但这是个易踩的陷阱 —— 若将来有人改用 template.getBusinessType()，会拿到 null。
        assertNull(t.getBusinessType(),
                "businessType 竟有值了 —— 若已改用显式别名，请同步更新本测试");
        String docType = jdbcTemplate.queryForObject(
                "SELECT doc_type FROM t_voucher_template WHERE id = ?",
                String.class, t.getId());
        assertEquals("OUTPUT_INVOICE", docType, "DB 中该行的 doc_type 与预期不符");
    }

    @Test
    @DisplayName("matchByDimensions 不命中时返回 null（不得返回任意模板）")
    void matchByDimensionsReturnsNullWhenNoMatch() {
        assertNull(voucherTemplateMapper.matchByDimensions("X", "NO_SUCH_DOC_TYPE", "debit"),
                "不存在的 doc_type 却返回了模板 ⇒ 匹配逻辑失效");
    }

    @Test
    @DisplayName("selectActiveByClassification 返回 null 是预期（该字段是幽灵字段）")
    void selectActiveByClassificationIsNullForGhostField() {
        // classification 被标 exist=false，实体无此列 ⇒ 该方法在真实 DB 上无法按分类过滤。
        // 记录当前行为：若将来补上该列并实现过滤，本断言会失败并提示更新。
        assertNull(voucherTemplateMapper.selectActiveByClassification("销售收款"),
                "selectActiveByClassification 竟返回了值 —— 若已实现分类过滤请同步更新本测试");
    }

    @Test
    @DisplayName("反证：SELECT * 在真实表上可执行（未映射列不应导致映射异常）")
    void selectStarDoesNotBreakMapping() {
        VoucherTemplateEntity t =
                voucherTemplateMapper.matchByDimensions("BANK", "OUTPUT_INVOICE", "debit");
        assertNotNull(t);
        // entries 是真实 jsonb 列，实体未映射它；此处用原生 SQL 验证其内容可用，
        // 说明「未映射」是实体侧的选择，而非列不存在。
        String entries = jdbcTemplate.queryForObject(
                "SELECT entries::text FROM t_voucher_template WHERE id = ?",
                String.class, t.getId());
        assertNotNull(entries, "entries 列为空");
        assertTrue(entries.trim().startsWith("["), "entries 不是 JSON 数组：" + entries);
    }
}