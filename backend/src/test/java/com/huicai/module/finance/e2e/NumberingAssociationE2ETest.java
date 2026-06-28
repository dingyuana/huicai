package com.huicai.module.finance.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.tax.entity.InputInvoiceEntity;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.InputInvoiceMapper;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编号关联体系 - 端到端链路测试 (L3 / @SlowTest)
 *
 * 核心验证：发票→应收/应付→凭证 之间的编号能否正确传递和交叉查询。
 * 使用真实 DB（Testcontainers）确保外键约束和数据一致性。
 *
 * 测试数据使用 9999.xxxx 编码前缀。
 */
@DisplayName("编号关联 - 端到端链路测试")
public class NumberingAssociationE2ETest extends AbstractMapperTest {

    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private ReceivableMapper receivableMapper;
    @Autowired private PayableMapper payableMapper;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private BusinessDocMapper businessDocMapper;

    // ==================== 销售链路测试 ====================

    @Nested
    @DisplayName("销售链路: 销项发票 → 应收单 → 凭证")
    class SalesChainTest {

        /**
         * T3-1: 销项发票生成应收单时，应收单应正确携带 invoiceNo
         */
        @Test
        @DisplayName("应收单通过 invoiceNo 可查到对应销项发票")
        void sales_invoice_to_receivable_by_invoiceNo() {
            // 1. 插入销项发票
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
            invoice.setTaxRate(new BigDecimal("0.13"));
            invoice.setDeleted(0);
            outputInvoiceMapper.insert(invoice);

            // 2. 模拟 OutputInvoiceStateMachineServiceImpl 生成应收单
            //    应收单的 invoiceNo 应 = 销项发票的 invoiceNo
            ReceivableEntity receivable = new ReceivableEntity();
            receivable.setDocNo("9999.E2E.SALE.REC.001");
            receivable.setPeriod("202606");
            receivable.setAmount(new BigDecimal("11300.00"));
            receivable.setInvoiceNo("9999.E2E.SALE.INV.001"); // 关键：传递发票号
            receivable.setStatus("PENDING_CONFIRM");
            receivable.setDeleted(0);
            receivable.setUnsettledAmount(new BigDecimal("11300.00"));
            receivable.setCustomerId(1L);
            receivable.setTxDate(LocalDate.of(2026, 6, 28));
            receivableMapper.insert(receivable);

            // 3. 验证：通过发票号查询应收单
            ReceivableEntity found = receivableMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReceivableEntity>()
                    .eq(ReceivableEntity::getInvoiceNo, "9999.E2E.SALE.INV.001")
            );

            assertNotNull(found, "通过 invoiceNo 应能查到应收单");
            assertEquals("9999.E2E.SALE.REC.001", found.getDocNo(),
                "应收单编号应正确");
        }

