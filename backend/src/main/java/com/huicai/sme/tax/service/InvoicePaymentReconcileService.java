package com.huicai.sme.tax.service;

import com.huicai.sme.tax.dto.vo.InvoiceReconcileVO;

import java.util.List;

/**
 * P58: 发票-收付款勾稽（三流合一只读视图）.
 */
public interface InvoicePaymentReconcileService {

    /** 按供应商聚合进项发票勾稽状态 */
    List<InvoiceReconcileVO> queryInputReconcile(String period, Long vendorId);

    /** 按客户聚合销项发票勾稽状态 */
    List<InvoiceReconcileVO> queryOutputReconcile(String period, Long customerId);
}
