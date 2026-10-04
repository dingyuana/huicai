package com.huicai.sme.tax.service.impl;

import org.springframework.transaction.annotation.Transactional;
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
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
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
