package com.huicai.module.finance.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import com.huicai.module.arap.entity.ArapSettlementEntryEntity;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.ArapSettlementEntryMapper;
import com.huicai.module.arap.mapper.ArapSettlementMapper;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.finance.entity.VoucherEntity;
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
 * 编号关联体系 - 核销链路端到端测试 (L3 / @SlowTest)
 *
 * 核心验证：应收/应付单 → 核销单 → 凭证 之间的编号传递。
 * 通过 t_arap_settlement_entry 明细表实现应收/应付 ↔ 核销的双向关联。
 */
@DisplayName("编号关联 - 核销链路端到端测试")
public class NumberingSettlementE2ETest extends AbstractMapperTest {

    @Autowired private ReceivableMapper receivableMapper;
    @Autowired private PayableMapper payableMapper;
    @Autowired private ArapSettlementMapper settlementMapper;
    @Autowired private ArapSettlementEntryMapper settlementEntryMapper;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;

    // ==================== 核销链路测试 ====================

    @Nested
    @DisplayName("核销链路: 应收单 → 核销单 → 凭证")
    class ReceivableSettlementChainTest {

        /**
         * T5-1: 核销单生成凭证时，应收单的 voucherNo 应被回写
         */
        @Test
        @DisplayName("核销单生成凭证后，应收单 voucherNo 被回写")
        void settlement_generates_voucher_and_writes_back() {
            // 1. 插入销项发票
            OutputInvoiceEntity invoice = new OutputInvoiceEntity();
            invoice.setInvoiceNo("9999.E2E.SETTLE.INV.001");
            invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
            invoice.setPeriod("202606");
            invoice.setAmount(new BigDecimal("10000.00"));
            invoice.setTaxAmount(new BigDecimal("1300.00"));
            invoice.setTotalAmount(new BigDecimal("11300.00"));
            invoice.setInvoiceType("SPECIAL");
            invoice.setStatus("CONFIRMED");
            invoice.setDeleted(0);
            invoice.setTaxRate(new BigDecimal("0.13"));
            outputInvoiceMapper.insert(invoice);

            // 2. 插入应收单
            ReceivableEntity receivable = new ReceivableEntity();
            receivable.setDocNo("9999.E2E.SETTLE.REC.001");
            receivable.setPeriod("202606");
            receivable.setAmount(new BigDecimal("11300.00"));
            receivable.setInvoiceNo("9999.E2E.SETTLE.INV.001");
            receivable.setStatus("PENDING_CONFIRM");
            receivable.setDeleted(0);
            receivable.setUnsettledAmount(new BigDecimal("5000.00"));
            receivable.setCustomerId(1L);
            receivable.setTxDate(LocalDate.of(2026, 6, 28));
            receivableMapper.insert(receivable);

            // 3. 插入核销单
            ArapSettlementEntity settlement = new ArapSettlementEntity();
            settlement.setSettlementNo("9999.E2E.SETTLE.SET.001");
            settlement.setSettlementType("RECEIVE");
            settlement.setSettlementDate(LocalDate.of(2026, 6, 28));
            settlement.setPeriod("202606");
            settlement.setTotalAmount(new BigDecimal("11300.00"));
            settlement.setStatus("CONFIRMED");
            settlement.setPartyId(1L);
            settlement.setPartyType("CUSTOMER");
            settlementMapper.insert(settlement);

            // 4. 插入核销明细（关联应收单 ID）
            ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
            entry.setSettlementId(settlement.getId());
            entry.setReceivableId(receivable.getId());
            entry.setSettledAmount(new BigDecimal("11300.00"));
            entry.setDiscountAmount(BigDecimal.ZERO);
            settlementEntryMapper.insert(entry);

            // 5. 模拟 ArapSettlementServiceImpl.generateVoucher()
            //    生成凭证并回写 voucherNo 到核销单和应收单
            VoucherEntity voucher = new VoucherEntity();
            voucher.setVoucherNo("9999.E2E.SETTLE.VCH.001");
            voucher.setPeriod("202606");
            voucher.setVoucherTypeId(1L);
            voucher.setStatus("DRAFT");
            voucher.setSource("GENERATED");
            voucher.setSummary("往来核销生成");
            voucher.setTotalDebit(new BigDecimal("11300.00"));
            voucher.setTotalCredit(new BigDecimal("11300.00"));
            voucher.setSourceDocType("SETTLEMENT");
            voucher.setSourceDocNo("9999.E2E.SETTLE.SET.001");
            voucher.setDeleted(0);
            voucherMapper.insert(voucher);

            // 6. 回写核销单 voucherNo
            settlement.setVoucherId(voucher.getId());
            settlement.setVoucherNo("9999.E2E.SETTLE.VCH.001");
            settlementMapper.updateById(settlement);

            // 7. 回写应收单 voucherNo（通过 settlementEntryMapper 查询明细找到应收单 ID）
            ArapSettlementEntryEntity foundEntry = settlementEntryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArapSettlementEntryEntity>()
                    .eq(ArapSettlementEntryEntity::getSettlementId, settlement.getId())
            );
            if (foundEntry != null && foundEntry.getReceivableId() != null) {
                ReceivableEntity toUpdate = receivableMapper.selectById(foundEntry.getReceivableId());
                if (toUpdate != null) {
                    toUpdate.setVoucherNo("9999.E2E.SETTLE.VCH.001");
                    receivableMapper.updateById(toUpdate);
                }
            }