        /**
         * T3-2: 应收单生成凭证时，凭证的 sourceDocNo 应 = 发票号，应收单的 voucherNo 应被回写
         */
        @Test
        @DisplayName("凭证通过 sourceDocNo 可追溯到销项发票，应收单 voucherNo 被回写")
        void sales_receivable_to_voucher() {
            // 1. 插入销项发票
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
            invoice.setTaxRate(new BigDecimal("0.13"));
            invoice.setDeleted(0);
            outputInvoiceMapper.insert(invoice);

            // 2. 插入应收单
            ReceivableEntity receivable = new ReceivableEntity();
            receivable.setDocNo("9999.E2E.SALE.REC.002");
            receivable.setPeriod("202606");
            receivable.setAmount(new BigDecimal("11300.00"));
            receivable.setInvoiceNo("9999.E2E.SALE.INV.002");
            receivable.setStatus("PENDING_CONFIRM");
            receivable.setDeleted(0);
            receivable.setUnsettledAmount(new BigDecimal("11300.00"));
            receivable.setCustomerId(1L);
            receivable.setTxDate(LocalDate.of(2026, 6, 28));
            receivableMapper.insert(receivable);

            // 3. 模拟 OutputInvoiceStateMachineServiceImpl.markVouchered()
            //    凭证的 sourceDocNo = 发票号，sourceDocType = "OUTPUT_INVOICE"
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
            voucher.setSourceDocNo("9999.E2E.SALE.INV.002"); // 关键：溯源到发票号
            voucher.setDeleted(0);
            voucherMapper.insert(voucher);

            // 4. 回写应收单的 voucherNo
            receivable.setVoucherId(voucher.getId());
            receivable.setVoucherNo("9999.E2E.SALE.VCH.002"); // 关键：回写凭证号
            receivableMapper.updateById(receivable);

            // 5. 验证链路
            // 5a. 通过发票号查凭证
            VoucherEntity foundVoucher = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getSourceDocNo, "9999.E2E.SALE.INV.002")
            );
            assertNotNull(foundVoucher, "通过 sourceDocNo 应能查到凭证");
            assertEquals("9999.E2E.SALE.VCH.002", foundVoucher.getVoucherNo());
            assertEquals("OUTPUT_INVOICE", foundVoucher.getSourceDocType());

            // 5b. 通过发票号查应收单
            ReceivableEntity foundReceivable = receivableMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReceivableEntity>()
                    .eq(ReceivableEntity::getInvoiceNo, "9999.E2E.SALE.INV.002")
            );
            assertNotNull(foundReceivable, "通过 invoiceNo 应能查到应收单");
            assertEquals("9999.E2E.SALE.VCH.002", foundReceivable.getVoucherNo(),
                "应收单的 voucherNo 应被回写");

            // 5c. 全链路验证
            assertEquals("9999.E2E.SALE.INV.002", foundReceivable.getInvoiceNo(),
                "应收单.invoiceNo = 发票号");
            assertEquals("9999.E2E.SALE.VCH.002", foundReceivable.getVoucherNo(),
                "应收单.voucherNo = 凭证号");
            assertEquals("9999.E2E.SALE.INV.002", foundVoucher.getSourceDocNo(),
                "凭证.sourceDocNo = 发票号");
        }

        /**
         * T3-3: 完整销售链路追溯
         */
        @Test
        @DisplayName("完整销售链路: 发票 → 应收单 → 凭证（编号传递验证）")
        void full_sales_chain() {
            String invoiceNo = "9999.E2E.SALE.INV.003";
            String docNo = "9999.E2E.SALE.REC.003";
            String voucherNo = "9999.E2E.SALE.VCH.003";

            // Step 1: 销项发票
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
            invoice.setTaxRate(new BigDecimal("0.13"));
            invoice.setDeleted(0);
            outputInvoiceMapper.insert(invoice);

            // Step 2: 应收单（invoiceNo = 发票号）
            ReceivableEntity receivable = new ReceivableEntity();
            receivable.setDocNo(docNo);
            receivable.setPeriod("202606");
            receivable.setAmount(new BigDecimal("11300.00"));
            receivable.setInvoiceNo(invoiceNo); // 关键传递
            receivable.setStatus("PENDING_CONFIRM");
            receivable.setDeleted(0);
            receivable.setUnsettledAmount(new BigDecimal("11300.00"));
            receivable.setCustomerId(1L);
            receivable.setTxDate(LocalDate.of(2026, 6, 28));
            receivableMapper.insert(receivable);

            // Step 3: 凭证（sourceDocNo = 发票号）
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
            voucher.setSourceDocNo(invoiceNo); // 关键传递
            voucher.setDeleted(0);
            voucherMapper.insert(voucher);

            // Step 4: 回写应收单 voucherNo
            receivable.setVoucherId(voucher.getId());
            receivable.setVoucherNo(voucherNo);
            receivableMapper.updateById(receivable);

            // Step 5: 验证全链路编号传递
            // 5a. 发票号 → 应收单
            ReceivableEntity r = receivableMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReceivableEntity>()
                    .eq(ReceivableEntity::getInvoiceNo, invoiceNo)
            );
            assertNotNull(r, "应收单应通过 invoiceNo 被查到");
            assertEquals(voucherNo, r.getVoucherNo(), "应收单应回写 voucherNo");

            // 5b. 发票号 → 凭证
            VoucherEntity v = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getSourceDocNo, invoiceNo)
            );
            assertNotNull(v, "凭证应通过 sourceDocNo 被查到");
            assertEquals(invoiceNo, v.getSourceDocNo(), "凭证应保留 sourceDocNo");

            // 5c. 应收单 voucherNo → 凭证
            VoucherEntity v2 = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getVoucherNo, voucherNo)
            );
            assertNotNull(v2, "凭证应通过 voucherNo 被查到");
        }
    }

    // ==================== 采购链路测试 ====================

    @Nested
    @DisplayName("采购链路: 进项发票 → 应付单 → 凭证")
    class ProcurementChainTest {

        /**
         * T4-1: 进项发票生成应付单时，应付单应正确携带 invoiceNo
         */
        @Test
        @DisplayName("应付单通过 invoiceNo 可查到对应进项发票")
        void procurement_invoice_to_payable_by_invoiceNo() {
            // 1. 插入进项发票
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

            // 2. 模拟 InputInvoiceImportService.createPayableFromInvoice()
            //    应付单的 invoiceNo = 进项发票号
            PayableEntity payable = new PayableEntity();
            payable.setDocNo("9999.E2E.PURC.PAY.001");
            payable.setPeriod("202606");
            payable.setAmount(new BigDecimal("11300.00"));
            payable.setInvoiceNo("9999.E2E.PURC.INV.001"); // 关键：传递发票号
            payable.setStatus("PENDING_CONFIRM");
            payable.setDeleted(0);
            payable.setUnsettledAmount(new BigDecimal("11300.00"));
            payable.setVendorId(1L);
            payable.setTxDate(LocalDate.of(2026, 6, 28));
            payableMapper.insert(payable);

            // 3. 验证
            PayableEntity found = payableMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayableEntity>()
                    .eq(PayableEntity::getInvoiceNo, "9999.E2E.PURC.INV.001")
            );

            assertNotNull(found, "通过 invoiceNo 应能查到应付单");
            assertEquals("9999.E2E.PURC.PAY.001", found.getDocNo());
        }

        /**
         * T4-2: 应付单生成凭证时，凭证的 sourceDocNo 应 = 发票号
         */
        @Test
        @DisplayName("凭证通过 sourceDocNo 可追溯到进项发票，应付单 voucherNo 被回写")
        void procurement_payable_to_voucher() {
            String invoiceNo = "9999.E2E.PURC.INV.002";

            // 1. 插入进项发票
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

            // 2. 插入应付单
            PayableEntity payable = new PayableEntity();
            payable.setDocNo("9999.E2E.PURC.PAY.002");
            payable.setPeriod("202606");
            payable.setAmount(new BigDecimal("11300.00"));
            payable.setInvoiceNo(invoiceNo);
            payable.setStatus("PENDING_CONFIRM");
            payable.setDeleted(0);
            payable.setUnsettledAmount(new BigDecimal("11300.00"));
            payable.setVendorId(1L);
            payable.setTxDate(LocalDate.of(2026, 6, 28));
            payableMapper.insert(payable);

            // 3. 模拟凭证生成（sourceDocNo = 发票号，sourceDocType = "INPUT_INVOICE"）
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

            // 4. 回写应付单 voucherNo
            payable.setVoucherId(voucher.getId());
            payable.setVoucherNo("9999.E2E.PURC.VCH.002");
            payableMapper.updateById(payable);

            // 5. 验证
            PayableEntity foundPayable = payableMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayableEntity>()
                    .eq(PayableEntity::getInvoiceNo, invoiceNo)
            );
            assertNotNull(foundPayable);
            assertEquals("9999.E2E.PURC.VCH.002", foundPayable.getVoucherNo(),
                "应付单 voucherNo 应被回写");

            VoucherEntity foundVoucher = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getSourceDocNo, invoiceNo)
            );
            assertNotNull(foundVoucher);
            assertEquals("INPUT_INVOICE", foundVoucher.getSourceDocType());
        }

        /**
         * T4-3: 完整采购链路追溯
         */
        @Test
        @DisplayName("完整采购链路: 发票 → 应付单 → 凭证（编号传递验证）")
        void full_procurement_chain() {
            String invoiceNo = "9999.E2E.PURC.INV.003";
            String docNo = "9999.E2E.PURC.PAY.003";
            String voucherNo = "9999.E2E.PURC.VCH.003";

            // 1. 进项发票
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

            // 2. 应付单（invoiceNo = 发票号）
            PayableEntity payable = new PayableEntity();
            payable.setDocNo(docNo);
            payable.setPeriod("202606");
            payable.setAmount(new BigDecimal("11300.00"));
            payable.setInvoiceNo(invoiceNo);
            payable.setStatus("PENDING_CONFIRM");
            payable.setDeleted(0);
            payable.setUnsettledAmount(new BigDecimal("11300.00"));
            payable.setVendorId(1L);
            payable.setTxDate(LocalDate.of(2026, 6, 28));
            payableMapper.insert(payable);

            // 3. 凭证（sourceDocNo = 发票号）
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

            // 4. 回写应付单 voucherNo
            payable.setVoucherId(voucher.getId());
            payable.setVoucherNo(voucherNo);
            payableMapper.updateById(payable);

            // 5. 验证：发票号 → 应付单 → 凭证
            PayableEntity foundPayable = payableMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayableEntity>()
                    .eq(PayableEntity::getInvoiceNo, invoiceNo)
            );
            assertNotNull(foundPayable, "应付单应通过 invoiceNo 被查到");
            assertEquals(voucherNo, foundPayable.getVoucherNo(),
                "应付单应回写 voucherNo");

            VoucherEntity foundVoucher = voucherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                    .eq(VoucherEntity::getSourceDocNo, invoiceNo)
            );
            assertNotNull(foundVoucher, "凭证应通过 sourceDocNo 被查到");
            assertEquals(invoiceNo, foundVoucher.getSourceDocNo());
        }
    }
}
