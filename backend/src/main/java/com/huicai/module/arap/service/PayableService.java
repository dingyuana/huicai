package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.dto.PayableVO;
import com.huicai.module.arap.entity.PayableEntity;

import java.util.Map;

public interface PayableService {
    IPage<PayableVO> pageQuery(Long vendorId, String period, String docNo, String invoiceNo, String voucherNo, Integer current, Integer size);
    PayableVO getById(Long id);
    PayableEntity create(PayableEntity entity);
    Map<String, Object> agingAnalysis(Long vendorId);

    /** 确认应付单（草稿→已确认） */
    void confirm(Long id, Long userId);

    /** 标记为已结清（unsettled_amount=0 时调用） */
    void markSettled(Long id, Long userId);

    /** 反核销/冲销（CONFIRMED/SETTLED→REVERSED） */
    void reverse(Long id, Long userId);
}
