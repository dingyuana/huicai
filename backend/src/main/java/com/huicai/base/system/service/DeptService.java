package com.huicai.base.system.service;

import com.huicai.base.system.entity.DeptEntity;

import java.util.List;

public interface DeptService {
    List<DeptEntity> getDeptTree();
    DeptEntity getById(Long id);
    void create(DeptEntity dept);
    void update(DeptEntity dept);
    void delete(Long id);
}
