package com.huicai.sme.arap.controller;

import com.huicai.common.response.R;
import com.huicai.sme.arap.service.PurchaseReturnService;
import com.huicai.sme.arap.service.PurchaseReturnService.PurchaseReturnRequest;
import com.huicai.sme.arap.service.PurchaseReturnService.PurchaseReturnVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "采购退货（财务）")
@RestController
@RequestMapping("/api/sme/arap/v1/purchase-returns")
@RequiredArgsConstructor
public class PurchaseReturnController {

    private final PurchaseReturnService service;

    @Operation(summary = "创建采购退货（财务处理）")
    @PostMapping
    public R<PurchaseReturnVO> createReturn(@RequestBody PurchaseReturnRequest request) {
        return R.ok(service.createReturn(request));
    }

    @Operation(summary = "查询采购退货详情")
    @GetMapping("/{id}")
    public R<PurchaseReturnVO> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "查询采购退货列表")
    @GetMapping("/list")
    public R<List<PurchaseReturnVO>> listReturns() {
        return R.ok(service.listReturns());
    }
}