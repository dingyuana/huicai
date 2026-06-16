package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.dto.PayableVO;
import com.huicai.module.arap.entity.PayableEntity;

import java.util.Map;

public interface PayableService {
    IPage<PayableVO> pageQuery(Long vendorId, String period, Integer current, Integer size);
    PayableVO getById(Long id);
    PayableEntity create(PayableEntity entity);
    Map<String, Object> agingAnalysis(Long vendorId);
}
