package com.huicai.module.finance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.finance.entity.BankAccountEntity;
import com.huicai.module.finance.service.BankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "银行账户")
@RestController
@RequestMapping("/api/v1/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<BankAccountEntity>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(keyword, current, size));
    }

    @Operation(summary = "查询所有启用账户")
    @GetMapping("/active")
    public R<List<BankAccountEntity>> active() {
        return R.ok(service.listActive());
    }

    @Operation(summary = "账户详情")
    @GetMapping("/{id}")
    public R<BankAccountEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "新增账户")
    @PostMapping
    public R<BankAccountEntity> create(@RequestBody BankAccountEntity entity) {
        return R.ok(service.create(entity));
    }

    @Operation(summary = "修改账户")
    @PutMapping("/{id}")
    public R<BankAccountEntity> update(@PathVariable Long id, @RequestBody BankAccountEntity entity) {
        return R.ok(service.update(id, entity));
    }

    @Operation(summary = "删除账户")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
