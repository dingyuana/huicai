package com.huicai.module.finance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.finance.entity.BankStatementEntity;
import com.huicai.module.finance.service.BankStatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "银行对账单")
@RestController
@RequestMapping("/api/v1/bank-statements")
@RequiredArgsConstructor
public class BankStatementController {

    private final BankStatementService service;

    @Operation(summary = "分页查询对账单")
    @GetMapping("/page")
    public R<IPage<BankStatementEntity>> page(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(accountId, status, current, size));
    }

    @Operation(summary = "导入CSV对账单")
    @PostMapping("/import-csv")
    public R<Integer> importCsv(@RequestParam Long accountId, @RequestBody String csvContent) {
        return R.ok(service.importFromCsv(accountId, csvContent));
    }

    @Operation(summary = "智能匹配建议")
    @GetMapping("/auto-match")
    public R<List<Map<String, Object>>> autoMatch(@RequestParam Long accountId) {
        return R.ok(service.autoMatch(accountId));
    }

    @Operation(summary = "确认匹配")
    @PostMapping("/{statementId}/confirm-match")
    public R<Integer> confirmMatch(@PathVariable Long statementId, @RequestParam Long journalId) {
        return R.ok(service.confirmMatch(statementId, journalId));
    }

    @Operation(summary = "忽略对账单")
    @PostMapping("/{statementId}/ignore")
    public R<Integer> ignore(@PathVariable Long statementId) {
        return R.ok(service.ignoreStatement(statementId));
    }

    @Operation(summary = "手动触发单条分类")
    @PostMapping("/{id}/classify")
    public R<BankStatementEntity> classify(@PathVariable Long id) {
        return R.ok(service.classifySingle(id));
    }

    @Operation(summary = "出纳单条确认分类")
    @PostMapping("/{id}/review")
    public R<BankStatementEntity> review(@PathVariable Long id) {
        return R.ok(service.review(id));
    }

    @Operation(summary = "批量确认分类")
    @PostMapping("/batch-review")
    public R<Integer> batchReview(@RequestBody List<Long> ids) {
        return R.ok(service.batchReview(ids));
    }
}
