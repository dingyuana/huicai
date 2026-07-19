package com.huicai.sme.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.arap.dto.vo.ArapSettlementVO;
import com.huicai.sme.arap.entity.ArapSettlementEntity;
import com.huicai.sme.arap.entity.ArapSettlementEntryEntity;
import com.huicai.sme.arap.service.ArapSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "核销管理")
@RestController
@RequestMapping("/api/v1/arap-settlements")
@RequiredArgsConstructor
public class ArapSettlementController {

    private final ArapSettlementService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<ArapSettlementVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String voucherNo,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQueryWithPartyName(status, voucherNo, current, size));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<ArapSettlementVO> getById(@PathVariable Long id) {
        return R.ok(service.getDetailWithPartyName(id));
    }

    @Operation(summary = "创建核销单")
    @PostMapping
    public R<ArapSettlementEntity> create(@RequestBody CreateRequest request) {
        return R.ok(service.create(request.settlement, request.entries));
    }

    @Operation(summary = "提交核销单 — DRAFT → SUBMITTED")
    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        service.submit(id);
        return R.ok();
    }

    @Operation(summary = "审批通过 — SUBMITTED → CONFIRMED")
    @PostMapping("/{id}/approve")
    public R<ArapSettlementEntity> approve(@PathVariable Long id) {
        return R.ok(service.approve(id));
    }

    @Operation(summary = "驳回 — SUBMITTED → REJECTED")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@RequestParam String reason, @PathVariable Long id) {
        service.reject(id, reason);
        return R.ok();
    }

    @Operation(summary = "确认核销（兼容旧接口，DRAFT→CONFIRMED自动提审）")
    @PostMapping("/{id}/confirm")
    public R<ArapSettlementEntity> confirm(@PathVariable Long id) {
        return R.ok(service.confirm(id));
    }

    @Operation(summary = "生成凭证 — CONFIRMED → VOUCHERED")
    @PostMapping("/{id}/generate-voucher")
    public R<Void> generateVoucher(@PathVariable Long id) {
        service.generateVoucher(id);
        return R.ok();
    }

    @Operation(summary = "反核销 — CONFIRMED → REVERSED")
    @PostMapping("/{id}/reverse")
    public R<Void> reverse(@PathVariable Long id) {
        service.reverse(id);
        return R.ok();
    }

    @Operation(summary = "取消核销单 — DRAFT → CANCELLED")
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return R.ok();
    }

    @Operation(summary = "核销明细列表")
    @GetMapping("/{id}/entries")
    public R<List<ArapSettlementEntryEntity>> getEntries(@PathVariable Long id) {
        return R.ok(service.getEntries(id));
    }

    @Operation(summary = "驳回核销单（无理由）")
    @PostMapping("/{id}/reject-simple")
    public R<Void> reject(@PathVariable Long id) {
        service.reject(id);
        return R.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    public static class CreateRequest {
        public ArapSettlementEntity settlement;
        public List<ArapSettlementEntryEntity> entries;
    }
}
