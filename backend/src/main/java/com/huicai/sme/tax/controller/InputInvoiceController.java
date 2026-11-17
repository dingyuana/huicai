package com.huicai.sme.tax.controller;

import com.huicai.common.response.R;
import com.huicai.sme.tax.service.impl.InputInvoiceImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * P10-2: 采购发票 Excel 导入控制器
 */
@Tag(name = "采购发票导入")
@RestController
@RequestMapping("/api/sme/tax/v1/input-invoices")
@RequiredArgsConstructor
public class InputInvoiceController {

    private final InputInvoiceImportService importService;

    @Operation(summary = "第一步: 上传采购发票Excel, 解析为预览数据, 不写入数据库")
    @PostMapping("/preview")
    // 🔴 SPEC-P153 同族（与 SalesInvoiceController#previewInvoices 同根）：
    //   previewInvoices 里 findExisting 查重 SELECT，无事务 ⇒ GUC 恒 NULL
    //   ⇒ 谓词滤掉全部行 ⇒ existing 恒 0，重复发票的预览提示永不出现。
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public R<Map<String, Object>> previewInvoices(@RequestParam("file") MultipartFile file) {
        return R.ok(importService.previewInvoices(file));
    }

    @Operation(summary = "第二步: 用户确认预览后, 真正写入数据库 + 生成单据 + 生成凭证 + 生成应付单")
    @PostMapping("/confirm-import")
    // 🔴 SPEC-P153 同族：本方法写 t_input_invoice / t_customer / t_voucher* 等 RLS 表，
    //   无事务 ⇒ GUC 恒 NULL ⇒ INSERT 被 WITH CHECK 拒（与销售侧同型 500）。
    //   行级容错由 service 内 rowTx（REQUIRES_NEW）保证，不靠"无事务"。
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> confirmImport(@RequestParam String batchId) {
        return R.ok(importService.confirmImport(batchId));
    }

    @Operation(summary = "一步式导入 (兼容旧调用, 内部走 preview + confirm)")
    @PostMapping("/import")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> importInvoices(@RequestParam("file") MultipartFile file) {
        return R.ok(importService.importInvoices(file));
    }
}
