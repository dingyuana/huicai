package com.huicai.sme.tax.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.base.business.dto.BusinessDocQueryDTO;
import com.huicai.base.business.dto.BusinessDocVO;
import com.huicai.base.business.service.BusinessDocService;
import com.huicai.sme.tax.service.impl.SalesInvoiceImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "销售发票导入")
@RestController
@RequestMapping("/api/sme/tax/v1/sales-invoices")
@RequiredArgsConstructor
public class SalesInvoiceController {

    private final SalesInvoiceImportService importService;
    private final BusinessDocService businessDocService;

    @Operation(summary = "第一步: 上传销售发票Excel, 解析为预览数据, 不写入数据库")
    @PostMapping("/preview")
    // 🔴 SPEC-P153：preview 触库（findExisting 查重 SELECT）。无事务 ⇒ TenantGucSetter
    //   提前 return ⇒ GUC 恒 NULL ⇒ RLS 谓词滤掉全部行 ⇒ existing 恒 0，
    //   「发票号已存在」预览提示永不出现（查重静默失效）。纯只读。
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public R<Map<String, Object>> previewInvoices(@RequestParam("file") MultipartFile file) {
        return R.ok(importService.previewInvoices(file));
    }

    @Operation(summary = "第二步: 用户确认预览后, 真正写入数据库 + 生成单据 + 生成凭证")
    @PostMapping("/confirm-import")
    // 🔴 SPEC-P153（2026-11-05 王会计导入发票 500 的另一半根因）：
    //   confirmImport 全程无事务 ⇒ ①ensureStandardSubjects 的 SELECT 被 RLS
    //   滤成 0 行 ⇒ 明明已有 1122/6001/2221 仍重重建；②INSERT 时 GUC 为 NULL
    //   ⇒ WITH CHECK 恒假 ⇒ "new row violates row-level security policy"。
    //   事务挂 Controller 是 P128 §7.1 / A-2 两次实测的唯一有效位。
    //   行级 catch 的容错语义保留：循环体全部是 mapper 直调，无内层事务代理，
    //   捕获单行异常不会把外层事务标成 rollback-only（P137 机制不适用本路径）。
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
    // SPEC-P153 同族：直调 ImportService 的 mapper 读写，无事务 ⇒ GUC 恒 NULL。
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> batchLinkRedFlush() {
        return R.ok(importService.batchLinkRedFlushInvoices());
    }
}