package com.huicai.base.auth.service;

import com.huicai.base.auth.entity.DeptEntity;

import java.util.List;

public interface DeptService {
    List<DeptEntity> getDeptTree();
    DeptEntity getById(Long id);
    void create(DeptEntity dept);
    void update(DeptEntity dept);
    void delete(Long id);
}
