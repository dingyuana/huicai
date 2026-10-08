package com.huicai.sme.cash.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.cash.dto.TicketDTO;
import com.huicai.sme.cash.entity.TicketEntity;
import com.huicai.sme.cash.entity.TicketTransactionEntity;
import com.huicai.sme.cash.service.TicketService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.sme.cash.vo.TicketTransactionVO;
import com.huicai.sme.cash.vo.TicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "票据管理")
@RestController
@RequestMapping("/api/sme/cash/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<TicketVO>> page(
            @RequestParam(required = false) String ticketType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return R.ok(TicketVO.from(ticketService.pageQuery(ticketType, status, current, size, scope, startDate, endDate)));
    }

    @Operation(summary = "查询详情")
    @GetMapping("/{id}")
    public R<TicketVO> get(@PathVariable Long id) {
        return R.ok(TicketVO.from(ticketService.getById(id)));
    }

    @Operation(summary = "新增票据")
    @PostMapping
    public R<TicketVO> create(@Valid @RequestBody TicketDTO dto) {
        return R.ok(TicketVO.from(ticketService.create(dto.toEntity(), SecurityUtils.getCurrentUserId())));
    }

    @Operation(summary = "修改票据")
    @PutMapping("/{id}")
    public R<TicketVO> update(@PathVariable Long id, @Valid @RequestBody TicketDTO dto) {
        return R.ok(TicketVO.from(ticketService.update(id, dto.toEntity())));
    }

    @Operation(summary = "删除票据")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ticketService.delete(id);
        return R.ok();
    }

    @Operation(summary = "领用票据")
    @PostMapping("/{id}/issue")
    public R<TicketVO> issue(@PathVariable Long id) {
        return R.ok(TicketVO.from(ticketService.issue(id, SecurityUtils.getCurrentUserId())));
    }

    @Operation(summary = "兑现票据")
    @PostMapping("/{id}/cash")
    public R<TicketVO> cash(@PathVariable Long id) {
        return R.ok(TicketVO.from(ticketService.cash(id, SecurityUtils.getCurrentUserId())));
    }

    @Operation(summary = "作废票据")
    @PostMapping("/{id}/void")
    public R<TicketVO> voidTicket(@PathVariable Long id) {
        return R.ok(TicketVO.from(ticketService.voidTicket(id, SecurityUtils.getCurrentUserId())));
    }

    @Operation(summary = "交易流水")
    @GetMapping("/{ticketId}/transactions")
    public R<List<TicketTransactionVO>> transactions(@PathVariable Long ticketId) {
        return R.ok(TicketTransactionVO.from(ticketService.getTransactions(ticketId)));
    }
}