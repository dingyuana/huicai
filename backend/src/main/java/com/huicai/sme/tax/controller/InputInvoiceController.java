package com.huicai.sme.tax.controller;

import com.huicai.common.response.R;
import com.huicai.sme.tax.service.impl.InputInvoiceImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * P10-2: 采购发票 Excel 导入控制器
 */
@Tag(name = "采购发票导入")
@RestController
@RequestMapping("/api/v1/input-invoices")
@RequiredArgsConstructor
public class InputInvoiceController {

    private final InputInvoiceImportService importService;

    @Operation(summary = "第一步: 上传采购发票Excel, 解析为预览数据, 不写入数据库")
    @PostMapping("/preview")
    public R<Map<String, Object>> previewInvoices(@RequestParam("file") MultipartFile file) {
        return R.ok(importService.previewInvoices(file));
    }

    @Operation(summary = "第二步: 用户确认预览后, 真正写入数据库 + 生成单据 + 生成凭证 + 生成应付单")
    @PostMapping("/confirm-import")
    public R<Map<String, Object>> confirmImport(@RequestParam String batchId) {
        return R.ok(importService.confirmImport(batchId));
    }

    @Operation(summary = "一步式导入 (兼容旧调用, 内部走 preview + confirm)")
    @PostMapping("/import")
    public R<Map<String, Object>> importInvoices(@RequestParam("file") MultipartFile file) {
        return R.ok(importService.importInvoices(file));
    }
}
