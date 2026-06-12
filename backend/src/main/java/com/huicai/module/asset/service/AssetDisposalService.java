package com.huicai.module.asset.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.asset.entity.AssetDisposalEntity;

public interface AssetDisposalService {
    IPage<AssetDisposalEntity> pageQuery(String status, Integer current, Integer size);
    AssetDisposalEntity getById(Long id);
    AssetDisposalEntity create(AssetDisposalEntity entity);
    AssetDisposalEntity approve(Long id);
    void delete(Long id);
}
