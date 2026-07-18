package com.huicai.module.finance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.finance.dto.BusinessDocQueryDTO;
import com.huicai.module.finance.dto.BusinessDocVO;
import com.huicai.module.finance.service.BusinessDocService;
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
    private final BusinessDocService businessDocService;

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
    public R<IPage<BusinessDocVO>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setCurrent(current);
        query.setSize(size);
        query.setDocType("INVOICE_OUT");
        return R.ok(businessDocService.pageQuery(query));
    }

    @Operation(summary = "批量红冲关联: 扫描现有红字发票, 按金额+客户名匹配蓝字并标记REVERSED")
    @PostMapping("/batch-link-red-flush")
    public R<Map<String, Object>> batchLinkRedFlush() {
        return R.ok(importService.batchLinkRedFlushInvoices());
    }
}