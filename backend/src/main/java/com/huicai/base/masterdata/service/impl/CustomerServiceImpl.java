package com.huicai.base.masterdata.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.masterdata.service.CustomerService;
import com.huicai.sme.arap.mapper.BusinessDocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper mapper;
    private final BusinessDocMapper businessDocMapper;

    @Override
    public IPage<CustomerEntity> pageQuery(String keyword, Boolean isActive, Integer current, Integer size) {
        Page<CustomerEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<CustomerEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(CustomerEntity::getCode, keyword)
                    .or().like(CustomerEntity::getName, keyword)
                    .or().like(CustomerEntity::getContactPerson, keyword));
        }
        if (isActive != null) {
            wrapper.eq(CustomerEntity::getIsActive, isActive);
        }
        wrapper.orderByAsc(CustomerEntity::getCode);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public List<CustomerEntity> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<CustomerEntity>()
                .eq(CustomerEntity::getIsActive, true)
                .orderByAsc(CustomerEntity::getCode));
    }

    @Override
    public CustomerEntity getById(Long id) {
        CustomerEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("客户不存在");
        }
        return entity;
    }

    @Override
    public CustomerEntity create(CustomerEntity entity) {
        validateCode(entity.getCode(), null);
        if (entity.getIsActive() == null) entity.setIsActive(true);
        if (entity.getCreditLimit() == null) entity.setCreditLimit(java.math.BigDecimal.ZERO);
        if (entity.getCreditDays() == null) entity.setCreditDays(30);
        mapper.insert(entity);
        return entity;
    }

    @Override
    public CustomerEntity update(CustomerEntity entity) {
        CustomerEntity existing = getById(entity.getId());
        validateCode(entity.getCode(), entity.getId());
        existing.setCode(entity.getCode());
        existing.setName(entity.getName());
        existing.setContactPerson(entity.getContactPerson());
        existing.setPhone(entity.getPhone());
        existing.setEmail(entity.getEmail());
        existing.setAddress(entity.getAddress());
        existing.setTaxNo(entity.getTaxNo());
        existing.setBankName(entity.getBankName());
        existing.setBankAccount(entity.getBankAccount());
        existing.setCreditLimit(entity.getCreditLimit());
        existing.setCreditDays(entity.getCreditDays());
        existing.setSubjectId(entity.getSubjectId());
        existing.setIsActive(entity.getIsActive());
        existing.setRemark(entity.getRemark());
        mapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public List<Map<String, Object>> unsettledSummary() {
        return businessDocMapper.aggregateByCustomer();
    }

    private void validateCode(String code, Long excludeId) {
        LambdaQueryWrapper<CustomerEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(CustomerEntity::getId, excludeId);
        }
        if (mapper.selectCount(wrapper) > 0) {
            throw new BusinessException("客户编码已存在: " + code);
        }
    }
}
