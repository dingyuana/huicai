package com.huicai.sme.arap.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编号关联体系 — 实体字段完整性测试（L2 / @SlowTest）
 *
 * <h3>历史说明（REQ-2026-113 / P99 A 类分诊）</h3>
 * 本测试此前在 {@code t_input_invoice} / {@code t_output_invoice} 上直接写入并回读
 * {@code docNo}/{@code voucherNo}，在 {@code t_voucher} 上回读
 * {@code sourceDocId}/{@code sourceDocNo}/{@code sourceDocType}，并使用
 * {@code vendorId=1}/{@code customerId=1}/{@code supplierId=1} 等硬编码悬空外键与
 * 非法单据状态 {@code PENDING_CONFIRM}。
 *
 * <p>核验结论（对照当前 migration 链实跑）：
 * <ul>
 *   <li><b>发票侧的编号冗余列已被移除</b>：{@code t_input_invoice} /
 *       {@code t_output_invoice} 上均无 {@code doc_no}、{@code voucher_no}。
 *       发票与业务单据的关联统一由 {@code t_business_doc.invoice_id / invoice_no} 承载
 *       （id 外键 + 编号冗余仍在业务单据一侧，符合铁律 #9）。注意发票侧
 *       {@code doc_id} / {@code voucher_id} <b>仍然存在</b>，属有效的 id 关联，不是废弃项；</li>
 *   <li><b>凭证侧溯源列已被移除</b>：{@code t_voucher} 上无 {@code source_doc_no} /
 *       {@code source_doc_id} / {@code source_doc_type}，现由
 *       {@code t_voucher.business_doc_id}（带 FK 到 {@code t_business_doc.id}）表达关联；</li>
 *   <li>{@code vendorId=1} / {@code customerId=1} 在干净库中无对应行，触发
 *       {@code fk_input_invoice_vendor} / {@code fk_output_invoice_customer} 外键违约；</li>
 *   <li>{@code t_business_doc} 的 {@code chk_doc_status} 不含 {@code PENDING_CONFIRM}，
 *       也不含 {@code CONFIRMED}（那是发票侧状态），合法值见
 *       {@code chk_doc_status}：DRAFT/SUBMITTED/APPROVED/VOUCHERED/
 *       PARTIALLY_RECONCILED/FULLY_RECONCILED/CLOSED/REJECTED/REVERSED。</li>
 * </ul>
 *
 * <p>因此本测试守护的同样是<b>一套已废弃的数据模型</b>。本次改为断言<b>新模型下真实存在
 * 的编号关联字段</b>，并用真实关联数据（而非硬编码 id=1）验证读写与双向反查，
 * 保持铁律 #9「xxx_id 外键 + xxx_no 编号冗余，支持全链路追溯」这一测试意图不变。
 *
 * <p>负向断言：已废弃的发票侧编号冗余列与凭证侧 source_doc_no 列<b>不得复活</b>。
 *
 * @see docs/development/plans/2026-09-28-slow-test-triage.md §二 A 类
 */
@DisplayName("编号关联 - 实体字段完整性")
public class NumberingAssociationFieldsTest extends AbstractMapperTest {

    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private BusinessDocMapper businessDocMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** t_* 表 enterprise_id 自 V102/V103 起 NOT NULL 且无 DB 默认值，必须显式赋值 */
    private static final Long ENTERPRISE_ID = 1L;

    /** t_business_doc.chk_doc_status 合法值（发票状态 CONFIRMED 在此非法） */
    private static final String DOC_STATUS_APPROVED = "APPROVED";

    /**
     * 编号唯一后缀，避免与其它测试类种子数据撞 uq_doc_no_type / uq_*_invoice_no。
     * 长度必须受控：{@code t_business_doc.doc_no} / {@code t_business_doc.voucher_no} /
     * {@code t_voucher.voucher_no} 均为 {@code varchar(32)}，
     * 超长会触发 "value too long for type character varying(32)"。
     */
    private static final AtomicLong SEQ = new AtomicLong();
    private static final String CLS = Long.toString(System.nanoTime() & 0xFFFFF, 36);

