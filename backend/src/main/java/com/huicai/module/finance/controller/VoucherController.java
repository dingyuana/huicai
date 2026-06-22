package com.huicai.module.finance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.finance.dto.VoucherCreateDTO;
import com.huicai.module.finance.dto.VoucherQueryDTO;
import com.huicai.module.finance.dto.VoucherStatusDTO;
import com.huicai.module.finance.dto.VoucherVO;
import com.huicai.module.finance.service.VoucherService;
import com.huicai.module.system.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "凭证管理")
@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @Operation(summary = "分页查询凭证")
    @PostMapping("/page")
    public R<IPage<VoucherVO>> page(@RequestBody VoucherQueryDTO queryDTO) {
        return R.ok(voucherService.pageQuery(queryDTO));
    }

    @Operation(summary = "获取凭证详情")
    @GetMapping("/{id}")
    public R<VoucherVO> getById(@PathVariable Long id) {
        return R.ok(voucherService.getDetail(id));
    }

    @Operation(summary = "创建凭证")
    @PostMapping
    public R<VoucherVO> create(@Valid @RequestBody VoucherCreateDTO dto) {
        return R.ok(voucherService.create(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "修改凭证")
    @PutMapping("/{id}")
    public R<VoucherVO> update(@PathVariable Long id, @Valid @RequestBody VoucherCreateDTO dto) {
        dto.setId(id);
        return R.ok(voucherService.update(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "删除凭证")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        voucherService.delete(id);
        return R.ok();
    }

    @Operation(summary = "提交凭证")
    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        voucherService.submit(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "批量提交")
    @PostMapping("/batch-submit")
    public R<Void> batchSubmit(@Valid @RequestBody VoucherStatusDTO dto) {
        voucherService.batchSubmit(dto.getIds(), SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "审核凭证")
    @PostMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        voucherService.audit(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "批量审核")
    @PostMapping("/batch-audit")
    public R<Void> batchAudit(@Valid @RequestBody VoucherStatusDTO dto) {
        voucherService.batchAudit(dto.getIds(), SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "记账")
    @PostMapping("/{id}/post")
    public R<Void> post(@PathVariable Long id) {
        voucherService.post(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "批量记账")
    @PostMapping("/batch-post")
    public R<Void> batchPost(@Valid @RequestBody VoucherStatusDTO dto) {
        voucherService.batchPost(dto.getIds(), SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "红冲凭证")
    @PostMapping("/{id}/reverse")
    public R<VoucherVO> reverse(@PathVariable Long id) {
        return R.ok(voucherService.reverse(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "驳回凭证 (SUBMITTED → DRAFT, reason必填)")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestParam String reason) {
        voucherService.reject(id, SecurityUtils.getCurrentUserId(), reason);
        return R.ok();
    }

    @Operation(summary = "反过账 (POSTED → AUDITED, 仅纠错)")
    @PostMapping("/{id}/unpost")
    public R<Void> unpost(@PathVariable Long id) {
        voucherService.unpost(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }
}
