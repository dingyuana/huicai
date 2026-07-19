package com.huicai.base.masterdata.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.masterdata.entity.EmployeeEntity;

import java.util.List;

public interface EmployeeService {
    IPage<EmployeeEntity> pageQuery(String keyword, Boolean isActive, Integer current, Integer size);
    List<EmployeeEntity> listAll();
    EmployeeEntity getById(Long id);

    /** 按姓名模糊查询（P11-3 银行流水匹配用） */
    EmployeeEntity findByName(String name);

    EmployeeEntity create(EmployeeEntity entity);
    EmployeeEntity update(EmployeeEntity entity);
    void delete(Long id);
}
