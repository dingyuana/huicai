package com.huicai.sme.cash.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.cash.entity.TicketEntity;
import com.huicai.sme.cash.entity.TicketTransactionEntity;
import com.huicai.sme.cash.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "票据管理")
@RestController
@RequestMapping("/api/sme/cash/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<TicketEntity>> page(
            @RequestParam(required = false) String ticketType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(ticketService.pageQuery(ticketType, status, current, size));
    }

    @Operation(summary = "查询详情")
    @GetMapping("/{id}")
    public R<TicketEntity> get(@PathVariable Long id) {
        return R.ok(ticketService.getById(id));
    }

    @Operation(summary = "新增票据")
    @PostMapping
    public R<TicketEntity> create(@RequestBody TicketEntity entity, Authentication auth) {
        return R.ok(ticketService.create(entity, 0L));
    }

    @Operation(summary = "修改票据")
    @PutMapping("/{id}")
    public R<TicketEntity> update(@PathVariable Long id, @RequestBody TicketEntity entity) {
        return R.ok(ticketService.update(id, entity));
    }

    @Operation(summary = "删除票据")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ticketService.delete(id);
        return R.ok();
    }

    @Operation(summary = "领用票据")
    @PostMapping("/{id}/issue")
    public R<TicketEntity> issue(@PathVariable Long id, Authentication auth) {
        return R.ok(ticketService.issue(id, 0L));
    }

    @Operation(summary = "兑现票据")
    @PostMapping("/{id}/cash")
    public R<TicketEntity> cash(@PathVariable Long id, Authentication auth) {
        return R.ok(ticketService.cash(id, 0L));
    }

    @Operation(summary = "作废票据")
    @PostMapping("/{id}/void")
    public R<TicketEntity> voidTicket(@PathVariable Long id, Authentication auth) {
        return R.ok(ticketService.voidTicket(id, 0L));
    }

    @Operation(summary = "交易流水")
    @GetMapping("/{ticketId}/transactions")
    public R<List<TicketTransactionEntity>> transactions(@PathVariable Long ticketId) {
        return R.ok(ticketService.getTransactions(ticketId));
    }
}