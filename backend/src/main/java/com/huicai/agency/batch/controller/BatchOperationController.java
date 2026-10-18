package com.huicai.agency.batch.controller;

import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.agency.security.AgencyEnterpriseRoleGuard;
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
    private final AgencyEnterpriseRoleGuard roleGuard;

    @PostMapping("/import")
    public R<BatchResultVO> importInvoices(@RequestParam List<MultipartFile> files,
                                            @RequestParam Long enterpriseId) {
        // SPEC-P120：批量操作是跨客户管理动作。本端点当前为 501 未实现桩，
        // 但实现放开时若无守卫即成为越权入口 —— 故在此先钉住。
        roleGuard.requireAgencyAdmin("批量导入");
        return R.ok(batchImportService.importInvoices(files, enterpriseId));
    }

    @PostMapping("/audit-vouchers")
    public R<BatchResultVO> auditVouchers(@RequestBody List<Long> voucherIds,
                                           @RequestParam Long enterpriseId) {
        roleGuard.requireAgencyAdmin("批量审核凭证");
        return R.ok(batchAuditService.auditVouchers(voucherIds, enterpriseId));
    }

    @PostMapping("/audit-invoices")
    public R<BatchResultVO> auditInvoices(@RequestBody List<Long> invoiceIds,
                                           @RequestParam Long enterpriseId) {
        roleGuard.requireAgencyAdmin("批量审核发票");
        return R.ok(batchAuditService.auditInvoices(invoiceIds, enterpriseId));
    }

    @PostMapping("/close")
    public R<BatchResultVO> closePeriods(@RequestBody List<Long> enterpriseIds,
                                          @RequestParam String period) {
        roleGuard.requireAgencyAdmin("批量结账");
        return R.ok(batchCloseService.closePeriods(enterpriseIds, period));
    }
}
