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

    /** SPEC-P157 R4：绑定员工自助登录账号（校验账号存在、同企业、未被他员工绑定；user_id=null 即解绑）。 */
    EmployeeEntity bindUser(Long employeeId, Long userId);
}
