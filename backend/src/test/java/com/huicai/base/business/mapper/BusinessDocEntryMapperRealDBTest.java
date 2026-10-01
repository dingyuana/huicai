package com.huicai.base.business.mapper;

import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BusinessDocEntryMapper 自定义方法真库测试（REQ-2026-131 / P104 第 4 批）
 *
 * <p>取代原 {@code BusinessDocEntryMapperTest}（mock 被测对象本身）。
 *
 * <p><b>重点</b>：{@code deleteByDocId} 与 {@code physicalDeleteAll} 是<b>物理删除</b>
 * （SQL 里没有 {@code deleted = 1}）。它们被 {@code SystemClearController} 的
 * 「清空业务单据」调用，是刻意的全量维护操作，**不属于铁律 #12 违规**
 * （后者针对的是业务对象的常规删除路径）。本类锁定这一区分，防止将来
 * 有人误以为它们该走软删。
 *
 * <p>{@code t_business_doc_entry.doc_id} 与 {@code subject_id} 均有外键，
 * 故夹具必须先造出合法父行。
 */
@DisplayName("P104 BusinessDocEntryMapper 自定义方法真库")
class BusinessDocEntryMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private BusinessDocEntryMapper businessDocEntryMapper;

    private Long createDoc(String suffix) {
        // chk_doc_type 只允许 RECEIPT/PAYMENT/EXPENSE/INVOICE_IN/INVOICE_OUT/
        // OTHER_RECEIVABLE/OTHER_PAYABLE/PREPAYMENT；period 为 NOT NULL 且无默认值
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_business_doc (doc_no, doc_type, doc_date, period, status, "
                        + "amount, enterprise_id, deleted) "
                        + "VALUES (?, 'RECEIPT', CURRENT_DATE, '209912', 'DRAFT', 0, 1, 0) RETURNING id",
                Long.class, "P104DOC" + suffix);
    }

    private Long subjectId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_subject WHERE deleted = 0 ORDER BY id LIMIT 1", Long.class);
    }

    private Long insertEntry(Long docId, Long subjectId, String amount, int sort) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_business_doc_entry (doc_id, subject_id, amount, sort_order, "
                        + "enterprise_id, deleted) VALUES (?, ?, ?, ?, 1, 0) RETURNING id",
                Long.class, docId, subjectId, new java.math.BigDecimal(amount), sort);
    }

    @Test
    @DisplayName("selectByDocId 按单据返回分录并按 id 排序")
    void selectByDocIdReturnsOrderedEntries() {
        Long docId = createDoc("A");
        Long sid = subjectId();
        insertEntry(docId, sid, "100.00", 1);
        insertEntry(docId, sid, "200.00", 2);

        var entries = businessDocEntryMapper.selectByDocId(docId);
        assertEquals(2, entries.size(), "应返回 2 条分录");
        assertTrue(entries.get(0).getId() < entries.get(1).getId(), "分录未按 id 升序");
    }

    @Test
    @DisplayName("selectByDocId 对无分录单据返回空列表而非 null")
    void selectByDocIdReturnsEmptyList() {
        Long docId = createDoc("B");
        var entries = businessDocEntryMapper.selectByDocId(docId);
        assertTrue(entries != null && entries.isEmpty(), "无分录时应返回空列表");
    }

    @Test
    @DisplayName("deleteByDocId 物理删除该单据的分录（不误伤其他单据）")
    void deleteByDocIdRemovesOnlyThatDoc() {
        Long docA = createDoc("C");
        Long docB = createDoc("D");
        Long sid = subjectId();
        Long a1 = insertEntry(docA, sid, "10.00", 1);
        insertEntry(docA, sid, "20.00", 2);
        Long b1 = insertEntry(docB, sid, "30.00", 1);

        assertEquals(2, businessDocEntryMapper.deleteByDocId(docA));

        Integer aLeft = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_business_doc_entry WHERE id = ?", Integer.class, a1);
        assertEquals(0, aLeft, "目标单据的分录未删除");
        Integer bLeft = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_business_doc_entry WHERE id = ?", Integer.class, b1);
        assertEquals(1, bLeft, "误删了其他单据的分录");
    }

    @Test
    @DisplayName("physicalDeleteAll 清空全表（维护操作语义，非软删）")
    void physicalDeleteAllClearsTable() {
        Long docId = createDoc("E");
        insertEntry(docId, subjectId(), "40.00", 1);

        businessDocEntryMapper.physicalDeleteAll();

        Integer left = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_business_doc_entry", Integer.class);
        assertEquals(0, left, "physicalDeleteAll 后表应为空");
    }
}