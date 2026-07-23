package com.huicai.agency.batch.service;

import com.huicai.agency.batch.dto.BatchResultVO;

import java.util.List;

public interface BatchAuditService {
    BatchResultVO auditVouchers(List<Long> voucherIds, Long enterpriseId);
    BatchResultVO auditInvoices(List<Long> invoiceIds, Long enterpriseId);
}
