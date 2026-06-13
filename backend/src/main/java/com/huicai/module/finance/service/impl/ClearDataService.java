package com.huicai.module.finance.service.impl;

import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
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

    private final BankStatementMapper bankStatementMapper;
    private final BusinessDocMapper businessDocMapper;
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

    public int clearAll() {
        int s = clearBankStatements();
        int i = clearInvoiceRecords();
        int v = clearVouchers();
        log.info("清空全部: total={}", s + i + v);
        return s + i + v;
    }
}