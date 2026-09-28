package com.huicai.base.voucher.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编号关联体系 — 端到端链路测试（L2 / @SlowTest）
 *
 * <h3>历史说明（REQ-2026-113 / P99 A 类分诊）</h3>
 * 本测试此前用 {@code t_voucher.source_doc_no} / {@code source_doc_type} 表达
 * 「凭证 → 发票」的溯源关系，并使用 {@code t_business_doc.status = "PENDING_CONFIRM"}。
 * 核验结论：
 * <ul>
 *   <li>{@code VoucherEntity.sourceDocId / sourceDocNo / sourceDocType} 三个字段自身即标注
 *       {@code @TableField(exist = false)}，注释明写「DB 无此列」—— 凭证侧的编号溯源
 *       结构<b>在当前 schema 中根本不存在</b>，用它们做链路断言是在守护已废弃的设计；</li>
 *   <li>凭证 → 业务单据的真实关联列是 {@code t_voucher.business_doc_id}
 *       （带 FK {@code fk_voucher_business_doc}），且有
 *       {@code idx_voucher_business_doc_id} 支撑；</li>
 *   <li>{@code "PENDING_CONFIRM"} 不在 {@code t_business_doc.chk_doc_status} 合法值内
 *       （该值属发票侧状态），且 {@code t_business_doc.doc_date} 为 NOT NULL 无默认值、
 *       {@code enterprise_id} 自 V102/V103 起 NOT NULL 无默认值。</li>
 * </ul>
 *
 * <p>本次保持「发票 → 业务单据 → 凭证」全链路溯源（铁律 #9）的测试意图不变，
 * 改用新模型的真实关联列：发票侧以 {@code invoice_no} 与单据对齐，单据侧冗余
 * {@code voucher_id}/{@code voucher_no}，凭证侧以 {@code business_doc_id} 回指单据。
 */
@DisplayName("编号关联 - 端到端链路测试")
public class NumberingAssociationE2ETest extends AbstractMapperTest {

    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private BusinessDocMapper businessDocMapper;

    /** t_* 表 enterprise_id 自 V102/V103 起 NOT NULL 且无 DB 默认值，必须显式赋值 */
    private static final Long ENTERPRISE_ID = 1L;

    /** t_business_doc.chk_doc_status 合法值（发票状态 PENDING_CONFIRM 在此非法） */
    private static final String DOC_STATUS_APPROVED = "APPROVED";

    private static final LocalDate DOC_DATE = LocalDate.of(2026, 6, 28);
    private static final String PERIOD = "202606";
    private static final BigDecimal AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal TAX = new BigDecimal("1300.00");
    private static final BigDecimal TOTAL = new BigDecimal("11300.00");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.13");

    /**
     * 本类编号唯一后缀，避免与其它测试类种子数据撞唯一约束。
     * 长度必须受控：t_business_doc.doc_no / t_business_doc.voucher_no / t_voucher.voucher_no
     * 均为 {@code varchar(32)}，超长会触发 "value too long for type character varying(32)"。
     */
    private static final String UQ = Long.toString(System.nanoTime() & 0xFFFFF, 36);

    private OutputInvoiceEntity newOutputInvoice(String seq) {
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("9999.E2E.SALE.INV." + seq + "." + UQ);
        invoice.setInvoiceDate(DOC_DATE);
        invoice.setPeriod(PERIOD);
        invoice.setAmount(AMOUNT);
        invoice.setTaxAmount(TAX);
        invoice.setTotalAmount(TOTAL);
        invoice.setInvoiceType("SPECIAL");
        invoice.setTaxRate(TAX_RATE);
        invoice.setStatus("CONFIRMED");
        invoice.setDeleted(0);
        invoice.setEnterpriseId(ENTERPRISE_ID);
        return invoice;
    }

    private InputInvoiceEntity newInputInvoice(String seq) {
        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo("9999.E2E.PURC.INV." + seq + "." + UQ);
        invoice.setInvoiceDate(DOC_DATE);
        invoice.setPeriod(PERIOD);
        invoice.setAmount(AMOUNT);
        invoice.setTaxAmount(TAX);
        invoice.setTotalAmount(TOTAL);
        invoice.setInvoiceType("SPECIAL");
        invoice.setTaxRate(TAX_RATE);
        invoice.setCertificationStatus("UNCERTIFIED");
        invoice.setStatus("CONFIRMED");
        invoice.setDeleted(0);
        invoice.setEnterpriseId(ENTERPRISE_ID);
        return invoice;
    }

