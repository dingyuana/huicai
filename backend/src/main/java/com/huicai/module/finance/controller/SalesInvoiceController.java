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

    @Operation(summary = "第一步: 上传销售发票Excel, 解析为预览数据, 不写入数据库")
    @PostMapping("/preview")
    public R<Map<String, Object>> previewInvoices(@RequestParam("file") MultipartFile file) {
        return R.ok(importService.previewInvoices(file));
    }

    @Operation(summary = "第二步: 用户确认预览后, 真正写入数据库 + 生成单据 + 生成凭证")
    @PostMapping("/confirm-import")
    public R<Map<String, Object>> confirmImport(@RequestParam String batchId) {
        return R.ok(importService.confirmImport(batchId));
    }

    @Operation(summary = "一步式导入 (兼容旧调用, 内部走 preview + confirm)")
    @PostMapping("/import")
    public R<Map<String, Object>> importInvoices(@RequestParam("file") MultipartFile file) {
        return R.ok(importService.importInvoices(file));
    }

    @Operation(summary = "查询导入记录")
    @GetMapping("/page")
    public R<?> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(Map.of("message", "请通过 /api/v1/business-docs/page?docType=INVOICE_OUT&source=INVOICE_IMPORT 查看"));
    }
}