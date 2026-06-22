package com.huicai.module.tax.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.tax.entity.InputInvoiceEntity;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.entity.TaxDeclarationEntity;
import com.huicai.module.tax.entity.TaxTypeEntity;
import com.huicai.module.tax.service.OutputInvoiceStateMachineService;
import com.huicai.module.tax.service.TaxService;
import com.huicai.module.system.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "税务管理")
@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService service;
    private final OutputInvoiceStateMachineService stateMachineService;

    // ========== 税种管理 ==========
    @Operation(summary = "税种分页查询")
    @GetMapping("/types/page")
    public R<IPage<TaxTypeEntity>> pageQueryTaxType(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQueryTaxType(keyword, current, size));
    }

    @Operation(summary = "查询全部税种")
    @GetMapping("/types/list")
    public R<List<TaxTypeEntity>> listTaxTypes() {
        return R.ok(service.listAllTaxTypes());
    }

    @Operation(summary = "创建税种")
    @PostMapping("/types")
    public R<TaxTypeEntity> createTaxType(@RequestBody TaxTypeEntity entity) {
        return R.ok(service.createTaxType(entity));
    }

    @Operation(summary = "更新税种")
    @PutMapping("/types/{id}")
    public R<TaxTypeEntity> updateTaxType(@PathVariable Long id, @RequestBody TaxTypeEntity entity) {
        entity.setId(id);
        return R.ok(service.updateTaxType(entity));
    }

    @Operation(summary = "删除税种")
    @DeleteMapping("/types/{id}")
    public R<Void> deleteTaxType(@PathVariable Long id) {
        service.deleteTaxType(id);
        return R.ok();
    }

    // ========== 进项发票 ==========
    @Operation(summary = "进项发票分页")
    @GetMapping("/input-invoices/page")
    public R<IPage<InputInvoiceEntity>> pageInput(
            @RequestParam(required = false) String vendorName,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String certStatus,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQueryInput(vendorName, period, certStatus, current, size));
    }

    @Operation(summary = "创建进项发票")
    @PostMapping("/input-invoices")
    public R<InputInvoiceEntity> createInput(@RequestBody InputInvoiceEntity entity) {
        return R.ok(service.createInput(entity));
    }

    @Operation(summary = "认证进项发票")
    @PostMapping("/input-invoices/{id}/certify")
    public R<InputInvoiceEntity> certify(@PathVariable Long id,
                                          @RequestParam(required = false) String deductionPeriod) {
        return R.ok(service.certify(id, deductionPeriod));
    }

    @Operation(summary = "进项汇总")
    @GetMapping("/input-invoices/summary")
    public R<Map<String, Object>> inputSummary(@RequestParam String period) {
        return R.ok(service.inputSummary(period));
    }

    @Operation(summary = "进项按税率分组")
    @GetMapping("/input-invoices/by-tax-rate")
    public R<List<Map<String, Object>>> inputByTaxRate(@RequestParam String period) {
        return R.ok(service.inputByTaxRate(period));
    }

    // ========== 销项发票 ==========
    @Operation(summary = "销项发票分页")
    @GetMapping("/output-invoices/page")
    public R<IPage<OutputInvoiceEntity>> pageOutput(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQueryOutput(customerName, period, status, current, size));
    }

    @Operation(summary = "销项发票详情")
    @GetMapping("/output-invoices/{id}")
    public R<OutputInvoiceEntity> getOutput(@PathVariable Long id) {
        return R.ok(service.getOutputById(id));
    }

    @Operation(summary = "创建销项发票")
    @PostMapping("/output-invoices")
    public R<OutputInvoiceEntity> createOutput(@RequestBody OutputInvoiceEntity entity) {
        return R.ok(service.createOutput(entity));
    }

    @Operation(summary = "删除销项发票（逻辑删除）")
    @DeleteMapping("/output-invoices/{id}")
    public R<Void> deleteOutput(@PathVariable Long id) {
        service.deleteOutput(id);
        return R.ok();
    }

    @Operation(summary = "销项汇总")
    @GetMapping("/output-invoices/summary")
    public R<Map<String, Object>> outputSummary(
            @RequestParam(required = false) String period) {
        if (period != null) return R.ok(service.outputSummary(period));
        return R.ok(service.outputSummaryAll());
    }

    @Operation(summary = "销项按税率分组")
    @GetMapping("/output-invoices/by-tax-rate")
    public R<List<Map<String, Object>>> outputByTaxRate(@RequestParam String period) {
        return R.ok(service.outputByTaxRate(period));
    }

    // ========== 销项发票状态机 (P21-a) ==========
    @Operation(summary = "提交审核 (PENDING_CONFIRM → PENDING_REVIEW)")
    @PostMapping("/output-invoices/{id}/submit-review")
    public R<Void> submitReview(@PathVariable Long id,
            @RequestParam(required = false) Long userId) {
        stateMachineService.submitForReview(id, orDefault(userId));
        return R.ok();
    }

    @Operation(summary = "审核通过 (PENDING_REVIEW → CONFIRMED)")
    @PostMapping("/output-invoices/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id,
            @RequestParam(required = false) Long userId) {
        stateMachineService.confirm(id, orDefault(userId));
        return R.ok();
    }

    @Operation(summary = "审核驳回 (PENDING_REVIEW → PENDING_CONFIRM)")
    @PostMapping("/output-invoices/{id}/reject")
    public R<Void> reject(@PathVariable Long id,
            @RequestParam String reason,
            @RequestParam(required = false) Long userId) {
        stateMachineService.reject(id, orDefault(userId), reason);
        return R.ok();
    }

    @Operation(summary = "回退到待审核 (CONFIRMED → PENDING_REVIEW)")
    @PostMapping("/output-invoices/{id}/revert")
    public R<Void> revert(@PathVariable Long id,
            @RequestParam(required = false) Long userId) {
        stateMachineService.revertToReview(id, orDefault(userId));
        return R.ok();
    }

    @Operation(summary = "标记已生成凭证 (CONFIRMED → VOUCHERED, 记录voucherId)")
    @PostMapping("/output-invoices/{id}/mark-vouchered")
    public R<Void> markVouchered(@PathVariable Long id,
            @RequestParam Long voucherId,
            @RequestParam(required = false) Long userId) {
        stateMachineService.markVouchered(id, voucherId, orDefault(userId));
        return R.ok();
    }

    @Operation(summary = "作废 (任意非终态 → VOIDED)")
    @PostMapping("/output-invoices/{id}/void")
    public R<Void> voidInvoice(@PathVariable Long id,
            @RequestParam String reason,
            @RequestParam(required = false) Long userId) {
        stateMachineService.voidInvoice(id, orDefault(userId), reason);
        return R.ok();
    }

    private Long orDefault(Long userId) {
        if (userId != null) return userId;
        try { return SecurityUtils.getCurrentUserId(); } catch (Exception e) { return 1L; }
    }

    // ========== 增值税计算 ==========
    @Operation(summary = "计算增值税")
    @GetMapping("/vat/calculate")
    public R<Map<String, Object>> calculateVat(@RequestParam String period) {
        return R.ok(service.calculateVat(period));
    }

    // ========== 申报 ==========
    @Operation(summary = "申报分页")
    @GetMapping("/declarations/page")
    public R<IPage<TaxDeclarationEntity>> pageDeclaration(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQueryDeclaration(status, current, size));
    }

    @Operation(summary = "创建申报")
    @PostMapping("/declarations")
    public R<TaxDeclarationEntity> createDeclaration(@RequestBody TaxDeclarationEntity entity) {
        return R.ok(service.createDeclaration(entity));
    }

    @Operation(summary = "提交申报")
    @PostMapping("/declarations/{id}/submit")
    public R<TaxDeclarationEntity> submitDeclaration(@PathVariable Long id) {
        return R.ok(service.submitDeclaration(id));
    }

    @Operation(summary = "P18-1 申报审批通过 (SUBMITTED → APPROVED)")
    @PostMapping("/declarations/{id}/approve")
    public R<TaxDeclarationEntity> approveDeclaration(
            @PathVariable Long id,
            @RequestParam(required = false) String approver) {
        return R.ok(service.approveDeclaration(id, approver));
    }

    @Operation(summary = "P18-1 申报驳回 (SUBMITTED → REJECTED, reason 必填)")
    @PostMapping("/declarations/{id}/reject")
    public R<TaxDeclarationEntity> rejectDeclaration(
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestParam(required = false) String approver) {
        return R.ok(service.rejectDeclaration(id, approver, reason));
    }
}
