package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.entity.PayableEntity;

import java.util.Map;

public interface PayableService {
    IPage<PayableEntity> pageQuery(Long vendorId, String period, Integer current, Integer size);
    PayableEntity getById(Long id);
    PayableEntity create(PayableEntity entity);
    Map<String, Object> agingAnalysis(Long vendorId);
}
