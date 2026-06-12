package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.entity.VendorEntity;

import java.util.List;
import java.util.Map;

public interface VendorService {
    IPage<VendorEntity> pageQuery(String keyword, Boolean isActive, Integer current, Integer size);
    List<VendorEntity> listAll();
    VendorEntity getById(Long id);
    VendorEntity create(VendorEntity entity);
    VendorEntity update(VendorEntity entity);
    void delete(Long id);
    List<Map<String, Object>> unsettledSummary();
}
