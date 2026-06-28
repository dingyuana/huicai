package com.huicai.module.finance.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编号关联体系 - 索引存在性测试 (L2 / @SlowTest)
 *
 * 验证 V64 Migration 创建的所有索引确实存在于数据库中。
 * 使用 PostgreSQL 系统表 pg_index + pg_class 查询。
 */
@SlowTest
@DisplayName("编号关联 - 索引存在性验证")
public class NumberingAssociationIndexesTest extends AbstractMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 通用索引存在性检查
     */
    private boolean indexExists(String indexName) {
        String sql = """
            SELECT COUNT(*) > 0 FROM pg_indexes WHERE indexname = ?
            """;
        List<Boolean> result = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getBoolean(1), indexName);
        return result.get(0);
    }

    // ==================== 进项发票索引 ====================

    @Test
    @DisplayName("索引: idx_input_invoice_doc_no 存在")
    void index_input_invoice_doc_no_exists() {
        assertTrue(indexExists("idx_input_invoice_doc_no"),
            "索引 idx_input_invoice_doc_no 应存在");
    }

    @Test
    @DisplayName("索引: idx_input_invoice_voucher_no 存在")
    void index_input_invoice_voucher_no_exists() {
        assertTrue(indexExists("idx_input_invoice_voucher_no"),
            "索引 idx_input_invoice_voucher_no 应存在");
    }

    // ==================== 应收单索引 ====================

    @Test
    @DisplayName("索引: idx_receivable_doc_no 存在")
    void index_receivable_doc_no_exists() {
        assertTrue(indexExists("idx_receivable_doc_no"),
            "索引 idx_receivable_doc_no 应存在");
    }

    @Test
    @DisplayName("索引: idx_receivable_voucher_no 存在")
    void index_receivable_voucher_no_exists() {
        assertTrue(indexExists("idx_receivable_voucher_no"),
            "索引 idx_receivable_voucher_no 应存在");
    }

    @Test
    @DisplayName("索引: idx_receivable_invoice_no 存在")
    void index_receivable_invoice_no_exists() {
        assertTrue(indexExists("idx_receivable_invoice_no"),
            "索引 idx_receivable_invoice_no 应存在");
    }

    // ==================== 应付单索引 ====================

    @Test
    @DisplayName("索引: idx_payable_doc_no 存在")
    void index_payable_doc_no_exists() {
        assertTrue(indexExists("idx_payable_doc_no"),
            "索引 idx_payable_doc_no 应存在");
    }

    @Test
    @DisplayName("索引: idx_payable_voucher_no 存在")
    void index_payable_voucher_no_exists() {
        assertTrue(indexExists("idx_payable_voucher_no"),
            "索引 idx_payable_voucher_no 应存在");
    }

    @Test
    @DisplayName("索引: idx_payable_invoice_no 存在")
    void index_payable_invoice_no_exists() {
        assertTrue(indexExists("idx_payable_invoice_no"),
            "索引 idx_payable_invoice_no 应存在");
    }

    // ==================== 凭证索引 ====================

    @Test
    @DisplayName("索引: idx_voucher_source_doc_no 存在")
    void index_voucher_source_doc_no_exists() {
        assertTrue(indexExists("idx_voucher_source_doc_no"),
            "索引 idx_voucher_source_doc_no 应存在");
    }

    @Test
    @DisplayName("索引: idx_voucher_source_doc_type 存在")
    void index_voucher_source_doc_type_exists() {
        assertTrue(indexExists("idx_voucher_source_doc_type"),
            "索引 idx_voucher_source_doc_type 应存在");
    }

    // ==================== 业务单据索引 ====================

    @Test
    @DisplayName("索引: idx_business_doc_voucher_no 存在")
    void index_business_doc_voucher_no_exists() {
        assertTrue(indexExists("idx_business_doc_voucher_no"),
            "索引 idx_business_doc_voucher_no 应存在");
    }

    // ==================== 核销单索引 ====================

    @Test
    @DisplayName("索引: idx_arap_settlement_voucher_no 存在")
    void index_arap_settlement_voucher_no_exists() {
        assertTrue(indexExists("idx_arap_settlement_voucher_no"),
            "索引 idx_arap_settlement_voucher_no 应存在");
    }

    // ==================== 核销明细索引 ====================

    @Test
    @DisplayName("索引: idx_settle_entry_receivable 存在")
    void index_settle_entry_receivable_exists() {
        assertTrue(indexExists("idx_settle_entry_receivable"),
            "索引 idx_settle_entry_receivable 应存在");
    }

    @Test
    @DisplayName("索引: idx_settle_entry_payable 存在")
    void index_settle_entry_payable_exists() {
        assertTrue(indexExists("idx_settle_entry_payable"),
            "索引 idx_settle_entry_payable 应存在");
    }

    // ==================== 汇总检查 ====================

    @Test
    @DisplayName("汇总: 所有 V64 索引应全部存在（共 14 个）")
    void all_indexes_summary() {
        String[] expectedIndexes = {
            "idx_input_invoice_doc_no",
            "idx_input_invoice_voucher_no",
            "idx_receivable_doc_no",
            "idx_receivable_voucher_no",
            "idx_receivable_invoice_no",
            "idx_payable_doc_no",
            "idx_payable_voucher_no",
            "idx_payable_invoice_no",
            "idx_voucher_source_doc_no",
            "idx_voucher_source_doc_type",
            "idx_business_doc_voucher_no",
            "idx_arap_settlement_voucher_no",
            "idx_settle_entry_receivable",
            "idx_settle_entry_payable"
        };

        int found = 0;
        for (String idx : expectedIndexes) {
            if (indexExists(idx)) {
                found++;
            } else {
                fail("索引 " + idx + " 不存在");
            }
        }

        assertEquals(expectedIndexes.length, found,
            "应找到 " + expectedIndexes.length + " 个索引，实际找到 " + found);
    }
}
