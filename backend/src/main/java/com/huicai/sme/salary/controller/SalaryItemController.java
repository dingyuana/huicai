package com.huicai.sme.salary.controller;

import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.response.R;
import com.huicai.sme.salary.dto.SalaryItemSaveDTO;
import com.huicai.sme.salary.service.SalaryItemService;
import com.huicai.sme.salary.vo.SalaryItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SPEC-P143：薪资项目配置端点（读 salary:list / 写 salary:save）。 */
@Tag(name = "薪资项目")
@RestController
@RequestMapping("/api/sme/salary/v1/salary-items")
@RequiredArgsConstructor
public class SalaryItemController {

    private final SalaryItemService salaryItemService;

    @Operation(summary = "薪资项目列表")
    @PreAuthorize("hasAuthority('salary:list')")
    @GetMapping
    public R<List<SalaryItemVO>> list() {
        return R.ok(salaryItemService.list());
    }

    @Operation(summary = "新建薪资项目")
    @PreAuthorize("hasAuthority('salary:save')")
    @PostMapping
    public R<Long> create(@Valid @RequestBody SalaryItemSaveDTO dto) {
        return R.ok(salaryItemService.create(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "修改薪资项目")
    @PreAuthorize("hasAuthority('salary:save')")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SalaryItemSaveDTO dto) {
        salaryItemService.update(id, dto, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "删除薪资项目（软删）")
    @PreAuthorize("hasAuthority('salary:save')")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        salaryItemService.delete(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }
}
