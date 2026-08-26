package com.huicai.sme.tax.service.impl;

import com.huicai.sme.tax.dto.vo.InvoiceReconcileVO;
import com.huicai.sme.tax.mapper.InvoicePaymentReconcileMapper;
import com.huicai.sme.tax.service.InvoicePaymentReconcileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * P58: 发票-收付款勾稽（三流合一只读视图）.
 */
@Service
@RequiredArgsConstructor
public class InvoicePaymentReconcileServiceImpl implements InvoicePaymentReconcileService {

    private final InvoicePaymentReconcileMapper reconcileMapper;

    @Override
    public List<InvoiceReconcileVO> queryInputReconcile(String period, Long vendorId) {
        return reconcileMapper.queryInputReconcile(period, vendorId);
    }

    @Override
    public List<InvoiceReconcileVO> queryOutputReconcile(String period, Long customerId) {
        return reconcileMapper.queryOutputReconcile(period, customerId);
    }
}
