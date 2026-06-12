package com.huicai.module.finance.controller;

import com.huicai.common.response.R;
import com.huicai.module.finance.service.impl.SalesInvoiceImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "销售发票导入")
@RestController
@RequestMapping("/api/v1/sales-invoices")
@RequiredArgsConstructor
public class SalesInvoiceController {

    private final SalesInvoiceImportService importService;

    @Operation(summary = "导入销售发票Excel (恺拓格式)")
    @PostMapping("/import")
    public R<Map<String, Object>> importInvoices(@RequestParam("file") MultipartFile file) {
        return R.ok(importService.importInvoices(file));
    }

    @Operation(summary = "查询导入记录")
    @GetMapping("/page")
    public R<?> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        // 发票导入记录直接通过业务单据列表查看 (docType=INVOICE_OUT, source=INVOICE_IMPORT)
        return R.ok(Map.of("message", "请通过 /api/v1/business-docs/page?docType=INVOICE_OUT&source=INVOICE_IMPORT 查看"));
    }
}