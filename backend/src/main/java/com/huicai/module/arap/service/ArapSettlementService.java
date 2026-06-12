package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import com.huicai.module.arap.entity.ArapSettlementEntryEntity;

import java.util.List;

public interface ArapSettlementService {
    IPage<ArapSettlementEntity> pageQuery(String status, Integer current, Integer size);
    ArapSettlementEntity getById(Long id);
    ArapSettlementEntity create(ArapSettlementEntity entity, List<ArapSettlementEntryEntity> entries);
    ArapSettlementEntity confirm(Long id);
    void delete(Long id);
}
