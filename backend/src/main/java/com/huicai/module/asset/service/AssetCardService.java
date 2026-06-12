package com.huicai.module.asset.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.asset.entity.AssetCardEntity;
import com.huicai.module.asset.entity.AssetDepreciationEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AssetCardService {
    IPage<AssetCardEntity> pageQuery(String keyword, String status, Long categoryId, Integer current, Integer size);
    AssetCardEntity getById(Long id);
    AssetCardEntity create(AssetCardEntity entity);
    AssetCardEntity update(AssetCardEntity entity);
    void delete(Long id);
    BigDecimal calculateDepreciation(AssetCardEntity card, String period);
    void depreciatePeriod(String period);
    void depreciateOne(Long assetId, String period);
    List<Map<String, Object>> recentCards(int limit);
}
