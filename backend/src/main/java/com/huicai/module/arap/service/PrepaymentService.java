package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.entity.PrepaymentEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预付款/预收款服务 — 管理供应商预付账款、客户预收账款.
 * <p>
 * 核心流程:
 * <pre>
 *   创建(DRAFT) → 确认(CONFIRMED) → 核销抵扣(APPLIED)
 *                            ↘ 反冲(REVERSED)
 * </pre>
 */
public interface PrepaymentService {

    /** 分页查询预付款/预收款 */
    IPage<PrepaymentEntity> pageQuery(Long vendorId, Long customerId, String status, Integer current, Integer size);

    /** 根据 ID 查询 */
    PrepaymentEntity getById(Long id);

    /** 新增预付款记录 (DRAFT) */
    PrepaymentEntity create(PrepaymentEntity entity);

    /** 确认预付款 (DRAFT → CONFIRMED) */
    PrepaymentEntity confirm(Long id);

    PrepaymentEntity applyToPayable(Long prepayId, Long payableId, BigDecimal applyAmount,
                                    String period, Long userId, String summary);

    /**
     * 预收冲应收 — 将客户预收款 APPLY 到应收单.
     * <p>
     * 会计处理: 借 预收账款(2203) / 贷 应收账款(1122)
     * 逻辑同 applyToPayable, 但面向客户侧.
     */
    PrepaymentEntity applyToReceivable(Long prepayId, Long receivableId, BigDecimal applyAmount,
                                       String period, Long userId, String summary);

    void reverse(Long id, Long userId, String reason);

    /** 获取指定供应商的未结清预付款列表 */
    List<PrepaymentEntity> getOpenPrepayments(Long vendorId);

    /** 获取指定客户的未结清预收款列表 */
    List<PrepaymentEntity> getOpenPrepaymentsForCustomer(Long customerId);
}
