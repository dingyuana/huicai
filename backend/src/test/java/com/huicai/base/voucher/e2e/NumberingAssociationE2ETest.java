package com.huicai.base.voucher.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.arap.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.sme.arap.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.sme.tax.entity.InputInvoiceEntity;
import com.huicai.sme.tax.entity.OutputInvoiceEntity;
import com.huicai.sme.tax.mapper.InputInvoiceMapper;
import com.huicai.sme.tax.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("编号关联 - 端到端链路测试")
public class NumberingAssociationE2ETest extends AbstractMapperTest {

    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private BusinessDocMapper businessDocMapper;

    @Nested
    @DisplayName("销售链路: 销项发票 → 业务单据 → 凭证")
    class SalesChainTest {

        @Test
        @DisplayName("业务单据通过 invoiceNo 可查到对应销项发票")
        void sales_invoice_to_businessDoc_by_invoiceNo() {
            OutputInvoiceEntity invoice = new OutputInvoiceEntity();
            invoice.setInvoiceNo("9999.E2E.SALE.INV.001");
            invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
            invoice.setPeriod("202606");
            invoice.setAmount(new BigDecimal("10000.00"));
            invoice.setTaxAmount(new BigDecimal("1300.00"));
            invoice.setTotalAmount(new BigDecimal("11300.00"));
            invoice.setInvoiceType("SPECIAL");
            invoice.setTaxRate(new BigDecimal("0.13"));
            invoice.setStatus("CONFIRMED");
            invoice.setDeleted(0);
            outputInvoiceMapper.insert(invoice);

            BusinessDocEntity doc = new BusinessDocEntity();
            doc.setDocNo("9999.E2E.SALE.DOC.001");
            doc.setDocType("INVOICE_OUT");
            doc.setPeriod("202606");
            doc.setAmount(new BigDecimal("11300.00"));
            doc.setInvoiceNo("9999.E2E.SALE.INV.001");
            doc.setStatus("PENDING_CONFIRM");
            doc.setCustomerId(1L);
            doc.setUnsettledAmount(new BigDecimal("11300.00"));
            businessDocMapper.insert(doc);

            BusinessDocEntity found = businessDocMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                    .eq(BusinessDocEntity::getInvoiceNo, "9999.E2E.SALE.INV.001")
            );

            assertNotNull(found, "通过 invoiceNo 应能查到业务单据");
            assertEquals("9999.E2E.SALE.DOC.001", found.getDocNo());
        }

