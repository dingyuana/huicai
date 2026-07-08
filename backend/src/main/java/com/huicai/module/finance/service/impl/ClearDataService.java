package com.huicai.module.finance.service.impl;

import com.huicai.module.arap.mapper.ArapSettlementEntryMapper;
import com.huicai.module.arap.mapper.ReconciliationLogMapper;
import com.huicai.module.finance.mapper.BankJournalMapper;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.tax.mapper.InputInvoiceMapper;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClearDataService {

    private final BankJournalMapper bankJournalMapper;
    private final BankStatementMapper bankStatementMapper;
    private final BusinessDocMapper businessDocMapper;
    private final BusinessDocEntryMapper businessDocEntryMapper;
    private final ArapSettlementEntryMapper settlementEntryMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final OutputInvoiceMapper outputInvoiceMapper;
    private final InputInvoiceMapper inputInvoiceMapper;
    private final ReconciliationLogMapper reconciliationLogMapper;

    public int clearBankStatements() {
        int ve = 0, v = 0, d = 0, s = 0;
        try { ve = voucherEntryMapper.deleteByVoucherSource("FROM_BANK_TXN"); } catch (Exception e) { log.warn("voucher_entry: {}", e.getMessage()); }
        try { v = voucherMapper.deleteBySource("FROM_BANK_TXN"); } catch (Exception e) { log.warn("voucher: {}", e.getMessage()); }
        try { d = businessDocMapper.deleteBySource("FROM_BANK_TXN"); } catch (Exception e) { log.warn("doc: {}", e.getMessage()); }
        try { s = bankStatementMapper.physicalDeleteAll(); } catch (Exception e) { log.warn("statement: {}", e.getMessage()); }
        log.info("清空银行流水: statements={}, docs={}, vouchers={}, entries={}", s, d, v, ve);
        return s + d + v + ve;
    }

    public int clearInvoiceRecords() {
        int ve = 0, v = 0, d = 0, oi = 0, ii = 0, ni = 0;
        // 1. Null out invoice_id on business_doc before deleting invoices
        try { ni = businessDocMapper.nullOutInvoiceId(); } catch (Exception e) { log.warn("doc nullOutInvoiceId: {}", e.getMessage()); }
        // 2. Delete input_invoice (no FK pointing to business_doc)
        try { ii = inputInvoiceMapper.physicalDeleteAll(); } catch (Exception e) { log.warn("input_invoice: {}", e.getMessage()); }
        // 3. Delete output_invoice (FK t_business_doc.invoice_id already nulled)
        try { oi = outputInvoiceMapper.physicalDeleteAll(); } catch (Exception e) { log.warn("output_invoice: {}", e.getMessage()); }
        // 4. Clear related vouchers + entries + docs (source = INVOICE_IMPORT)
        try { ve = voucherEntryMapper.deleteByVoucherSource("INVOICE_IMPORT"); } catch (Exception e) { log.warn("voucher_entry: {}", e.getMessage()); }
        try { v = voucherMapper.deleteBySource("INVOICE_IMPORT"); } catch (Exception e) { log.warn("voucher: {}", e.getMessage()); }
        try { d = businessDocMapper.deleteBySource("INVOICE_IMPORT"); } catch (Exception e) { log.warn("doc: {}", e.getMessage()); }
        log.info("清空发票记录: invoice_id_nulled={}, input_invoices={}, output_invoices={}, docs={}, vouchers={}, entries={}", ni, ii, oi, d, v, ve);
        return ni + ii + oi + d + v + ve;
    }

    public int clearVouchers() {
        int ve = 0, v = 0, d = 0, s = 0, oi = 0;
        try { ve = voucherEntryMapper.deleteAll(); } catch (Exception e) { log.warn("voucher_entry: {}", e.getMessage()); }
        try { v = voucherMapper.deleteAll(); } catch (Exception e) { log.warn("voucher: {}", e.getMessage()); }
        try { d = businessDocMapper.nullOutVoucherIds(); } catch (Exception e) { log.warn("doc nullOutVoucherIds: {}", e.getMessage()); }
        try { s = bankStatementMapper.nullOutGeneratedVoucherIds(); } catch (Exception e) { log.warn("statement nullOutGeneratedVoucherIds: {}", e.getMessage()); }
        try { oi = outputInvoiceMapper.nullOutVoucherIds(); } catch (Exception e) { log.warn("output_invoice nullOutVoucherIds: {}", e.getMessage()); }
        log.info("清空凭证: vouchers={}, entries={}, docs_cleared={}, stmts_cleared={}, invoices_cleared={}", v, ve, d, s, oi);
        return v + ve;
    }

    /**
     * 清空业务单据(含明细行)
     */
    public int clearBusinessDocs() {
        int rr = 0, pr = 0, bjr = 0, e = 0, d = 0, se = 0, rl = 0, vn = 0, ni = 0;
        // 先解除所有外键引用，再物理删除
        try { bjr = bankJournalMapper.nullOutBusinessDocId(); } catch (Exception ex) { log.warn("bank_journal nullOutDocId: {}", ex.getMessage()); }
        try { vn = voucherMapper.nullOutBusinessDocId(); } catch (Exception ex) { log.warn("voucher nullOutBusinessDocId: {}", ex.getMessage()); }
        try { ni = businessDocMapper.nullOutInvoiceId(); } catch (Exception ex) { log.warn("doc nullOutInvoiceId: {}", ex.getMessage()); }
        // settlement_entry + reconciliation_log 引用 business_doc_id，先删
        try { se = settlementEntryMapper.deleteAll(); } catch (Exception ex) { log.warn("settlement_entry: {}", ex.getMessage()); }
        try { rl = reconciliationLogMapper.deleteAll(); } catch (Exception ex) { log.warn("reconciliation_log: {}", ex.getMessage()); }
        // doc_entry 有 ON DELETE CASCADE，先删 entry 再删 doc
        try { e = businessDocEntryMapper.physicalDeleteAll(); } catch (Exception ex) { log.warn("doc_entry: {}", ex.getMessage()); }
        try { d = businessDocMapper.physicalDeleteAll(); } catch (Exception ex) { log.warn("doc: {}", ex.getMessage()); }
        log.info("清空业务单据: refs_nulled(j={},v={},i={}), entries={}, docs={}, settlement_entries={}, recon_logs={}", bjr, vn, ni, e, d, se, rl);
        return e + d + se + rl;
    }

    public int clearAll() {
        int s = clearBankStatements();
        int i = clearInvoiceRecords();
        int v = clearVouchers();
        int d = clearBusinessDocs();
        log.info("清空全部: total={}", s + i + v + d);
        return s + i + v + d;
    }
}