package com.huicai.sme.cash.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.cash.entity.BankJournalEntity;
import com.huicai.sme.cash.service.BankJournalService;
import com.huicai.base.system.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.huicai.sme.cash.vo.BankJournalVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.huicai.sme.cash.dto.BankJournalSaveDTO;
import jakarta.validation.Valid;

@Tag(name = "银行日记账")
@RestController
@RequestMapping("/api/sme/cash/v1/bank-journals")
@RequiredArgsConstructor
public class BankJournalController {

    private final BankJournalService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<com.huicai.sme.cash.vo.BankJournalVO>> page(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String txType,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(BankJournalVO.from(service.pageQuery(accountId, period, txType, current, size)));
    }

    @Operation(summary = "新增日记账")
    @PostMapping
    public R<com.huicai.sme.cash.vo.BankJournalVO> create(@Valid @RequestBody BankJournalSaveDTO dto) {
        BankJournalEntity entity = dto.toEntity();
        return R.ok(BankJournalVO.from(service.create(entity, SecurityUtils.getCurrentUserId())));
    }

    @Operation(summary = "修改日记账")
    @PutMapping("/{id}")
    public R<com.huicai.sme.cash.vo.BankJournalVO> update(@PathVariable Long id, @Valid @RequestBody BankJournalSaveDTO dto) {
        BankJournalEntity entity = dto.toEntity();
        return R.ok(BankJournalVO.from(service.update(id, entity)));
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