        @Test
        @DisplayName("凭证通过 sourceDocNo 可追溯到销项发票，业务单据 voucherNo 被回写")
        void sales_businessDoc_to_voucher() {
            OutputInvoiceEntity invoice = new OutputInvoiceEntity();
            invoice.setInvoiceNo("9999.E2E.SALE.INV.002");
            invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
            invoice.setPeriod("202606");
            invoice.setAmount(new BigDecimal("10000.00"));
            invoice.setTaxAmount(new BigDecimal("1300.00"));
            invoice.setTotalAmount(new BigDecimal("11300.00"));
            invoice.setInvoiceType("SPECIAL");
            invoice.setTaxRate(new BigDecimal("0.13"));
            invoice.setStatus("CONFIRMED");
            invoice.setDeleted(0);
            outputInvoiceMapper.insert(invoice);

            BusinessDocEntity doc = new BusinessDocEntity();
            doc.setDocNo("9999.E2E.SALE.DOC.002");
            doc.setDocType("INVOICE_OUT");
            doc.setPeriod("202606");
            doc.setAmount(new BigDecimal("11300.00"));
            doc.setInvoiceNo("9999.E2E.SALE.INV.002");
            doc.setStatus("PENDING_CONFIRM");
            doc.setCustomerId(1L);
            doc.setUnsettledAmount(new BigDecimal("11300.00"));
            businessDocMapper.insert(doc);

            VoucherEntity voucher = new VoucherEntity();
            voucher.setVoucherNo("9999.E2E.SALE.VCH.002");
            voucher.setPeriod("202606");
            voucher.setVoucherTypeId(1L);
            voucher.setStatus("DRAFT");
            voucher.setSource("GENERATED");
            voucher.setSummary("销售发票转凭证");
            voucher.setTotalDebit(new BigDecimal("11300.00"));
            voucher.setTotalCredit(new BigDecimal("11300.00"));
            voucher.setSourceDocType("OUTPUT_INVOICE");
            voucher.setSourceDocNo("9999.E2E.SALE.INV.002");
            voucher.setDeleted(0);
            voucherMapper.insert(voucher);

            doc.setVoucherId(voucher.getId());
            doc.setVoucherNo("9999.E2E.SALE.VCH.002");
            businessDocMapper.updateById(doc);

            VoucherEntity foundVoucher = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getSourceDocNo, "9999.E2E.SALE.INV.002")
            );
            assertNotNull(foundVoucher, "通过 sourceDocNo 应能查到凭证");
            assertEquals("9999.E2E.SALE.VCH.002", foundVoucher.getVoucherNo());
            assertEquals("OUTPUT_INVOICE", foundVoucher.getSourceDocType());

            BusinessDocEntity foundDoc = businessDocMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                    .eq(BusinessDocEntity::getInvoiceNo, "9999.E2E.SALE.INV.002")
            );
            assertNotNull(foundDoc, "通过 invoiceNo 应能查到业务单据");
            assertEquals("9999.E2E.SALE.VCH.002", foundDoc.getVoucherNo());
        }

        @Test
        @DisplayName("完整销售链路: 发票 → 业务单据 → 凭证（编号传递验证）")
        void full_sales_chain() {
            String invoiceNo = "9999.E2E.SALE.INV.003";
            String docNo = "9999.E2E.SALE.DOC.003";
            String voucherNo = "9999.E2E.SALE.VCH.003";

            OutputInvoiceEntity invoice = new OutputInvoiceEntity();
            invoice.setInvoiceNo(invoiceNo);
            invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
            invoice.setPeriod("202606");
            invoice.setAmount(new BigDecimal("10000.00"));
            invoice.setTaxAmount(new BigDecimal("1300.00"));
            invoice.setTotalAmount(new BigDecimal("11300.00"));
            invoice.setInvoiceType("SPECIAL");
            invoice.setTaxRate(new BigDecimal("0.13"));
            invoice.setStatus("CONFIRMED");
            invoice.setDeleted(0);
            outputInvoiceMapper.insert(invoice);

            BusinessDocEntity doc = new BusinessDocEntity();
            doc.setDocNo(docNo);
            doc.setDocType("INVOICE_OUT");
            doc.setPeriod("202606");
            doc.setAmount(new BigDecimal("11300.00"));
            doc.setInvoiceNo(invoiceNo);
            doc.setStatus("PENDING_CONFIRM");
            doc.setCustomerId(1L);
            doc.setUnsettledAmount(new BigDecimal("11300.00"));
            businessDocMapper.insert(doc);

            VoucherEntity voucher = new VoucherEntity();
            voucher.setVoucherNo(voucherNo);
            voucher.setPeriod("202606");
            voucher.setVoucherTypeId(1L);
            voucher.setStatus("DRAFT");
            voucher.setSource("GENERATED");
            voucher.setSummary("销售发票转凭证");
            voucher.setTotalDebit(new BigDecimal("11300.00"));
            voucher.setTotalCredit(new BigDecimal("11300.00"));
            voucher.setSourceDocType("OUTPUT_INVOICE");
            voucher.setSourceDocNo(invoiceNo);
            voucher.setDeleted(0);
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

            VoucherEntity foundVoucher = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getSourceDocNo, invoiceNo)
            );
            assertNotNull(foundVoucher, "凭证应通过 sourceDocNo 被查到");
            assertEquals(invoiceNo, foundVoucher.getSourceDocNo());
        }
    }

    @Nested
    @DisplayName("采购链路: 进项发票 → 业务单据 → 凭证")
    class ProcurementChainTest {

        @Test
        @DisplayName("业务单据通过 invoiceNo 可查到对应进项发票")
        void procurement_invoice_to_businessDoc_by_invoiceNo() {
            InputInvoiceEntity invoice = new InputInvoiceEntity();
            invoice.setInvoiceNo("9999.E2E.PURC.INV.001");
            invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
            invoice.setPeriod("202606");
            invoice.setAmount(new BigDecimal("10000.00"));
            invoice.setTaxAmount(new BigDecimal("1300.00"));
            invoice.setTotalAmount(new BigDecimal("11300.00"));
            invoice.setInvoiceType("SPECIAL");
            invoice.setTaxRate(new BigDecimal("0.13"));
            invoice.setCertificationStatus("UNCERTIFIED");
            invoice.setDeleted(0);
            inputInvoiceMapper.insert(invoice);

            BusinessDocEntity doc = new BusinessDocEntity();
            doc.setDocNo("9999.E2E.PURC.DOC.001");
            doc.setDocType("INVOICE_IN");
            doc.setPeriod("202606");
            doc.setAmount(new BigDecimal("11300.00"));
            doc.setInvoiceNo("9999.E2E.PURC.INV.001");
            doc.setStatus("PENDING_CONFIRM");
            doc.setSupplierId(1L);
            doc.setUnsettledAmount(new BigDecimal("11300.00"));
            businessDocMapper.insert(doc);

            BusinessDocEntity found = businessDocMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                    .eq(BusinessDocEntity::getInvoiceNo, "9999.E2E.PURC.INV.001")
            );

            assertNotNull(found, "通过 invoiceNo 应能查到业务单据");
            assertEquals("9999.E2E.PURC.DOC.001", found.getDocNo());
        }

        @Test
        @DisplayName("凭证通过 sourceDocNo 可追溯到进项发票，业务单据 voucherNo 被回写")
        void procurement_businessDoc_to_voucher() {
            String invoiceNo = "9999.E2E.PURC.INV.002";

            InputInvoiceEntity invoice = new InputInvoiceEntity();
            invoice.setInvoiceNo(invoiceNo);
            invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
            invoice.setPeriod("202606");
            invoice.setAmount(new BigDecimal("10000.00"));
            invoice.setTaxAmount(new BigDecimal("1300.00"));
            invoice.setTotalAmount(new BigDecimal("11300.00"));
            invoice.setInvoiceType("SPECIAL");
            invoice.setTaxRate(new BigDecimal("0.13"));
            invoice.setCertificationStatus("UNCERTIFIED");
            invoice.setDeleted(0);
            inputInvoiceMapper.insert(invoice);

            BusinessDocEntity doc = new BusinessDocEntity();
            doc.setDocNo("9999.E2E.PURC.DOC.002");
            doc.setDocType("INVOICE_IN");
            doc.setPeriod("202606");
            doc.setAmount(new BigDecimal("11300.00"));
            doc.setInvoiceNo(invoiceNo);
            doc.setStatus("PENDING_CONFIRM");
            doc.setSupplierId(1L);
            doc.setUnsettledAmount(new BigDecimal("11300.00"));
            businessDocMapper.insert(doc);

            VoucherEntity voucher = new VoucherEntity();
            voucher.setVoucherNo("9999.E2E.PURC.VCH.002");
            voucher.setPeriod("202606");
            voucher.setVoucherTypeId(1L);
            voucher.setStatus("DRAFT");
            voucher.setSource("GENERATED");
            voucher.setSummary("采购发票转凭证");
            voucher.setTotalDebit(new BigDecimal("11300.00"));
            voucher.setTotalCredit(new BigDecimal("11300.00"));
            voucher.setSourceDocType("INPUT_INVOICE");
            voucher.setSourceDocNo(invoiceNo);
            voucher.setDeleted(0);
            voucherMapper.insert(voucher);

            doc.setVoucherId(voucher.getId());
            doc.setVoucherNo("9999.E2E.PURC.VCH.002");
            businessDocMapper.updateById(doc);

            BusinessDocEntity foundDoc = businessDocMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                    .eq(BusinessDocEntity::getInvoiceNo, invoiceNo)
            );
            assertNotNull(foundDoc);
            assertEquals("9999.E2E.PURC.VCH.002", foundDoc.getVoucherNo());

            VoucherEntity foundVoucher = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getSourceDocNo, invoiceNo)
            );
            assertNotNull(foundVoucher);
            assertEquals("INPUT_INVOICE", foundVoucher.getSourceDocType());
        }

        @Test
        @DisplayName("完整采购链路: 发票 → 业务单据 → 凭证（编号传递验证）")
        void full_procurement_chain() {
            String invoiceNo = "9999.E2E.PURC.INV.003";
            String docNo = "9999.E2E.PURC.DOC.003";
            String voucherNo = "9999.E2E.PURC.VCH.003";

            InputInvoiceEntity invoice = new InputInvoiceEntity();
            invoice.setInvoiceNo(invoiceNo);
            invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
            invoice.setPeriod("202606");
            invoice.setAmount(new BigDecimal("10000.00"));
            invoice.setTaxAmount(new BigDecimal("1300.00"));
            invoice.setTotalAmount(new BigDecimal("11300.00"));
            invoice.setInvoiceType("SPECIAL");
            invoice.setTaxRate(new BigDecimal("0.13"));
            invoice.setCertificationStatus("UNCERTIFIED");
            invoice.setDeleted(0);
            inputInvoiceMapper.insert(invoice);

            BusinessDocEntity doc = new BusinessDocEntity();
            doc.setDocNo(docNo);
            doc.setDocType("INVOICE_IN");
            doc.setPeriod("202606");
            doc.setAmount(new BigDecimal("11300.00"));
            doc.setInvoiceNo(invoiceNo);
            doc.setStatus("PENDING_CONFIRM");
            doc.setSupplierId(1L);
            doc.setUnsettledAmount(new BigDecimal("11300.00"));
            businessDocMapper.insert(doc);

            VoucherEntity voucher = new VoucherEntity();
            voucher.setVoucherNo(voucherNo);
            voucher.setPeriod("202606");
            voucher.setVoucherTypeId(1L);
            voucher.setStatus("DRAFT");
            voucher.setSource("GENERATED");
            voucher.setSummary("采购发票转凭证");
            voucher.setTotalDebit(new BigDecimal("11300.00"));
            voucher.setTotalCredit(new BigDecimal("11300.00"));
            voucher.setSourceDocType("INPUT_INVOICE");
            voucher.setSourceDocNo(invoiceNo);
            voucher.setDeleted(0);
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

            VoucherEntity foundVoucher = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getSourceDocNo, invoiceNo)
            );
            assertNotNull(foundVoucher, "凭证应通过 sourceDocNo 被查到");
            assertEquals(invoiceNo, foundVoucher.getSourceDocNo());
        }
    }
}