package com.huicai.sme.asset.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.asset.entity.AssetInventoryEntity;
import com.huicai.sme.asset.entity.AssetInventoryEntryEntity;

import java.util.List;

public interface AssetInventoryService {
    IPage<AssetInventoryEntity> pageQuery(String status, Integer current, Integer size);
    AssetInventoryEntity getById(Long id);
    AssetInventoryEntity create(AssetInventoryEntity entity, List<AssetInventoryEntryEntity> entries);
    AssetInventoryEntity complete(Long id, List<AssetInventoryEntryEntity> entries);
    void delete(Long id);
}
