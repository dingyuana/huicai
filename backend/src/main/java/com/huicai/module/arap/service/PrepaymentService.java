package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.entity.PrepaymentEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预付款/预收款服务接口
 */
public interface PrepaymentService {

    /** 创建预付款 */
    PrepaymentEntity createPaymentPrepay(Long vendorId, BigDecimal amount, String period,
                                          java.time.LocalDate txDate, String summary,
                                          String sourceDocType, Long sourceDocId,
                                          Long docId, Long voucherId, String createdBy);

    /** 创建预收款 */
    PrepaymentEntity createReceiptPrepay(Long customerId, BigDecimal amount, String period,
                                          java.time.LocalDate txDate, String summary,
                                          String sourceDocType, Long sourceDocId,
                                          Long docId, Long voucherId, String createdBy);

    /** 查询供应商预付款 */
    List<PrepaymentEntity> listPaymentPrepay(Long vendorId);

    /** 查询客户预收款 */
    List<PrepaymentEntity> listReceiptPrepay(Long customerId);

    /** 分页查询预付款 */
    IPage<PrepaymentEntity> pagePaymentPrepay(Long vendorId, Integer current, Integer size);

    /** 分页查询预收款 */
    IPage<PrepaymentEntity> pageReceiptPrepay(Long customerId, Integer current, Integer size);

    /** 获取单个预付/预收详情 */
    PrepaymentEntity getById(Long id);

    /** 冲销预付款: 用预付款冲应付账款 */
    java.math.BigDecimal settlePayable(Long prepaymentId, Long payableId, BigDecimal settleAmount, String remark);

    /** 冲销预收款: 用预收款冲应收账款 */
    java.math.BigDecimal settleReceivable(Long prepaymentId, Long receivableId, BigDecimal settleAmount, String remark);

    /** 反冲销 */
    void reverseSettle(Long id);

    /** 确认预付/预收 (DRAFT -> CONFIRMED) */
    void confirm(Long id);

    /** 取消预付/预收 (CONFIRMED -> CANCELLED) */
    void cancel(Long id);
}
