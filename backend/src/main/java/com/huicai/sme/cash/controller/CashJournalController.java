package com.huicai.sme.cash.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.cash.entity.CashJournalEntity;
import com.huicai.sme.cash.service.CashJournalService;
import com.huicai.sme.cash.vo.CashJournalVO;
import com.huicai.sme.cash.vo.CashJournalVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.huicai.base.system.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import com.huicai.sme.cash.dto.CashJournalSaveDTO;
import jakarta.validation.Valid;

@Tag(name = "现金日记账")
@RestController
@RequestMapping("/api/sme/cash/v1/cash-journals")
@RequiredArgsConstructor
public class CashJournalController {

    private final CashJournalService cashJournalService;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<com.huicai.sme.cash.vo.CashJournalVO>> page(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(CashJournalVO.from(cashJournalService.pageQuery(period, startDate, endDate, current, size)));
    }

    @Operation(summary = "查询详情")
    @GetMapping("/{id}")
    public R<com.huicai.sme.cash.vo.CashJournalVO> get(@PathVariable Long id) {
        return R.ok(CashJournalVO.from(cashJournalService.getById(id)));
    }

    @Operation(summary = "新增")
    @PostMapping
    public R<com.huicai.sme.cash.vo.CashJournalVO> create(@Valid @RequestBody CashJournalSaveDTO dto) {
        CashJournalEntity entity = dto.toEntity();
        return R.ok(CashJournalVO.from(
                cashJournalService.create(entity, SecurityUtils.getCurrentUserId())));
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    public R<com.huicai.sme.cash.vo.CashJournalVO> update(@PathVariable Long id, @Valid @RequestBody CashJournalSaveDTO dto) {
        CashJournalEntity entity = dto.toEntity();
        return R.ok(CashJournalVO.from(cashJournalService.update(id, entity)));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        cashJournalService.delete(id);
        return R.ok();
    }

    @Operation(summary = "生成凭证")
    @PostMapping("/{id}/generate-voucher")
    public R<Long> generateVoucher(@PathVariable Long id) {
        return R.ok(cashJournalService.generateVoucher(id, SecurityUtils.getCurrentUserId()));
    }
}