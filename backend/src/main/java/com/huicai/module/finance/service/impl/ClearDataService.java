package com.huicai.module.finance.service.impl;

import com.huicai.module.arap.mapper.ArapSettlementEntryMapper;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.finance.mapper.BankJournalMapper;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
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
    private final PayableMapper payableMapper;
    private final ReceivableMapper receivableMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final OutputInvoiceMapper outputInvoiceMapper;

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
        int ve = 0, v = 0, d = 0, oi = 0;
        try { oi = outputInvoiceMapper.physicalDeleteAll(); } catch (Exception e) { log.warn("output_invoice: {}", e.getMessage()); }
        try { ve = voucherEntryMapper.deleteByVoucherSource("INVOICE_IMPORT"); } catch (Exception e) { log.warn("voucher_entry: {}", e.getMessage()); }
        try { v = voucherMapper.deleteBySource("INVOICE_IMPORT"); } catch (Exception e) { log.warn("voucher: {}", e.getMessage()); }
        try { d = businessDocMapper.deleteBySource("INVOICE_IMPORT"); } catch (Exception e) { log.warn("doc: {}", e.getMessage()); }
        log.info("清空发票记录: docs={}, vouchers={}, entries={}, output_invoices={}", d, v, ve, oi);
        return d + v + ve + oi;
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
        int rr = 0, pr = 0, bjr = 0, e = 0, d = 0;
        // 先解除外键引用，再物理删除
        try { rr = receivableMapper.nullOutBusinessDocId(); } catch (Exception ex) { log.warn("receivable nullOutDocId: {}", ex.getMessage()); }
        try { pr = payableMapper.nullOutBusinessDocId(); } catch (Exception ex) { log.warn("payable nullOutDocId: {}", ex.getMessage()); }
        try { bjr = bankJournalMapper.nullOutBusinessDocId(); } catch (Exception ex) { log.warn("bank_journal nullOutDocId: {}", ex.getMessage()); }
        try { e = businessDocEntryMapper.physicalDeleteAll(); } catch (Exception ex) { log.warn("doc_entry: {}", ex.getMessage()); }
        try { d = businessDocMapper.physicalDeleteAll(); } catch (Exception ex) { log.warn("doc: {}", ex.getMessage()); }
        log.info("清空业务单据: doc_refs_nulled(r={},p={},j={}), entries={}, docs={}", rr, pr, bjr, e, d);
        return e + d;
    }

    /**
     * 清空应收明细: 先删 settlement_entries 引用, 再删应收
     */
    public int clearReceivables() {
        int entries = 0, docRefs = 0, r = 0;
        try { entries = settlementEntryMapper.deleteByReceivableNotNull(); } catch (Exception ex) { log.warn("settlement_entry: {}", ex.getMessage()); }
        try { docRefs = receivableMapper.nullOutBusinessDocId(); } catch (Exception ex) { log.warn("receivable nullOutDocId: {}", ex.getMessage()); }
        try { r = receivableMapper.physicalDeleteAll(); } catch (Exception ex) { log.warn("receivable: {}", ex.getMessage()); }
        log.info("清空应收: entries={}, docRefs={}, receivables={}", entries, docRefs, r);
        return r;
    }

    /**
     * 清空应付明细: 先删 settlement_entries 引用, 再删应付
     */
    public int clearPayables() {
        int entries = 0, docRefs = 0, p = 0;
        try { entries = settlementEntryMapper.deleteByPayableNotNull(); } catch (Exception ex) { log.warn("settlement_entry: {}", ex.getMessage()); }
        try { docRefs = payableMapper.nullOutBusinessDocId(); } catch (Exception ex) { log.warn("payable nullOutDocId: {}", ex.getMessage()); }
        try { p = payableMapper.physicalDeleteAll(); } catch (Exception ex) { log.warn("payable: {}", ex.getMessage()); }
        log.info("清空应付: entries={}, docRefs={}, payables={}", entries, docRefs, p);
        return p;
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