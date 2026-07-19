package com.huicai.sme.cash.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.cash.entity.CashJournalEntity;
import com.huicai.sme.cash.service.CashJournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "现金日记账")
@RestController
@RequestMapping("/api/sme/cash/v1/cash-journals")
@RequiredArgsConstructor
public class CashJournalController {

    private final CashJournalService cashJournalService;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<CashJournalEntity>> page(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(cashJournalService.pageQuery(period, startDate, endDate, current, size));
    }

    @Operation(summary = "查询详情")
    @GetMapping("/{id}")
    public R<CashJournalEntity> get(@PathVariable Long id) {
        return R.ok(cashJournalService.getById(id));
    }

    @Operation(summary = "新增")
    @PostMapping
    public R<CashJournalEntity> create(@RequestBody CashJournalEntity entity, Authentication auth) {
        String username = auth.getName();
        return R.ok(cashJournalService.create(entity, 0L));
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    public R<CashJournalEntity> update(@PathVariable Long id, @RequestBody CashJournalEntity entity) {
        return R.ok(cashJournalService.update(id, entity));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        cashJournalService.delete(id);
        return R.ok();
    }

    @Operation(summary = "生成凭证")
    @PostMapping("/{id}/generate-voucher")
    public R<Long> generateVoucher(@PathVariable Long id, Authentication auth) {
        String username = auth.getName();
        return R.ok(cashJournalService.generateVoucher(id, 0L));
    }
}