    private BusinessDocEntity newDoc(String docType, String docNo, String invoiceNo) {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(docNo);
        doc.setDocType(docType);
        doc.setPeriod(PERIOD);
        doc.setDocDate(DOC_DATE);
        doc.setAmount(TOTAL);
        doc.setInvoiceNo(invoiceNo);
        doc.setStatus(DOC_STATUS_APPROVED);
        doc.setUnsettledAmount(TOTAL);
        doc.setDeleted(0);
        doc.setEnterpriseId(ENTERPRISE_ID);
        return doc;
    }

    private VoucherEntity newVoucher(String voucherNo, String summary, Long businessDocId) {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(PERIOD);
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(summary);
        voucher.setTotalDebit(TOTAL);
        voucher.setTotalCredit(TOTAL);
        voucher.setBusinessDocId(businessDocId);
        voucher.setDeleted(0);
        voucher.setEnterpriseId(ENTERPRISE_ID);
        return voucher;
    }

    @Nested
    @DisplayName("销售链路: 销项发票 → 业务单据 → 凭证")
    class SalesChainTest {

        @Test
        @DisplayName("业务单据通过 invoiceNo 可查到对应销项发票")
        void sales_invoice_to_businessDoc_by_invoiceNo() {
            OutputInvoiceEntity invoice = newOutputInvoice("001");
            outputInvoiceMapper.insert(invoice);

            String docNo = "9999.E2E.SALE.DOC.001." + UQ;
            BusinessDocEntity doc = newDoc("INVOICE_OUT", docNo, invoice.getInvoiceNo());
            doc.setCustomerId(1L);
            businessDocMapper.insert(doc);

            BusinessDocEntity found = businessDocMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                    .eq(BusinessDocEntity::getInvoiceNo, invoice.getInvoiceNo())
            );

            assertNotNull(found, "通过 invoiceNo 应能查到业务单据");
            assertEquals(docNo, found.getDocNo());
        }

