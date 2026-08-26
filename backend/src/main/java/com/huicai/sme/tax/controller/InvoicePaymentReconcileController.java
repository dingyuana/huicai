package com.huicai.sme.tax.controller;

import com.huicai.common.response.R;
import com.huicai.sme.tax.dto.vo.InvoiceReconcileVO;
import com.huicai.sme.tax.service.InvoicePaymentReconcileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * P58: 发票-收付款勾稽（三流合一视图）.
 */
@RestController
@RequestMapping("/api/sme/tax/v1/invoice-reconcile")
@Tag(name = "发票勾稽", description = "发票与收付款核销状态聚合视图")
@RequiredArgsConstructor
public class InvoicePaymentReconcileController {

    private final InvoicePaymentReconcileService service;

    @Operation(summary = "进项发票勾稽（按供应商）")
    @GetMapping("/input")
    public R<List<InvoiceReconcileVO>> input(@RequestParam(required = false) String period,
                                            @RequestParam(required = false) Long vendorId) {
        return R.ok(service.queryInputReconcile(period, vendorId));
    }

    @Operation(summary = "销项发票勾稽（按客户）")
    @GetMapping("/output")
    public R<List<InvoiceReconcileVO>> output(@RequestParam(required = false) String period,
                                              @RequestParam(required = false) Long customerId) {
        return R.ok(service.queryOutputReconcile(period, customerId));
    }
}
