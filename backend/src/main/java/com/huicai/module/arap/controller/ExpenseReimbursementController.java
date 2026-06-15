package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.arap.entity.ExpenseReimbursementEntity;
import com.huicai.module.arap.service.ExpenseReimbursementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "费用报销单 - P11-2")
@RestController
@RequestMapping("/api/v1/expense-reimbursements")
@RequiredArgsConstructor
public class ExpenseReimbursementController {

    private final ExpenseReimbursementService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<ExpenseReimbursementEntity>> page(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(employeeId, status, current, size));
    }

    @Operation(summary = "查询全部")
    @GetMapping("/list")
    public R<List<ExpenseReimbursementEntity>> list() {
        return R.ok(service.listAll());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<ExpenseReimbursementEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "创建草稿")
    @PostMapping
    public R<ExpenseReimbursementEntity> create(@RequestBody ExpenseReimbursementEntity entity) {
        return R.ok(service.createDraft(entity));
    }

    @Operation(summary = "修改草稿")
    @PutMapping("/{id}")
    public R<ExpenseReimbursementEntity> update(@PathVariable Long id, @RequestBody ExpenseReimbursementEntity entity) {
        entity.setId(id);
        return R.ok(service.updateDraft(entity));
    }

    @Operation(summary = "提交审核")
    @PostMapping("/{id}/submit")
    public R<ExpenseReimbursementEntity> submit(@PathVariable Long id) {
        return R.ok(service.submit(id));
    }

    @Operation(summary = "审核通过")
    @PostMapping("/{id}/approve")
    public R<ExpenseReimbursementEntity> approve(@PathVariable Long id, @RequestParam(required = false) String approver) {
        return R.ok(service.approve(id, approver));
    }

    @Operation(summary = "驳回")
    @PostMapping("/{id}/reject")
    public R<ExpenseReimbursementEntity> reject(@PathVariable Long id,
                                                 @RequestParam String reason,
                                                 @RequestParam(required = false) String approver) {
        return R.ok(service.reject(id, approver, reason));
    }

    @Operation(summary = "生成凭证 (APPROVED → VOUCHERED)")
    @PostMapping("/{id}/generate-voucher")
    public R<ExpenseReimbursementEntity> generateVoucher(@PathVariable Long id, @RequestParam Long voucherId) {
        return R.ok(service.generateVoucher(id, voucherId));
    }
}