        @Test
        @DisplayName("凭证通过 businessDocId 可追溯到业务单据，业务单据 voucherNo 被回写")
        void sales_businessDoc_to_voucher() {
            OutputInvoiceEntity invoice = newOutputInvoice("002");
            outputInvoiceMapper.insert(invoice);

            String docNo = "9999.E2E.SALE.DOC.002." + UQ;
            BusinessDocEntity doc = newDoc("INVOICE_OUT", docNo, invoice.getInvoiceNo());
            doc.setCustomerId(1L);
            businessDocMapper.insert(doc);

            String voucherNo = "9999.E2E.SALE.VCH.002." + UQ;
            VoucherEntity voucher = newVoucher(voucherNo, "销售发票转凭证", doc.getId());
            voucherMapper.insert(voucher);

            doc.setVoucherId(voucher.getId());
            doc.setVoucherNo(voucherNo);
            businessDocMapper.updateById(doc);

            VoucherEntity foundVoucher = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getBusinessDocId, doc.getId())
            );
            assertNotNull(foundVoucher, "通过 businessDocId 应能查到凭证");
            assertEquals(voucherNo, foundVoucher.getVoucherNo());

            BusinessDocEntity foundDoc = businessDocMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                    .eq(BusinessDocEntity::getInvoiceNo, invoice.getInvoiceNo())
            );
            assertNotNull(foundDoc, "通过 invoiceNo 应能查到业务单据");
            assertEquals(voucherNo, foundDoc.getVoucherNo());

            // 负向：凭证侧不存在 source_doc_no 列，实体字段从 DB 读回必为 null
            assertNull(foundVoucher.getSourceDocNo(), "t_voucher 无 source_doc_no 列，不应有值");
        }

        @Test
        @DisplayName("完整销售链路: 发票 → 业务单据 → 凭证（编号传递验证）")
        void full_sales_chain() {
            OutputInvoiceEntity invoice = newOutputInvoice("003");
            String invoiceNo = invoice.getInvoiceNo();
            outputInvoiceMapper.insert(invoice);

            String docNo = "9999.E2E.SALE.DOC.003." + UQ;
            BusinessDocEntity doc = newDoc("INVOICE_OUT", docNo, invoiceNo);
            doc.setCustomerId(1L);
            businessDocMapper.insert(doc);

            String voucherNo = "9999.E2E.SALE.VCH.003." + UQ;
            VoucherEntity voucher = newVoucher(voucherNo, "销售发票转凭证", doc.getId());
            voucherMapper.insert(voucher);

            doc.setVoucherId(voucher.getId());
            doc.setVoucherNo(voucherNo);
            businessDocMapper.updateById(doc);

            BusinessDocEntity foundDoc = businessDocMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                    .eq(BusinessDocEntity::getInvoiceNo, invoiceNo)
            );
            assertNotNull(foundDoc, "业务单据应通过 invoiceNo 被查到");
            assertEquals(voucherNo, foundDoc.getVoucherNo());
            assertEquals(voucher.getId(), foundDoc.getVoucherId());

            VoucherEntity foundVoucher = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getBusinessDocId, doc.getId())
            );
            assertNotNull(foundVoucher, "凭证应通过 businessDocId 被查到");
            assertEquals(voucherNo, foundVoucher.getVoucherNo());
        }
    }

    @Nested
    @DisplayName("采购链路: 进项发票 → 业务单据 → 凭证")
    class ProcurementChainTest {

        @Test
        @DisplayName("业务单据通过 invoiceNo 可查到对应进项发票")
        void procurement_invoice_to_businessDoc_by_invoiceNo() {
            InputInvoiceEntity invoice = newInputInvoice("001");
            inputInvoiceMapper.insert(invoice);

            String docNo = "9999.E2E.PURC.DOC.001." + UQ;
            BusinessDocEntity doc = newDoc("INVOICE_IN", docNo, invoice.getInvoiceNo());
            doc.setSupplierId(1L);
            businessDocMapper.insert(doc);

            BusinessDocEntity found = businessDocMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                    .eq(BusinessDocEntity::getInvoiceNo, invoice.getInvoiceNo())
            );

            assertNotNull(found, "通过 invoiceNo 应能查到业务单据");
            assertEquals(docNo, found.getDocNo());
        }

        @Test
        @DisplayName("凭证通过 businessDocId 可追溯到业务单据，业务单据 voucherNo 被回写")
        void procurement_businessDoc_to_voucher() {
            InputInvoiceEntity invoice = newInputInvoice("002");
            String invoiceNo = invoice.getInvoiceNo();
            inputInvoiceMapper.insert(invoice);

            String docNo = "9999.E2E.PURC.DOC.002." + UQ;
            BusinessDocEntity doc = newDoc("INVOICE_IN", docNo, invoiceNo);
            doc.setSupplierId(1L);
            businessDocMapper.insert(doc);

            String voucherNo = "9999.E2E.PURC.VCH.002." + UQ;
            VoucherEntity voucher = newVoucher(voucherNo, "采购发票转凭证", doc.getId());
            voucherMapper.insert(voucher);

            doc.setVoucherId(voucher.getId());
            doc.setVoucherNo(voucherNo);
            businessDocMapper.updateById(doc);

            BusinessDocEntity foundDoc = businessDocMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                    .eq(BusinessDocEntity::getInvoiceNo, invoiceNo)
            );
            assertNotNull(foundDoc);
            assertEquals(voucherNo, foundDoc.getVoucherNo());

            VoucherEntity foundVoucher = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getBusinessDocId, doc.getId())
            );
            assertNotNull(foundVoucher);
            assertEquals(voucherNo, foundVoucher.getVoucherNo());

            // 负向：凭证侧不存在 source_doc_type 列，实体字段从 DB 读回必为 null
            assertNull(foundVoucher.getSourceDocType(), "t_voucher 无 source_doc_type 列，不应有值");
        }

        @Test
        @DisplayName("完整采购链路: 发票 → 业务单据 → 凭证（编号传递验证）")
        void full_procurement_chain() {
            InputInvoiceEntity invoice = newInputInvoice("003");
            String invoiceNo = invoice.getInvoiceNo();
            inputInvoiceMapper.insert(invoice);

            String docNo = "9999.E2E.PURC.DOC.003." + UQ;
            BusinessDocEntity doc = newDoc("INVOICE_IN", docNo, invoiceNo);
            doc.setSupplierId(1L);
            businessDocMapper.insert(doc);

            String voucherNo = "9999.E2E.PURC.VCH.003." + UQ;
            VoucherEntity voucher = newVoucher(voucherNo, "采购发票转凭证", doc.getId());
            voucherMapper.insert(voucher);

            doc.setVoucherId(voucher.getId());
            doc.setVoucherNo(voucherNo);
            businessDocMapper.updateById(doc);

            BusinessDocEntity foundDoc = businessDocMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                    .eq(BusinessDocEntity::getInvoiceNo, invoiceNo)
            );
            assertNotNull(foundDoc, "业务单据应通过 invoiceNo 被查到");
            assertEquals(voucherNo, foundDoc.getVoucherNo());
            assertEquals(voucher.getId(), foundDoc.getVoucherId());

            VoucherEntity foundVoucher = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getBusinessDocId, doc.getId())
            );
            assertNotNull(foundVoucher, "凭证应通过 businessDocId 被查到");
            assertEquals(voucherNo, foundVoucher.getVoucherNo());
        }
    }
}
