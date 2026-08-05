package com.huicai.sme.cash.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.sme.cash.service.BankStatementService;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.sme.arap.service.impl.AutoGenerationService;
import com.huicai.sme.cash.service.impl.BankStatementExcelImportService;
import com.huicai.base.business.util.ColumnMappingResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "银行对账单")
@RestController
@RequestMapping("/api/sme/cash/v1/bank-statements")
@RequiredArgsConstructor
public class BankStatementController {

    private final BankStatementService service;
    private final BankStatementExcelImportService excelImportService;
    private final AutoGenerationService autoGenerationService;

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(BankStatementController.class);

    @Operation(summary = "分页查询对账单")
    @GetMapping("/page")
    public R<IPage<BankStatementEntity>> page(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(accountId, status, classification, reviewStatus, current, size));
    }

    @Operation(summary = "获取对账单详情")
    @GetMapping("/{id}")
    public R<BankStatementEntity> detail(@PathVariable Long id) {
        return R.ok(service.getDetail(id));
    }

    @Operation(summary = "导入CSV对账单")
    @PostMapping("/import-csv")
    public R<Integer> importCsv(@RequestParam Long accountId, @RequestBody String csvContent) {
        return R.ok(service.importFromCsv(accountId, csvContent));
    }

    /** 银行流水导入使用的系统字段. 排除销售发票字段 (发票号码/销方/购方/商品/税率/税额/合计/正负标志). */
    private static final java.util.Set<ColumnMappingResolver.Field> BANK_STATEMENT_FIELDS = java.util.EnumSet.of(
            ColumnMappingResolver.Field.TX_DATE,
            ColumnMappingResolver.Field.TX_TYPE,
            ColumnMappingResolver.Field.AMOUNT,
            ColumnMappingResolver.Field.COUNTER_ACCOUNT,
            ColumnMappingResolver.Field.SUMMARY,
            ColumnMappingResolver.Field.EXTERNAL_NO,
            ColumnMappingResolver.Field.PAYER_NAME,
            ColumnMappingResolver.Field.PAYEE_NAME
    );

    @Operation(summary = "解析Excel表头, 返回所有列名和系统字段列表")
    @PostMapping("/parse-headers")
    public R<Map<String, Object>> parseHeaders(@RequestParam("file") MultipartFile file) {
        List<String> headers = excelImportService.parseHeaders(file);
        List<Map<String, Object>> systemFields = BANK_STATEMENT_FIELDS.stream()
                .map(f -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("field", f.name());
                    m.put("label", f.getAliases()[0]);
                    m.put("required", f == ColumnMappingResolver.Field.TX_DATE || f == ColumnMappingResolver.Field.AMOUNT);
                    return m;
                })
                .collect(Collectors.toList());
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("headers", headers);
        result.put("fields", systemFields);
        return R.ok(result);
    }

    @Operation(summary = "Excel导入第一步: 上传预览, 不写入数据库")
    @PostMapping("/preview-excel")
    public R<Map<String, Object>> previewExcel(
            @RequestParam Long accountId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String columnMappingJson) {
        if (StrUtil.isNotBlank(columnMappingJson)) {
            cn.hutool.json.JSONObject mappingObj = new cn.hutool.json.JSONObject(columnMappingJson);
            Map<String, String> columnMapping = new java.util.HashMap<>();
            for (var entry : mappingObj.entrySet()) {
                columnMapping.put(entry.getKey(), entry.getValue().toString());
            }
            return R.ok(excelImportService.previewExcel(accountId, file, columnMapping));
        }
        return R.ok(excelImportService.previewExcel(accountId, file));
    }

    @Operation(summary = "Excel导入第二步: 用户确认预览后, 真正写入数据库")
    @PostMapping("/confirm-import")
    public R<Map<String, Object>> confirmImport(@RequestParam String batchId) {
        return R.ok(excelImportService.confirmImport(batchId));
    }

    @Operation(summary = "Excel导入直接模式 (兼容旧调用, 等于preview+confirm)")
    @PostMapping("/import-excel")
    public R<Map<String, Object>> importExcel(
            @RequestParam Long accountId,
            @RequestParam("file") MultipartFile file) {
        var preview = excelImportService.previewExcel(accountId, file);
        String batchId = (String) preview.get("batchId");
        var confirm = excelImportService.confirmImport(batchId);
        confirm.put("total", preview.get("total"));
        return R.ok(confirm);
    }

    @Operation(summary = "智能匹配建议")
    @GetMapping("/auto-match")
    public R<List<Map<String, Object>>> autoMatch(@RequestParam Long accountId) {
        return R.ok(service.autoMatch(accountId));
    }

    @Operation(summary = "按分类统计当前账户的流水数量")
    @GetMapping("/classification-counts")
    public R<Map<String, Integer>> classificationCounts(@RequestParam Long accountId,
                                                         @RequestParam(required = false) String reviewStatus) {
        return R.ok(service.classificationCounts(accountId, reviewStatus));
    }

    @Operation(summary = "确认匹配")
    @PostMapping("/{statementId}/confirm-match")
    public R<Integer> confirmMatch(@PathVariable Long statementId, @RequestParam Long journalId) {
        return R.ok(service.confirmMatch(statementId, journalId));
    }

    @Operation(summary = "忽略对账单")
    @PostMapping("/{statementId}/ignore")
    public R<Integer> ignore(@PathVariable Long statementId) {
        return R.ok(service.ignoreStatement(statementId));
    }

    @Operation(summary = "手动触发单条分类")
    @PostMapping("/{id}/classify")
    public R<BankStatementEntity> classify(@PathVariable Long id) {
        return R.ok(service.classifySingle(id));
    }

    @Operation(summary = "出纳单条审核确认（仅改状态，不生成凭证）")
    @PostMapping("/{id}/review")
    public R<BankStatementEntity> review(@PathVariable Long id) {
        return R.ok(service.review(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "批量审核确认")
    @PostMapping("/batch-review")
    public R<BankStatementService.BatchResult> batchReview(@RequestBody List<Long> ids) {
        var result = service.batchReview(ids, SecurityUtils.getCurrentUserId());
        logger.info("批量审核确认: total={}, success={}, failed={}", result.total(), result.success(), result.failed().size());
        return R.ok(result);
    }

    @Operation(summary = "主管审核（CONFIRMED → AUDITED）")
    @PostMapping("/{id}/audit")
    public R<BankStatementEntity> audit(@PathVariable Long id) {
        return R.ok(service.audit(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "批量主管审核")
    @PostMapping("/batch-audit")
    public R<BankStatementService.BatchResult> batchAudit(@RequestBody List<Long> ids) {
        var result = service.batchAudit(ids, SecurityUtils.getCurrentUserId());
        logger.info("批量审核: total={}, success={}, failed={}", result.total(), result.success(), result.failed().size());
        return R.ok(result);
    }

    @Operation(summary = "审核通过后生成凭证（仅允许 AUDITED 状态执行）")
    @PostMapping("/{id}/generate")
    public R<BankStatementEntity> generate(@PathVariable Long id) {
        return R.ok(service.generateVoucher(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "批量生成凭证")
    @PostMapping("/batch-generate")
    public R<BankStatementService.BatchResult> batchGenerate(@RequestBody List<Long> ids) {
        var result = service.batchGenerateVouchers(ids, SecurityUtils.getCurrentUserId());
        logger.info("批量制证: total={}, success={}, failed={}", result.total(), result.success(), result.failed().size());
        return R.ok(result);
    }

    @Operation(summary = "核准过账 (仅 voucher_generated/payment_created → approved)")
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id) {
        service.approve(id);
        return R.ok();
    }

    @Operation(summary = "删除单条对账单")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.deleteStatement(id);
        return R.ok();
    }

    @Operation(summary = "手动修改流水分类")
    @PutMapping("/{id}/classification")
    public R<BankStatementEntity> updateClassification(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String classification = body.get("classification");
        return R.ok(service.updateClassification(id, classification));
    }

    @Operation(summary = "P2: C类人工处理 - 指定 A/B 类型后自动生成")
    @PostMapping("/{id}/process-manual")
    public R<BankStatementEntity> processManual(@PathVariable Long id,
                                                 @RequestParam String targetType,
                                                 @RequestParam(required = false) String paymentType) {
        return R.ok(service.processManual(id, targetType, paymentType, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "P2: 预览凭证草稿 (只计算不写入)")
    @GetMapping("/{id}/preview-draft")
    public R<List<BankStatementService.PreviewEntry>> previewDraft(@PathVariable Long id) {
        return R.ok(service.previewDraft(id));
    }
}