            // 8. 验证
            // 8a. 应收单 voucherNo 被回写
            ReceivableEntity updatedReceivable = receivableMapper.selectById(receivable.getId());
            assertNotNull(updatedReceivable);
            assertEquals("9999.E2E.SETTLE.VCH.001", updatedReceivable.getVoucherNo(),
                "应收单 voucherNo 应被回写");

            // 8b. 核销单 voucherNo 被设置
            ArapSettlementEntity updatedSettlement = settlementMapper.selectById(settlement.getId());
            assertNotNull(updatedSettlement);
            assertEquals("9999.E2E.SETTLE.VCH.001", updatedSettlement.getVoucherNo(),
                "核销单 voucherNo 应被设置");

            // 8c. 凭证 sourceDocNo = 核销单号
            VoucherEntity foundVoucher = voucherMapper.selectById(voucher.getId());
            assertNotNull(foundVoucher);
            assertEquals("SETTLEMENT", foundVoucher.getSourceDocType());
            assertEquals("9999.E2E.SETTLE.SET.001", foundVoucher.getSourceDocNo());
        }

        /**
         * T5-2: 通过应收单 ID 查核销明细 → 核销单 → 凭证
         */
        @Test
        @DisplayName("应收单 → 核销明细 → 核销单 → 凭证 正向追溯")
        void receivable_to_settlement_to_voucher_forward() {
            // 1. 插入应收单
            ReceivableEntity receivable = new ReceivableEntity();
            receivable.setDocNo("9999.E2E.SETTLE.REC.002");
            receivable.setPeriod("202606");
            receivable.setAmount(new BigDecimal("5000.00"));
            receivable.setInvoiceNo("9999.E2E.SETTLE.INV.002");
            receivable.setStatus("PENDING_CONFIRM");
            receivable.setDeleted(0);
            receivable.setUnsettledAmount(new BigDecimal("5000.00"));
            receivable.setCustomerId(1L);
            receivable.setTxDate(LocalDate.of(2026, 6, 28));
            receivableMapper.insert(receivable);

            // 2. 插入核销单
            ArapSettlementEntity settlement = new ArapSettlementEntity();
            settlement.setSettlementNo("9999.E2E.SETTLE.SET.002");
            settlement.setSettlementType("RECEIVE");
            settlement.setSettlementDate(LocalDate.of(2026, 6, 28));
            settlement.setPeriod("202606");
            settlement.setTotalAmount(new BigDecimal("5000.00"));
            settlement.setStatus("CONFIRMED");
            settlement.setPartyId(1L);
            settlement.setPartyType("CUSTOMER");
            settlementMapper.insert(settlement);

            // 3. 插入核销明细
            ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
            entry.setSettlementId(settlement.getId());
            entry.setReceivableId(receivable.getId());
            entry.setSettledAmount(new BigDecimal("5000.00"));
            entry.setDiscountAmount(BigDecimal.ZERO);
            settlementEntryMapper.insert(entry);

            // 4. 插入凭证
            VoucherEntity voucher = new VoucherEntity();
            voucher.setVoucherNo("9999.E2E.SETTLE.VCH.002");
            voucher.setPeriod("202606");
            voucher.setVoucherTypeId(1L);
            voucher.setStatus("DRAFT");
            voucher.setSource("GENERATED");
            voucher.setSummary("核销生成凭证");
            voucher.setTotalDebit(new BigDecimal("5000.00"));
            voucher.setTotalCredit(new BigDecimal("5000.00"));
            voucher.setSourceDocType("SETTLEMENT");
            voucher.setSourceDocNo("9999.E2E.SETTLE.SET.002");
            voucher.setDeleted(0);
            voucherMapper.insert(voucher);

            // 5. 回写
            settlement.setVoucherId(voucher.getId());
            settlement.setVoucherNo("9999.E2E.SETTLE.VCH.002");
            settlementMapper.updateById(settlement);

            receivable.setVoucherId(voucher.getId());
            receivable.setVoucherNo("9999.E2E.SETTLE.VCH.002");
            receivableMapper.updateById(receivable);

            // 6. 验证：应收单 ID → 核销明细 → 核销单 → 凭证
            ArapSettlementEntryEntity foundEntry = settlementEntryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArapSettlementEntryEntity>()
                    .eq(ArapSettlementEntryEntity::getReceivableId, receivable.getId())
            );
            assertNotNull(foundEntry, "应通过 receivableId 查到核销明细");

