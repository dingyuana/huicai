package com.huicai.sme.cash.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.cash.entity.BankJournalEntity;
import com.huicai.sme.cash.service.BankJournalService;
import com.huicai.base.system.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "银行日记账")
@RestController
@RequestMapping("/api/sme/cash/v1/bank-journals")
@RequiredArgsConstructor
public class BankJournalController {

    private final BankJournalService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<BankJournalEntity>> page(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String txType,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(accountId, period, txType, current, size));
    }

    @Operation(summary = "新增日记账")
    @PostMapping
    public R<BankJournalEntity> create(@RequestBody BankJournalEntity entity) {
        return R.ok(service.create(entity, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "修改日记账")
    @PutMapping("/{id}")
    public R<BankJournalEntity> update(@PathVariable Long id, @RequestBody BankJournalEntity entity) {
        return R.ok(service.update(id, entity));
    }

    @Operation(summary = "删除日记账")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "生成凭证")
    @PostMapping("/{id}/generate-voucher")
    public R<Long> generateVoucher(@PathVariable Long id) {
        return R.ok(service.generateVoucher(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "按期间汇总")
    @GetMapping("/aggregate")
    public R<List<Map<String, Object>>> aggregate(@RequestParam Long accountId, @RequestParam String period) {
        return R.ok(service.aggregate(accountId, period));
    }

    @Operation(summary = "账户当前余额")
    @GetMapping("/balance")
    public R<BigDecimal> balance(@RequestParam Long accountId) {
        return R.ok(service.getAccountBalance(accountId));
    }
}
