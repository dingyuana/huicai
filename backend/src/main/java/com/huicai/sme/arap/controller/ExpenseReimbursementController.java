package com.huicai.sme.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.arap.dto.ExpenseReimbursementVO;
import com.huicai.sme.arap.service.ExpenseReimbursementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.huicai.sme.arap.entity.ExpenseReimbursementEntity;
import java.util.List;

@Tag(name = "费用报销单 - P11-2")
@RestController
@RequestMapping("/api/sme/arap/v1/expense-reimbursements")
@RequiredArgsConstructor
public class ExpenseReimbursementController {

    private final ExpenseReimbursementService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<ExpenseReimbursementVO>> page(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(employeeId, status, current, size));
    }

    @Operation(summary = "查询全部")
    @GetMapping("/list")
    public R<List<ExpenseReimbursementVO>> list() {
        return R.ok(service.listAll());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<ExpenseReimbursementVO> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "创建草稿")
    @PostMapping
    public R<ExpenseReimbursementVO> create(@RequestBody ExpenseReimbursementEntity entity) {
        return R.ok(service.createDraft(entity));
    }

    @Operation(summary = "修改草稿")
    @PutMapping("/{id}")
    public R<ExpenseReimbursementVO> update(@PathVariable Long id, @RequestBody ExpenseReimbursementEntity entity) {
        entity.setId(id);
        return R.ok(service.updateDraft(entity));
    }

    @Operation(summary = "提交审核")
    @PostMapping("/{id}/submit")
    public R<ExpenseReimbursementVO> submit(@PathVariable Long id) {
        return R.ok(service.submit(id));
    }

    @Operation(summary = "审核通过")
    @PostMapping("/{id}/approve")
    public R<ExpenseReimbursementVO> approve(@PathVariable Long id, @RequestParam(required = false) String approver) {
        return R.ok(service.approve(id, approver));
    }

    @Operation(summary = "驳回")
    @PostMapping("/{id}/reject")
    public R<ExpenseReimbursementVO> reject(@PathVariable Long id,
                                             @RequestParam String reason,
                                             @RequestParam(required = false) String approver) {
        return R.ok(service.reject(id, approver, reason));
    }

    @Operation(summary = "生成凭证 (APPROVED → VOUCHERED)")
    @PostMapping("/{id}/generate-voucher")
    public R<ExpenseReimbursementVO> generateVoucher(@PathVariable Long id, @RequestParam Long voucherId) {
        return R.ok(service.generateVoucher(id, voucherId));
    }

    @Operation(summary = "P11-4: 报销单审批后自动生成真实凭证 (按 expenseType 匹配科目)")
    @PostMapping("/{id}/auto-voucher")
    public R<ExpenseReimbursementVO> autoVoucher(@PathVariable Long id) {
        return R.ok(service.generateVoucherForApproved(id));
    }
}