    private String uniqueNo() {
        return CLS + Long.toString(SEQ.incrementAndGet(), 36);
    }

    private boolean columnExists(String table, String column) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        return n != null && n > 0;
    }

    // ==================== 负向：已废弃字段不得复活 ====================

    @Test
    @DisplayName("负向: 发票表上的 doc_no / voucher_no 编号冗余已废弃，不得存在")
    void deprecated_invoiceDocNumbers_absent() {
        for (String table : new String[]{"t_input_invoice", "t_output_invoice"}) {
            for (String column : new String[]{"doc_no", "voucher_no"}) {
                assertFalse(columnExists(table, column),
                        table + "." + column + " 属已废弃设计：编号冗余应只保留在 t_business_doc 一侧");
            }
        }
    }

    @Test
    @DisplayName("负向: 凭证表上的 source_doc_no / source_doc_id / source_doc_type 已废弃，不得存在")
    void deprecated_voucherSourceDocNo_absent() {
        for (String column : new String[]{"source_doc_no", "source_doc_id", "source_doc_type"}) {
            assertFalse(columnExists("t_voucher", column),
                    "t_voucher." + column + " 属已废弃的双向编号结构，现由 t_voucher.business_doc_id 表达关联");
        }
    }

    @Test
    @DisplayName("正向: 发票侧 doc_id / voucher_id id 关联仍有效，不得被误删")
    void invoiceDocAndVoucherId_stillPresent() {
        for (String table : new String[]{"t_input_invoice", "t_output_invoice"}) {
            assertTrue(columnExists(table, "doc_id"), table + ".doc_id 是有效的 id 关联，应保留");
            assertTrue(columnExists(table, "voucher_id"), table + ".voucher_id 是有效的 id 关联，应保留");
        }
    }

    // ==================== 凭证 ↔ 业务单据（新模型）====================

    @Test
    @DisplayName("凭证: businessDocId 溯源字段可读写（真实 FK 目标）")
    void voucher_businessDocId() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("9999.TRACE.DOC." + uniqueNo());
        doc.setDocType("INVOICE_OUT");
        doc.setPeriod("202606");
        doc.setDocDate(LocalDate.of(2026, 6, 28));
        doc.setAmount(new BigDecimal("11300.00"));
        doc.setStatus(DOC_STATUS_APPROVED);
        doc.setEnterpriseId(ENTERPRISE_ID);
        doc.setDeleted(0);
        businessDocMapper.insert(doc);

        VoucherEntity entity = new VoucherEntity();
        entity.setVoucherNo("9999.VCH.TRACE." + uniqueNo());
        entity.setPeriod("202606");
        entity.setVoucherTypeId(1L);
        entity.setStatus("DRAFT");
        entity.setSource("GENERATED");
        entity.setSummary("测试溯源凭证");
        entity.setTotalDebit(new BigDecimal("11300.00"));
        entity.setTotalCredit(new BigDecimal("11300.00"));
        entity.setBusinessDocId(doc.getId());
        entity.setEnterpriseId(ENTERPRISE_ID);
        entity.setDeleted(0);

        voucherMapper.insert(entity);

        VoucherEntity found = voucherMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals(doc.getId(), found.getBusinessDocId());
    }

    @Test
    @DisplayName("编号冗余: 业务单据上的 voucherId / voucherNo 可读写")
    void businessDoc_voucherNo() {
        String voucherNo = "9999.VCH.BDOC." + uniqueNo();
        BusinessDocEntity entity = new BusinessDocEntity();
        entity.setDocNo("9999.BDOC.DOC." + uniqueNo());
        entity.setDocType("INVOICE_OUT");
        entity.setPeriod("202606");
        entity.setDocDate(LocalDate.of(2026, 6, 28));
        entity.setAmount(new BigDecimal("10000.00"));
        entity.setStatus(DOC_STATUS_APPROVED);
        entity.setVoucherId(1L);
        entity.setVoucherNo(voucherNo);
        entity.setDeleted(0);
        entity.setEnterpriseId(ENTERPRISE_ID);

        businessDocMapper.insert(entity);

        BusinessDocEntity found = businessDocMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals(1L, found.getVoucherId());
        assertEquals(voucherNo, found.getVoucherNo());
    }

    // ==================== 发票 ↔ 业务单据（新模型）====================

    @Test
    @DisplayName("业务单据(应收): invoiceNo 编号冗余 + customerId 关联可读写")
    void businessDoc_receivable_fields() {
        String invoiceNo = "9999.OI.INV." + uniqueNo();
        BusinessDocEntity entity = new BusinessDocEntity();
        entity.setDocNo("9999.REC.DOC." + uniqueNo());
        entity.setDocType("INVOICE_OUT");
        entity.setPeriod("202606");
        entity.setDocDate(LocalDate.of(2026, 6, 28));
        entity.setAmount(new BigDecimal("11300.00"));
        entity.setInvoiceNo(invoiceNo);
        entity.setStatus(DOC_STATUS_APPROVED);
        entity.setCustomerId(1L);
        entity.setUnsettledAmount(new BigDecimal("11300.00"));
        entity.setDeleted(0);
        entity.setEnterpriseId(ENTERPRISE_ID);

        businessDocMapper.insert(entity);

        BusinessDocEntity found = businessDocMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals(entity.getDocNo(), found.getDocNo());
        assertEquals(invoiceNo, found.getInvoiceNo());
        assertEquals(1L, found.getCustomerId());
        assertEquals(new BigDecimal("11300.00"), found.getUnsettledAmount());
    }

    @Test
    @DisplayName("业务单据(应付): invoiceNo 编号冗余 + supplierId 关联可读写")
    void businessDoc_payable_fields() {
        String invoiceNo = "9999.II.INV." + uniqueNo();
        BusinessDocEntity entity = new BusinessDocEntity();
        entity.setDocNo("9999.PAY.DOC." + uniqueNo());
        entity.setDocType("INVOICE_IN");
        entity.setPeriod("202606");
        entity.setDocDate(LocalDate.of(2026, 6, 28));
        entity.setAmount(new BigDecimal("11300.00"));
        entity.setInvoiceNo(invoiceNo);
        entity.setStatus(DOC_STATUS_APPROVED);
        entity.setSupplierId(1L);
        entity.setUnsettledAmount(new BigDecimal("11300.00"));
        entity.setDeleted(0);
        entity.setEnterpriseId(ENTERPRISE_ID);

        businessDocMapper.insert(entity);

        BusinessDocEntity found = businessDocMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals(entity.getDocNo(), found.getDocNo());
        assertEquals(invoiceNo, found.getInvoiceNo());
        assertEquals(1L, found.getSupplierId());
    }

    @Test
    @DisplayName("编号关联核心: 发票号在业务单据上可被正确查询（全链路溯源）")
    void association_invoiceNo_query_businessDoc() {
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        String invoiceNo = "9999.ASSOC.INV." + uniqueNo();
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
        invoice.setPeriod("202606");
        invoice.setCustomerName("测试客户");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setInvoiceType("SPECIAL");
        invoice.setStatus("CONFIRMED");
        invoice.setDeleted(0);
        invoice.setEnterpriseId(ENTERPRISE_ID);
        outputInvoiceMapper.insert(invoice);

        String docNo = "9999.ASSOC.REC." + uniqueNo();
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(docNo);
        doc.setDocType("INVOICE_OUT");
        doc.setPeriod("202606");
        doc.setDocDate(LocalDate.of(2026, 6, 28));
        doc.setAmount(new BigDecimal("11300.00"));
        doc.setInvoiceNo(invoiceNo);
        doc.setStatus(DOC_STATUS_APPROVED);
        doc.setUnsettledAmount(new BigDecimal("11300.00"));
        doc.setDeleted(0);
        doc.setEnterpriseId(ENTERPRISE_ID);
        businessDocMapper.insert(doc);

        BusinessDocEntity found = businessDocMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                        .eq(BusinessDocEntity::getInvoiceNo, invoiceNo)
        );

        assertNotNull(found, "应能通过发票号在业务单据上反查");
        assertEquals(docNo, found.getDocNo());
    }

    @Test
    @DisplayName("编号关联核心: 凭证通过 businessDocId 可追溯到业务单据")
    void association_voucher_to_businessDoc_via_businessDocId() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("9999.ASSOC.DOC." + uniqueNo());
        doc.setDocType("INVOICE_OUT");
        doc.setPeriod("202606");
        doc.setDocDate(LocalDate.of(2026, 6, 28));
        doc.setAmount(new BigDecimal("11300.00"));
        doc.setStatus(DOC_STATUS_APPROVED);
        doc.setDeleted(0);
        doc.setEnterpriseId(ENTERPRISE_ID);
        businessDocMapper.insert(doc);

        String voucherNo = "9999.ASSOC.VCH." + uniqueNo();
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod("202606");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("关联测试凭证");
        voucher.setTotalDebit(new BigDecimal("11300.00"));
        voucher.setTotalCredit(new BigDecimal("11300.00"));
        voucher.setBusinessDocId(doc.getId());
        voucher.setDeleted(0);
        voucher.setEnterpriseId(ENTERPRISE_ID);
        voucherMapper.insert(voucher);

        VoucherEntity found = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                        .eq(VoucherEntity::getBusinessDocId, doc.getId())
        );

        assertNotNull(found, "应能通过 businessDocId 在凭证上反查");
        assertEquals(voucherNo, found.getVoucherNo());
    }

    // ==================== 进项发票基础字段（新模型）====================

    @Test
    @DisplayName("进项发票: 基础业务字段可读写（vendor 走名称冗余，避开悬空外键）")
    void inputInvoice_baseFields() {
        String invoiceNo = "9999.II.INV." + uniqueNo();
        InputInvoiceEntity entity = new InputInvoiceEntity();
        entity.setInvoiceNo(invoiceNo);
        entity.setInvoiceDate(LocalDate.of(2026, 6, 28));
        entity.setPeriod("202606");
        entity.setVendorName("测试供应商");
        entity.setAmount(new BigDecimal("10000.00"));
        entity.setTaxRate(new BigDecimal("0.13"));
        entity.setTaxAmount(new BigDecimal("1300.00"));
        entity.setTotalAmount(new BigDecimal("11300.00"));
        entity.setInvoiceType("SPECIAL");
        entity.setCertificationStatus("UNCERTIFIED");
        entity.setStatus("CONFIRMED");
        entity.setDeleted(0);
        entity.setEnterpriseId(ENTERPRISE_ID);

        inputInvoiceMapper.insert(entity);

        InputInvoiceEntity found = inputInvoiceMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals(invoiceNo, found.getInvoiceNo());
        assertEquals("测试供应商", found.getVendorName());
        assertEquals(new BigDecimal("11300.00"), found.getTotalAmount());
    }

    // ==================== 汇总检查 ====================

    @Test
    @DisplayName("汇总: 新模型下编号关联所需列均应存在")
    void associationColumns_summary() {
        String[][] required = {
                {"t_voucher", "voucher_no"},
                {"t_voucher", "business_doc_id"},
                {"t_business_doc", "doc_no"},
                {"t_business_doc", "doc_type"},
                {"t_business_doc", "voucher_id"},
                {"t_business_doc", "voucher_no"},
                {"t_business_doc", "invoice_id"},
                {"t_business_doc", "invoice_no"},
                {"t_input_invoice", "invoice_no"},
                {"t_input_invoice", "doc_id"},
                {"t_output_invoice", "invoice_no"},
                {"t_output_invoice", "doc_id"},
        };
        StringBuilder missing = new StringBuilder();
        for (String[] pair : required) {
            if (!columnExists(pair[0], pair[1])) {
                missing.append("  缺失字段: ").append(pair[0]).append('.').append(pair[1]).append('\n');
            }
        }
        assertEquals(0, missing.length(), "编号关联结构不完整：\n" + missing);
    }
}
