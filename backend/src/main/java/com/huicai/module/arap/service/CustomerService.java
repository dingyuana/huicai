package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.entity.CustomerEntity;

import java.util.List;
import java.util.Map;

public interface CustomerService {
    IPage<CustomerEntity> pageQuery(String keyword, Boolean isActive, Integer current, Integer size);
    List<CustomerEntity> listAll();
    CustomerEntity getById(Long id);
    CustomerEntity create(CustomerEntity entity);
    CustomerEntity update(CustomerEntity entity);
    void delete(Long id);
    List<Map<String, Object>> unsettledSummary();
}