            ArapSettlementEntity foundSettlement = settlementMapper.selectById(foundEntry.getSettlementId());
            assertNotNull(foundSettlement);
            assertEquals("9999.E2E.SETTLE.VCH.002", foundSettlement.getVoucherNo());
        }
    }

    @Nested
    @DisplayName("核销链路: 应付单 → 核销单 → 凭证")
    class PayableSettlementChainTest {

        /**
         * T5-3: 核销应付单时，凭证通过 payableId 关联
         */
        @Test
        @DisplayName("应付单核销后，凭证通过 payableId 可追溯到应付单")
        void payable_settlement_via_payableId() {
            // 1. 插入进项发票
            InputInvoiceEntity invoice = new InputInvoiceEntity();
            invoice.setInvoiceNo("9999.E2E.PURC.INV.003");
            invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
            invoice.setPeriod("202606");
            invoice.setAmount(new BigDecimal("10000.00"));
            invoice.setTaxAmount(new BigDecimal("1300.00"));
            invoice.setTotalAmount(new BigDecimal("11300.00"));
            invoice.setInvoiceType("SPECIAL");
            invoice.setCertificationStatus("UNCERTIFIED");
            invoice.setDeleted(0);
            invoice.setTaxRate(new BigDecimal("0.13"));
            inputInvoiceMapper.insert(invoice);

            // 2. 插入应付单
            PayableEntity payable = new PayableEntity();
            payable.setDocNo("9999.E2E.PURC.PAY.003");
            payable.setPeriod("202606");
            payable.setAmount(new BigDecimal("11300.00"));
            payable.setInvoiceNo("9999.E2E.PURC.INV.003");
            payable.setStatus("PENDING_CONFIRM");
            payable.setDeleted(0);
            payable.setUnsettledAmount(new BigDecimal("11300.00"));
            payable.setVendorId(1L);
            payable.setTxDate(LocalDate.of(2026, 6, 28));
            payableMapper.insert(payable);

            // 3. 插入核销单
            ArapSettlementEntity settlement = new ArapSettlementEntity();
            settlement.setSettlementNo("9999.E2E.PURC.SET.003");
            settlement.setSettlementType("PAY");
            settlement.setSettlementDate(LocalDate.of(2026, 6, 28));
            settlement.setPeriod("202606");
            settlement.setTotalAmount(new BigDecimal("11300.00"));
            settlement.setStatus("CONFIRMED");
            settlement.setPartyId(1L);
            settlement.setPartyType("CUSTOMER");
            settlementMapper.insert(settlement);

            // 4. 插入核销明细（关联应付单 ID）
            ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
            entry.setSettlementId(settlement.getId());
            entry.setPayableId(payable.getId());
            entry.setSettledAmount(new BigDecimal("11300.00"));
            entry.setDiscountAmount(BigDecimal.ZERO);
            settlementEntryMapper.insert(entry);

            // 5. 插入凭证
            VoucherEntity voucher = new VoucherEntity();
            voucher.setVoucherNo("9999.E2E.PURC.VCH.003");
            voucher.setPeriod("202606");
            voucher.setVoucherTypeId(1L);
            voucher.setStatus("DRAFT");
            voucher.setSource("GENERATED");
            voucher.setSummary("应付核销生成凭证");
            voucher.setTotalDebit(new BigDecimal("11300.00"));
            voucher.setTotalCredit(new BigDecimal("11300.00"));
            voucher.setSourceDocType("SETTLEMENT");
            voucher.setSourceDocNo("9999.E2E.PURC.SET.003");
            voucher.setDeleted(0);
            voucherMapper.insert(voucher);

            // 6. 回写
            settlement.setVoucherId(voucher.getId());
            settlement.setVoucherNo("9999.E2E.PURC.VCH.003");
            settlementMapper.updateById(settlement);

            payable.setVoucherId(voucher.getId());
            payable.setVoucherNo("9999.E2E.PURC.VCH.003");
            payableMapper.updateById(payable);

            // 7. 验证：应付单 ID → 核销明细 → 核销单 → 凭证
            ArapSettlementEntryEntity foundEntry = settlementEntryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArapSettlementEntryEntity>()
                    .eq(ArapSettlementEntryEntity::getPayableId, payable.getId())
            );
            assertNotNull(foundEntry, "应通过 payableId 查到核销明细");

            ArapSettlementEntity foundSettlement = settlementMapper.selectById(foundEntry.getSettlementId());
            assertNotNull(foundSettlement);
            assertEquals("9999.E2E.PURC.VCH.003", foundSettlement.getVoucherNo());

            PayableEntity foundPayable = payableMapper.selectById(payable.getId());
            assertEquals("9999.E2E.PURC.VCH.003", foundPayable.getVoucherNo(),
                "应付单 voucherNo 应被回写");
        }
    }
}
