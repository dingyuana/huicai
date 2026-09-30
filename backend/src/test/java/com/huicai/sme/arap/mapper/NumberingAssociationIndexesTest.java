package com.huicai.sme.arap.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编号关联体系 — 索引与双向关联字段存在性测试（L2 / @SlowTest）
 *
 * <h3>历史说明（REQ-2026-113 / P99 A 类分诊）</h3>
 * 本测试此前断言 14 个索引（原注释称「由 V64 Migration 创建」），全部不存在：
 * <pre>
 *   idx_receivable_doc_no / invoice_no / voucher_no   （3）
 *   idx_payable_doc_no    / invoice_no / voucher_no   （3）
 *   idx_settle_entry_receivable / idx_settle_entry_payable（2）
 *   idx_voucher_source_doc_no / idx_voucher_source_doc_type（2）
 *   idx_input_invoice_doc_no / idx_input_invoice_voucher_no（2）
 *   idx_business_doc_voucher_no / idx_arap_settlement_voucher_no（2）
 * </pre>
 * 核验结论：<b>该设计已废弃，且从不存在于当前 schema</b>。
 * <ul>
 *   <li>{@code docs/CORE-技术方案.md} §4.2 明文「t_receivable/t_payable 已删除，
 *       统一使用 t_business_doc 表」；</li>
 *   <li>全仓<b>无任何 DROP migration</b> 删除这两张表 —— 说明合并发生在 V1 baseline
 *       之前，两表在当前迁移链中从未被创建；</li>
 *   <li>V60~V91 区间<b>无任何 migration 文件</b>，所谓「V64」并不存在，
 *       其索引自然无从产生。</li>
 * </ul>
 * 因此本测试<b>不是数据准备不足，而是在守护一套已被废弃的数据模型</b>。若为让测试
 * 转绿而补建这些索引，会在不存在的表上建索引（必然失败并诱导误判），或反之在现存表
 * 上补 {@code source_doc_no}/{@code source_doc_type} 等旧列，复活已废弃的双向编号结构，
 * 与 {@code t_business_doc} 单一模型冲突。
 *
 * <p>本次改为断言<b>新模型下真正支撑编号关联的索引与字段</b>，保持「编号关联有 DB 层
 * 支撑」这一测试意图不变（铁律 #9 编号关联溯源）。
 *
 * @see docs/development/plans/2026-09-28-slow-test-triage.md §二 A 类
 */
