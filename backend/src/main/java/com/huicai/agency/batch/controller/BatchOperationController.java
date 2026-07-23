package com.huicai.agency.batch.controller;

import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.agency.batch.service.BatchAuditService;
import com.huicai.agency.batch.service.BatchCloseService;
import com.huicai.agency.batch.service.BatchImportService;
import com.huicai.common.response.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agency/batch")
@RequiredArgsConstructor
public class BatchOperationController {

    private final BatchImportService batchImportService;
    private final BatchAuditService batchAuditService;
    private final BatchCloseService batchCloseService;

    @PostMapping("/import")
    public R<BatchResultVO> importInvoices(@RequestParam List<MultipartFile> files,
                                            @RequestParam Long enterpriseId) {
        return R.ok(batchImportService.importInvoices(files, enterpriseId));
    }

    @PostMapping("/audit-vouchers")
    public R<BatchResultVO> auditVouchers(@RequestBody List<Long> voucherIds,
                                           @RequestParam Long enterpriseId) {
        return R.ok(batchAuditService.auditVouchers(voucherIds, enterpriseId));
    }

    @PostMapping("/audit-invoices")
    public R<BatchResultVO> auditInvoices(@RequestBody List<Long> invoiceIds,
                                           @RequestParam Long enterpriseId) {
        return R.ok(batchAuditService.auditInvoices(invoiceIds, enterpriseId));
    }

    @PostMapping("/close")
    public R<BatchResultVO> closePeriods(@RequestBody List<Long> enterpriseIds,
                                          @RequestParam String period) {
        return R.ok(batchCloseService.closePeriods(enterpriseIds, period));
    }
}
