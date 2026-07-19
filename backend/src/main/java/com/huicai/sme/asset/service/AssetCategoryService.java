package com.huicai.sme.asset.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.asset.entity.AssetCategoryEntity;

import java.util.List;

public interface AssetCategoryService {
    IPage<AssetCategoryEntity> pageQuery(String keyword, Integer current, Integer size);
    List<AssetCategoryEntity> listAll();
    AssetCategoryEntity getById(Long id);
    AssetCategoryEntity create(AssetCategoryEntity entity);
    AssetCategoryEntity update(AssetCategoryEntity entity);
    void delete(Long id);
}
