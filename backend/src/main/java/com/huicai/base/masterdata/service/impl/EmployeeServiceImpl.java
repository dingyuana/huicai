package com.huicai.base.masterdata.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.masterdata.entity.EmployeeEntity;
import com.huicai.base.masterdata.mapper.EmployeeMapper;
import com.huicai.base.masterdata.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper mapper;

    @Override
    public IPage<EmployeeEntity> pageQuery(String keyword, Boolean isActive, Integer current, Integer size) {
        Page<EmployeeEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<EmployeeEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(EmployeeEntity::getCode, keyword)
                    .or().like(EmployeeEntity::getName, keyword)
                    .or().like(EmployeeEntity::getPhone, keyword));
        }
        if (isActive != null) {
            wrapper.eq(EmployeeEntity::getIsActive, isActive);
        }
        wrapper.orderByAsc(EmployeeEntity::getCode);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public List<EmployeeEntity> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<EmployeeEntity>()
                .eq(EmployeeEntity::getIsActive, true)
                .orderByAsc(EmployeeEntity::getCode));
    }

    @Override
    public EmployeeEntity getById(Long id) {
        EmployeeEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("员工不存在: " + id);
        }
        return entity;
    }

    @Override
    public EmployeeEntity findByName(String name) {
        if (StrUtil.isBlank(name)) return null;
        // 1) 全名匹配
        List<EmployeeEntity> exact = mapper.selectList(
                new LambdaQueryWrapper<EmployeeEntity>().eq(EmployeeEntity::getName, name));
        if (!exact.isEmpty()) return exact.get(0);
        // 2) 模糊匹配
        List<EmployeeEntity> fuzzy = mapper.selectList(
                new LambdaQueryWrapper<EmployeeEntity>().like(EmployeeEntity::getName, name).last("LIMIT 1"));
        return fuzzy.isEmpty() ? null : fuzzy.get(0);
    }

    @Override
    public EmployeeEntity create(EmployeeEntity entity) {
        if (StrUtil.isBlank(entity.getName())) {
            throw new BusinessException("员工姓名不能为空");
        }
        if (entity.getIsActive() == null) entity.setIsActive(true);
        validateCode(entity.getCode(), null);
        mapper.insert(entity);
        return entity;
    }

    @Override
    public EmployeeEntity update(EmployeeEntity entity) {
        EmployeeEntity existing = getById(entity.getId());
        validateCode(entity.getCode(), entity.getId());
        existing.setCode(entity.getCode());
        existing.setName(entity.getName());
        existing.setDeptId(entity.getDeptId());
        existing.setPhone(entity.getPhone());
        existing.setEmail(entity.getEmail());
        existing.setBankName(entity.getBankName());
        existing.setBankAccount(entity.getBankAccount());
        existing.setIdCard(entity.getIdCard());
        existing.setIsActive(entity.getIsActive());
        existing.setRemark(entity.getRemark());
        mapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private void validateCode(String code, Long excludeId) {
        if (StrUtil.isBlank(code)) return;
        LambdaQueryWrapper<EmployeeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmployeeEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(EmployeeEntity::getId, excludeId);
        }
        if (mapper.selectCount(wrapper) > 0) {
            throw new BusinessException("员工工号已存在: " + code);
        }
    }
}
