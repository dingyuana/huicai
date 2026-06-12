package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.entity.ReceivableEntity;

import java.util.List;
import java.util.Map;

public interface ReceivableService {
    IPage<ReceivableEntity> pageQuery(Long customerId, String period, Integer current, Integer size);
    ReceivableEntity getById(Long id);
    ReceivableEntity create(ReceivableEntity entity);
    List<Map<String, Object>> overdueList();
    Map<String, Object> agingAnalysis(Long customerId);
    Map<String, Object> overallAging();
}
