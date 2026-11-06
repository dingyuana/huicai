package com.huicai.sme.salary.controller;

import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.response.R;
import com.huicai.sme.salary.dto.SalaryBillSaveDTO;
import com.huicai.sme.salary.service.SalaryBillService;
import com.huicai.sme.salary.vo.SalaryBillDetailVO;
import com.huicai.sme.salary.vo.SalaryBillVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SPEC-P143：工资单端点。
 *
 * <p>读 salary:list / 编辑 salary:save / 确认 salary:confirm / 发放 salary:pay
 * （V197 编制族授权：ACCOUNTANT/ADMIN/FINANCE_MGR）。</p>
 */
@Tag(name = "工资单")
@RestController
@RequestMapping("/api/sme/salary/v1/salary-bills")
@RequiredArgsConstructor
public class SalaryBillController {

    private final SalaryBillService salaryBillService;

    @Operation(summary = "工资单列表")
    @PreAuthorize("hasAuthority('salary:list')")
    @GetMapping
    public R<List<SalaryBillVO>> list() {
        return R.ok(salaryBillService.list());
    }

    @Operation(summary = "工资单详情（员工级明细 + 个税快照）")
    @PreAuthorize("hasAuthority('salary:list')")
    @GetMapping("/{id}")
    public R<SalaryBillDetailVO> detail(@PathVariable Long id) {
        return R.ok(salaryBillService.detail(id));
    }

    @Operation(summary = "工资单原始明细（纵表，编辑草稿回显用）")
    @PreAuthorize("hasAuthority('salary:list')")
    @GetMapping("/{id}/items")
    public R<List<com.huicai.sme.salary.vo.SalaryBillItemVO>> items(@PathVariable Long id) {
        return R.ok(salaryBillService.items(id));
    }

    @Operation(summary = "保存工资单（按期间 upsert，仅草稿可改）")
    @PreAuthorize("hasAuthority('salary:save')")
    @PostMapping
    public R<Long> save(@Valid @RequestBody SalaryBillSaveDTO dto) {
        return R.ok(salaryBillService.save(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "确认（个税累计预扣 + 计提凭证 SALC-{id}）")
    @PreAuthorize("hasAuthority('salary:confirm')")
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        salaryBillService.confirm(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "发放（发放凭证 SALP-{id}）")
    @PreAuthorize("hasAuthority('salary:pay')")
    @PostMapping("/{id}/pay")
    public R<Void> pay(@PathVariable Long id) {
        salaryBillService.pay(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }
}
