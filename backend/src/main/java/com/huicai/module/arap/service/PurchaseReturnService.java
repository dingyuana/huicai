package com.huicai.module.arap.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购退货服务（P53 M3）
 */
public interface PurchaseReturnService {

    PurchaseReturnVO createReturn(PurchaseReturnRequest request);

    PurchaseReturnVO getById(Long id);

    List<PurchaseReturnVO> listReturns();

    record PurchaseReturnRequest(
        String originalDocNo,
        Long vendorId,
        BigDecimal returnAmount,
        BigDecimal taxAmount,
        String reason
    ) {}

    record PurchaseReturnVO(
        Long id,
        String returnNo,
        Long vendorId,
        String vendorName,
        String originalDocNo,
        BigDecimal returnAmount,
        BigDecimal taxAmount,
        String reason,
        String status,
        Long voucherId,
        String voucherNo
    ) {}
}