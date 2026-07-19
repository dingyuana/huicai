package com.huicai.sme.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.arap.service.BusinessDocAgingService;
import com.huicai.sme.arap.dto.BusinessDocQueryDTO;
import com.huicai.sme.arap.dto.BusinessDocVO;
import com.huicai.sme.arap.service.BusinessDocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.util.Map;

@Tag(name = "往来管理")
@RestController
@RequestMapping("/api/sme/arap/v1")
@RequiredArgsConstructor
public class ArapController {

    private final BusinessDocService docService;
    private final BusinessDocAgingService businessDocAgingService;

    @Operation(summary = "应收明细分页查询")
    @GetMapping("/receivables/page")
    public R<IPage<BusinessDocVO>> pageReceivable(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status) {
        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setCurrent(current);
        query.setSize(size);
        query.setDocTypes(List.of("INVOICE_OUT", "RECEIPT", "OTHER_RECEIVABLE"));
        if (customerId != null) {
            query.setKeyword(String.valueOf(customerId));
        }
        query.setPeriod(period);
        query.setStatus(status);
        return R.ok(docService.pageQuery(query));
    }

    @Operation(summary = "应付明细分页查询")
    @GetMapping("/payables/page")
    public R<IPage<BusinessDocVO>> pagePayable(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status) {
        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setCurrent(current);
        query.setSize(size);
        query.setDocTypes(List.of("INVOICE_IN", "PAYMENT", "EXPENSE", "OTHER_PAYABLE"));
        if (vendorId != null) {
            query.setKeyword(String.valueOf(vendorId));
        }
        query.setPeriod(period);
        query.setStatus(status);
        return R.ok(docService.pageQuery(query));
    }

    @Operation(summary = "获取应收单详情")
    @GetMapping("/receivables/{id}")
    public R<BusinessDocVO> getReceivable(@PathVariable Long id) {
        return R.ok(docService.getDetail(id));
    }

    @Operation(summary = "获取应付单详情")
    @GetMapping("/payables/{id}")
    public R<BusinessDocVO> getPayable(@PathVariable Long id) {
        return R.ok(docService.getDetail(id));
    }

    @Operation(summary = "确认应收")
    @PostMapping("/receivables/{id}/confirm")
    public R<Void> confirmReceivable(@PathVariable Long id) {
        return R.ok();
    }

    @Operation(summary = "红冲应收")
    @PostMapping("/receivables/{id}/reverse")
    public R<Void> reverseReceivable(@PathVariable Long id) {
        return R.ok();
    }

    @Operation(summary = "确认应付")
    @PostMapping("/payables/{id}/confirm")
    public R<Void> confirmPayable(@PathVariable Long id) {
        return R.ok();
    }

    @Operation(summary = "红冲应付")
    @PostMapping("/payables/{id}/reverse")
    public R<Void> reversePayable(@PathVariable Long id) {
        return R.ok();
    }

    @Operation(summary = "应收账龄分析")
    @GetMapping("/receivables/aging")
    public R<List<Map<String, Object>>> receivableAging(@RequestParam(required = false) Long customerId) {
        return R.ok(businessDocAgingService.getReceivableAging(customerId));
    }

    @Operation(summary = "应付账龄分析")
    @GetMapping("/payables/aging")
    public R<List<Map<String, Object>>> payableAging(@RequestParam(required = false) Long vendorId) {
        return R.ok(businessDocAgingService.getPayableAging(vendorId));
    }

    @Operation(summary = "逾期应收")
    @GetMapping("/receivables/overdue")
    public R<Void> overdueReceivables() {
        return R.ok();
    }
}
