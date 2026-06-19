package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.dto.ReceivableVO;
import com.huicai.module.arap.entity.ReceivableEntity;

import java.util.List;
import java.util.Map;

public interface ReceivableService {
    IPage<ReceivableVO> pageQuery(Long customerId, String period, Integer current, Integer size);
    ReceivableVO getById(Long id);
    ReceivableEntity create(ReceivableEntity entity);
    List<Map<String, Object>> overdueList();
    Map<String, Object> agingAnalysis(Long customerId);
    Map<String, Object> overallAging();

    /** 确认应收单（草稿→已确认） */
    void confirm(Long id, Long userId);

    /** 标记为已结清（unsettled_amount=0 时调用） */
    void markSettled(Long id, Long userId);

    /** 反核销/冲销（CONFIRMED/SETTLED→REVERSED） */
    void reverse(Long id, Long userId);
}