@SlowTest
@DisplayName("编号关联 - 索引与双向关联字段存在性验证")
public class NumberingAssociationIndexesTest extends AbstractMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 新模型下支撑编号关联的索引（已逐个实测存在于当前 schema） */
    private static final String[] ASSOCIATION_INDEXES = {
            // 编号唯一性
            "uq_doc_no_type",                 // t_business_doc (doc_type, doc_no) WHERE deleted=0
            "uq_settlement_no",               // t_arap_settlement (settlement_no)
            "uq_input_invoice_no",            // t_input_invoice (invoice_no)
            "uq_output_invoice_no",           // t_output_invoice (invoice_no)
            // 实体间关联
            "idx_voucher_business_doc_id",            // t_voucher -> t_business_doc
            "idx_doc_entry_doc_id",                   // t_business_doc_entry -> t_business_doc
            "idx_business_doc_invoice_id",            // t_business_doc -> 发票
            "idx_business_doc_settlement_account_id", // t_business_doc -> 结算账户
            "idx_arap_settlement_entry_business_doc_id", // 核销明细 -> 业务单据
            "idx_settle_entry_settle",                // 核销明细 -> 核销单
    };

    /** 编号双向关联：xxx_id 外键 + xxx_no 编号冗余（铁律 #9） */
    private static final String[][] ASSOCIATION_COLUMNS = {
            {"t_voucher", "business_doc_id"},          // 凭证 -> 业务单据
            {"t_business_doc", "voucher_id"},          // 业务单据 -> 凭证（反向）
            {"t_business_doc", "voucher_no"},          // 凭证号编号冗余
            {"t_business_doc", "invoice_no"},          // 发票号编号冗余
            {"t_arap_settlement", "voucher_id"},       // 核销单 -> 凭证
            {"t_arap_settlement", "source_doc_id"},    // 核销单 -> 来源单据
            {"t_arap_settlement", "source_doc_type"},  // 核销单 -> 来源单据类型
    };

    private boolean indexExists(String indexName) {
        List<Boolean> result = jdbcTemplate.query(
                "SELECT COUNT(*) > 0 FROM pg_indexes WHERE indexname = ?",
                (rs, rowNum) -> rs.getBoolean(1), indexName);
        return result.get(0);
    }

    private boolean columnExists(String table, String column) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        return n != null && n > 0;
    }

    // ==================== 编号唯一性索引 ====================

    @Test
    @DisplayName("索引: uq_doc_no_type 存在（业务单据编号按类型唯一）")
    void index_doc_no_type_unique_exists() {
        assertTrue(indexExists("uq_doc_no_type"), "索引 uq_doc_no_type 应存在");
    }

    @Test
    @DisplayName("索引: uq_settlement_no 存在（核销单编号唯一）")
    void index_settlement_no_unique_exists() {
        assertTrue(indexExists("uq_settlement_no"), "索引 uq_settlement_no 应存在");
    }

    @Test
    @DisplayName("索引: uq_input_invoice_no 存在（进项发票号唯一）")
    void index_input_invoice_no_unique_exists() {
        assertTrue(indexExists("uq_input_invoice_no"), "索引 uq_input_invoice_no 应存在");
    }

    @Test
    @DisplayName("索引: uq_output_invoice_no 存在（销项发票号唯一）")
    void index_output_invoice_no_unique_exists() {
        assertTrue(indexExists("uq_output_invoice_no"), "索引 uq_output_invoice_no 应存在");
    }

    // ==================== 实体关联索引 ====================

    @Test
    @DisplayName("索引: idx_voucher_business_doc_id 存在（凭证→业务单据）")
    void index_voucher_business_doc_id_exists() {
        assertTrue(indexExists("idx_voucher_business_doc_id"),
                "索引 idx_voucher_business_doc_id 应存在");
    }

    @Test
    @DisplayName("索引: idx_doc_entry_doc_id 存在（单据分录→业务单据）")
    void index_doc_entry_doc_id_exists() {
        assertTrue(indexExists("idx_doc_entry_doc_id"), "索引 idx_doc_entry_doc_id 应存在");
    }

    @Test
    @DisplayName("索引: idx_business_doc_invoice_id 存在（业务单据→发票）")
    void index_business_doc_invoice_id_exists() {
        assertTrue(indexExists("idx_business_doc_invoice_id"),
                "索引 idx_business_doc_invoice_id 应存在");
    }

    @Test
    @DisplayName("索引: idx_business_doc_settlement_account_id 存在（业务单据→结算账户）")
    void index_business_doc_settlement_account_id_exists() {
        assertTrue(indexExists("idx_business_doc_settlement_account_id"),
                "索引 idx_business_doc_settlement_account_id 应存在");
    }

    @Test
    @DisplayName("索引: idx_arap_settlement_entry_business_doc_id 存在（核销明细→业务单据）")
    void index_settlement_entry_business_doc_id_exists() {
        assertTrue(indexExists("idx_arap_settlement_entry_business_doc_id"),
                "索引 idx_arap_settlement_entry_business_doc_id 应存在");
    }

    @Test
    @DisplayName("索引: idx_settle_entry_settle 存在（核销明细→核销单）")
    void index_settle_entry_settle_exists() {
        assertTrue(indexExists("idx_settle_entry_settle"), "索引 idx_settle_entry_settle 应存在");
    }

    // ==================== 编号双向关联字段（铁律 #9）====================

    @Test
    @DisplayName("双向关联: 凭证↔业务单据 两侧字段均存在")
    void bidirectional_voucher_businessDoc_columnsExist() {
        assertTrue(columnExists("t_voucher", "business_doc_id"), "t_voucher.business_doc_id 应存在");
        assertTrue(columnExists("t_business_doc", "voucher_id"), "t_business_doc.voucher_id 应存在");
    }

    @Test
    @DisplayName("编号冗余: 业务单据上的 voucher_no / invoice_no 均存在")
    void redundantNumbers_on_businessDoc_exist() {
        assertTrue(columnExists("t_business_doc", "voucher_no"), "t_business_doc.voucher_no 应存在");
        assertTrue(columnExists("t_business_doc", "invoice_no"), "t_business_doc.invoice_no 应存在");
    }

    @Test
    @DisplayName("溯源字段: 核销单的 voucher_id / source_doc_id / source_doc_type 均存在")
    void settlement_trace_columnsExist() {
        assertTrue(columnExists("t_arap_settlement", "voucher_id"), "t_arap_settlement.voucher_id 应存在");
        assertTrue(columnExists("t_arap_settlement", "source_doc_id"), "t_arap_settlement.source_doc_id 应存在");
        assertTrue(columnExists("t_arap_settlement", "source_doc_type"), "t_arap_settlement.source_doc_type 应存在");
    }

    // ==================== 负向：废弃设计不得复活 ====================

    @Test
    @DisplayName("负向: 已废弃的 t_receivable / t_payable 相关索引不得存在")
    void deprecated_receivablePayable_indexes_absent() {
        String[] deprecated = {
                "idx_receivable_doc_no", "idx_receivable_invoice_no", "idx_receivable_voucher_no",
                "idx_payable_doc_no", "idx_payable_invoice_no", "idx_payable_voucher_no",
                "idx_settle_entry_receivable", "idx_settle_entry_payable",
                "idx_voucher_source_doc_no", "idx_voucher_source_doc_type",
                "idx_business_doc_voucher_no", "idx_arap_settlement_voucher_no",
                "idx_input_invoice_doc_no", "idx_input_invoice_voucher_no",
        };
        for (String idx : deprecated) {
            assertFalse(indexExists(idx),
                    "已废弃设计不应存在索引 " + idx + "（其对应表 t_receivable/t_payable 已并入 t_business_doc）");
        }
    }

    @Test
    @DisplayName("负向: 已废弃的 t_receivable / t_payable 表本身不存在")
    void deprecated_tables_absent() {
        for (String table : new String[]{"t_receivable", "t_payable"}) {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = ?",
                    Integer.class, table);
            assertNotNull(n);
            assertEquals(0, n, "表 " + table + " 已并入 t_business_doc，不应存在");
        }
    }

    // ==================== 汇总检查 ====================

    @Test
    @DisplayName("汇总: 编号关联的全部索引与字段均应存在")
    void all_association_indexesAndColumns_summary() {
        StringBuilder missing = new StringBuilder();
        for (String idx : ASSOCIATION_INDEXES) {
            if (!indexExists(idx)) {
                missing.append("  缺失索引: ").append(idx).append('\n');
            }
        }
        for (String[] col : ASSOCIATION_COLUMNS) {
            if (!columnExists(col[0], col[1])) {
                missing.append("  缺失字段: ").append(col[0]).append('.').append(col[1]).append('\n');
            }
        }
        assertTrue(missing.length() == 0,
                "编号关联结构不完整：\n" + missing);
    }
}
