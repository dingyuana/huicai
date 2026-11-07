package com.huicai.sme.tax.controller;

import com.huicai.sme.tax.dto.InputInvoiceCreateDTO;
import com.huicai.sme.tax.dto.TaxDeclarationCreateDTO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.sme.tax.dto.BatchOperationResult;
import com.huicai.sme.tax.dto.OutputInvoiceCreateDTO;
import com.huicai.sme.tax.dto.vo.AppendixIResponse;
import com.huicai.sme.tax.dto.vo.AppendixIIResponse;
import com.huicai.sme.tax.dto.vo.TaxBurdenVO;
import com.huicai.sme.tax.dto.OutputInvoiceBatchDTO;
import com.huicai.sme.tax.dto.TaxTypeSaveDTO;
import com.huicai.sme.tax.dto.vo.TaxTypeVO;
import com.huicai.sme.tax.dto.vo.InputInvoiceVO;
import com.huicai.sme.tax.dto.vo.OutputInvoiceVO;
import com.huicai.sme.tax.dto.vo.TaxDeclarationVO;
import com.huicai.sme.tax.entity.TaxDeclarationEntity;
import com.huicai.sme.tax.entity.TaxTypeEntity;
import com.huicai.sme.tax.service.InputInvoiceStateMachineService;
import com.huicai.base.business.service.OutputInvoiceStateMachineService;
import com.huicai.sme.tax.service.TaxService;
import com.huicai.base.system.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "税务管理")
@RestController
@RequestMapping("/api/sme/tax/v1/tax")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService service;
    private final OutputInvoiceStateMachineService stateMachineService;
    private final InputInvoiceStateMachineService inputStateMachineService;

    // ========== 税种管理 ==========
    @Operation(summary = "税种分页查询")
    @GetMapping("/types/page")
    public R<IPage<TaxTypeVO>> pageQueryTaxType(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        IPage<TaxTypeEntity> tp = service.pageQueryTaxType(keyword, current, size);
        return R.ok(tp == null ? null : tp.convert(TaxTypeVO::from));
    }

    @Operation(summary = "查询全部税种")
    @GetMapping("/types/list")
    public R<List<TaxTypeVO>> listTaxTypes() {
        List<TaxTypeEntity> tts = service.listAllTaxTypes();
        return R.ok(tts == null ? null : tts.stream().map(TaxTypeVO::from).collect(java.util.stream.Collectors.toList()));
    }

    @Operation(summary = "创建税种")
    @PostMapping("/types")
    public R<TaxTypeVO> createTaxType(@Valid @RequestBody TaxTypeSaveDTO dto) {
        TaxTypeEntity tte = service.createTaxType(dto.toEntity());
        return R.ok(tte == null ? null : TaxTypeVO.from(tte));
    }

    @Operation(summary = "更新税种")
    @PutMapping("/types/{id}")
    public R<TaxTypeVO> updateTaxType(@PathVariable Long id, @Valid @RequestBody TaxTypeSaveDTO dto) {
        TaxTypeEntity entity = dto.toEntity();
        entity.setId(id);
        TaxTypeEntity ute = service.updateTaxType(entity);
        return R.ok(ute == null ? null : TaxTypeVO.from(ute));
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
    public R<IPage<InputInvoiceVO>> pageInput(
            @RequestParam(required = false) String vendorName,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String certStatus,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        IPage<InputInvoiceEntity> ip = service.pageQueryInput(vendorName, period, certStatus, scope, startDate, endDate, current, size);
        return R.ok(ip == null ? null : ip.convert(InputInvoiceVO::from));
    }

    @Operation(summary = "创建进项发票")
    @PostMapping("/input-invoices")
    public R<InputInvoiceVO> createInput(@Valid @RequestBody InputInvoiceCreateDTO dto) {
        InputInvoiceEntity iie = service.createInput(dto.toEntity());
        return R.ok(iie == null ? null : InputInvoiceVO.from(iie));
    }

    @Operation(summary = "认证进项发票")
    @PostMapping("/input-invoices/{id}/certify")
    public R<InputInvoiceVO> certify(@PathVariable Long id,
                                          @RequestParam(required = false) String deductionPeriod) {
        InputInvoiceEntity ice = service.certify(id, deductionPeriod);
        return R.ok(ice == null ? null : InputInvoiceVO.from(ice));
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

    // ========== 进项发票状态机 (P40) ==========
    @Operation(summary = "进项-提交审核 (PENDING_CONFIRM -> PENDING_REVIEW)")
    @PostMapping("/input-invoices/{id}/submit-review")
    public R<Void> submitInputReview(@PathVariable Long id,
            @RequestParam(required = false) Long userId) {
        inputStateMachineService.submitForReview(id, orDefault(userId));
        return R.ok();
    }

    @Operation(summary = "进项-审核通过 (PENDING_REVIEW -> CONFIRMED -> 自动创建应付单+凭证)")
    @PostMapping("/input-invoices/{id}/confirm")
    public R<Void> confirmInput(@PathVariable Long id,
            @RequestParam(required = false) Long userId) {
        inputStateMachineService.confirm(id, orDefault(userId));
        return R.ok();
    }

    @Operation(summary = "进项-审核驳回 (PENDING_REVIEW -> PENDING_CONFIRM)")
    @PostMapping("/input-invoices/{id}/reject")
    public R<Void> rejectInput(@PathVariable Long id,
            @RequestParam String reason,
            @RequestParam(required = false) Long userId) {
        inputStateMachineService.reject(id, orDefault(userId), reason);
        return R.ok();
    }

    @Operation(summary = "进项-回退到待审核 (CONFIRMED -> PENDING_REVIEW)")
    @PostMapping("/input-invoices/{id}/revert")
    public R<Void> revertInput(@PathVariable Long id,
            @RequestParam(required = false) Long userId) {
        inputStateMachineService.revertToReview(id, orDefault(userId));
        return R.ok();
    }

    @Operation(summary = "进项-作废 (任意非终态 -> VOIDED)")
    @PostMapping("/input-invoices/{id}/void")
    public R<Void> voidInput(@PathVariable Long id,
            @RequestParam String reason,
            @RequestParam(required = false) Long userId) {
        inputStateMachineService.voidInvoice(id, orDefault(userId), reason);
        return R.ok();
    }

    @Operation(summary = "进项-红冲 (CONFIRMED/VOUCHERED/PARTIALLY_RECONCILED -> REVERSED, 生成红字发票)")
    @PostMapping("/input-invoices/{id}/reverse")
    public R<Long> reverseInput(@PathVariable Long id,
            @RequestParam String reason,
            @RequestParam(required = false) Long userId) {
        Long redInvoiceId = inputStateMachineService.reverseInvoice(id, orDefault(userId), reason);
        return R.ok(redInvoiceId);
    }

    // ========== 销项发票 ==========
    @Operation(summary = "销项发票分页")
    @GetMapping("/output-invoices/page")
    public R<IPage<OutputInvoiceVO>> pageOutput(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String invoiceType,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        IPage<OutputInvoiceEntity> op = service.pageQueryOutput(customerName, period, status, invoiceType, scope, startDate, endDate, current, size);
        return R.ok(op == null ? null : op.convert(OutputInvoiceVO::from));
    }

    @Operation(summary = "销项发票详情")
    @GetMapping("/output-invoices/{id}")
    public R<OutputInvoiceVO> getOutput(@PathVariable Long id) {
        OutputInvoiceEntity oge = service.getOutputById(id);
        return R.ok(oge == null ? null : OutputInvoiceVO.from(oge));
    }

    @Operation(summary = "创建销项发票（入参 DTO，不接受 status —— 人工审核链不可跳过）")
    @PostMapping("/output-invoices")
    public R<OutputInvoiceVO> createOutput(@Valid @RequestBody OutputInvoiceCreateDTO dto) {
        OutputInvoiceEntity oce = service.createOutput(dto);
        return R.ok(oce == null ? null : OutputInvoiceVO.from(oce));
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
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String invoiceType,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (customerName == null && status == null && invoiceType == null && scope == null && startDate == null && endDate == null) {
            if (period != null) return R.ok(service.outputSummary(period));
            return R.ok(service.outputSummaryAll());
        }
        return R.ok(service.outputSummaryFilter(customerName, period, status, invoiceType, scope, startDate, endDate));
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

    @Operation(summary = "审核通过 (PENDING_REVIEW → CONFIRMED → P31 自动创建应收单+凭证)")
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

    @Operation(summary = "生成凭证 (CONFIRMED → VOUCHERED, 自动创建凭证分录)")
    @PostMapping("/output-invoices/{id}/mark-vouchered")
    public R<Void> markVouchered(@PathVariable Long id,
            @RequestParam(required = false) Long userId) {
        service.generateVoucherFromInvoice(id, orDefault(userId));
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

    @Operation(summary = "红冲 (CONFIRMED/VOUCHERED/PARTIALLY_RECONCILED → 生成红字发票)")
    @PostMapping("/output-invoices/{id}/reverse")
    public R<Void> reverseInvoice(@PathVariable Long id,
            @RequestParam String reason,
            @RequestParam(required = false) Long userId) {
        stateMachineService.reverseInvoice(id, orDefault(userId), reason);
        return R.ok();
    }

    private Long orDefault(Long userId) {
        if (userId != null) return userId;
        try { return SecurityUtils.getCurrentUserId(); } catch (Exception e) { return 1L; }
    }

    // ========== 增值税计算 + P61 附表 =========
    @Operation(summary = "计算增值税")
    @GetMapping("/vat/calculate")
    public R<Map<String, Object>> calculateVat(@RequestParam String period) {
        return R.ok(service.calculateVat(period));
    }

    @Operation(summary = "P61 附表一（销项，按客户+税率聚合）")
    @GetMapping("/vat/appendix-i")
    public R<AppendixIResponse> appendixI(
            @RequestParam String period,
            @RequestParam(required = false) Long customerId) {
        return R.ok(service.appendixI(period, customerId));
    }

    @Operation(summary = "P61 附表二（进项，按供应商+税率+认证申报态聚合）")
    @GetMapping("/vat/appendix-ii")
    public R<AppendixIIResponse> appendixII(
            @RequestParam String period,
            @RequestParam(required = false) Long vendorId) {
        return R.ok(service.appendixII(period, vendorId));
    }

    @Operation(summary = "P61 税负率分析（type=CURR/YOY）")
    @GetMapping("/vat/tax-burden")
    public R<TaxBurdenVO> taxBurden(
            @RequestParam String period,
            @RequestParam(defaultValue = "CURR") String type) {
        return R.ok(service.taxBurden(period, type));
    }

    // ========== 申报 ==========
    @Operation(summary = "申报分页")
    @GetMapping("/declarations/page")
    public R<IPage<TaxDeclarationVO>> pageDeclaration(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        IPage<TaxDeclarationEntity> dp = service.pageQueryDeclaration(status, current, size);
        return R.ok(dp == null ? null : dp.convert(TaxDeclarationVO::from));
    }

    @Operation(summary = "创建申报")
    @PostMapping("/declarations")
    public R<TaxDeclarationVO> createDeclaration(@Valid @RequestBody TaxDeclarationCreateDTO dto) {
        TaxDeclarationEntity dce = service.createDeclaration(dto);
        return R.ok(dce == null ? null : TaxDeclarationVO.from(dce));
    }

    @Operation(summary = "提交申报")
    @PostMapping("/declarations/{id}/submit")
    public R<TaxDeclarationVO> submitDeclaration(@PathVariable Long id) {
        TaxDeclarationEntity dse = service.submitDeclaration(id);
        return R.ok(dse == null ? null : TaxDeclarationVO.from(dse));
    }

    @Operation(summary = "P18-1 申报审批通过 (SUBMITTED → APPROVED)")
    @PostMapping("/declarations/{id}/approve")
    public R<TaxDeclarationVO> approveDeclaration(
            @PathVariable Long id,
            @RequestParam(required = false) String approver) {
        TaxDeclarationEntity dae = service.approveDeclaration(id, approver);
        return R.ok(dae == null ? null : TaxDeclarationVO.from(dae));
    }

    @Operation(summary = "P18-1 申报驳回 (SUBMITTED → REJECTED, reason 必填)")
    @PostMapping("/declarations/{id}/reject")
    public R<TaxDeclarationVO> rejectDeclaration(
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestParam(required = false) String approver) {
        TaxDeclarationEntity dre = service.rejectDeclaration(id, approver, reason);
        return R.ok(dre == null ? null : TaxDeclarationVO.from(dre));
    }

    @Operation(summary = "批量合并生成凭证（多张销项发票→一张凭证）")
    @PostMapping("/invoice/batch/generate-voucher")
    public R<Map<String, Object>> batchGenerateVoucher(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> idsRaw = (List<Integer>) body.get("invoice_ids");
        if (idsRaw == null || idsRaw.isEmpty()) return R.fail("invoice_ids 不能为空");
        List<Long> invoiceIds = idsRaw.stream().map(Integer::longValue).collect(java.util.stream.Collectors.toList());
        Boolean sameCustomer = (Boolean) body.getOrDefault("same_customer", true);
        String voucherNo = service.batchGenerateVoucherFromInvoices(invoiceIds, SecurityUtils.getCurrentUserId(), sameCustomer);
        return R.ok(java.util.Map.of("voucher_no", voucherNo));
    }

    // ========== 销项发票批量操作 (P56) ==========
    @Operation(summary = "批量提交审核 (PENDING_CONFIRM → PENDING_REVIEW)")
    @PostMapping("/output-invoices/batch/submit-review")
    public R<BatchOperationResult> batchSubmitReview(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
        return R.ok(service.batchSubmitForReview(dto.getIds(), SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "批量审核通过 (PENDING_REVIEW → CONFIRMED + 业务单+凭证)")
    @PostMapping("/output-invoices/batch/confirm")
    public R<BatchOperationResult> batchConfirm(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
        return R.ok(service.batchConfirm(dto.getIds(), SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "批量驳回 (PENDING_REVIEW → PENDING_CONFIRM)")
    @PostMapping("/output-invoices/batch/reject")
    public R<BatchOperationResult> batchReject(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
        return R.ok(service.batchReject(dto.getIds(), SecurityUtils.getCurrentUserId(), dto.getReason()));
    }

    @Operation(summary = "批量回退 (CONFIRMED → PENDING_REVIEW)")
    @PostMapping("/output-invoices/batch/revert")
    public R<BatchOperationResult> batchRevert(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
        return R.ok(service.batchRevert(dto.getIds(), SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "批量生成凭证 (CONFIRMED → VOUCHERED)")
    @PostMapping("/output-invoices/batch/mark-vouchered")
    public R<BatchOperationResult> batchMarkVouchered(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
        return R.ok(service.batchMarkVouchered(dto.getIds(), SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "批量作废 (任意非终态 → VOIDED)")
    @PostMapping("/output-invoices/batch/void")
    public R<BatchOperationResult> batchVoid(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
        return R.ok(service.batchVoid(dto.getIds(), SecurityUtils.getCurrentUserId(), dto.getReason()));
    }

    @Operation(summary = "批量红冲 (CONFIRMED/VOUCHERED/PARTIALLY_RECONCILED → REVERSED + 红字发票)")
    @PostMapping("/output-invoices/batch/reverse")
    public R<BatchOperationResult> batchReverse(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
        return R.ok(service.batchReverse(dto.getIds(), SecurityUtils.getCurrentUserId(), dto.getReason()));
    }
}
