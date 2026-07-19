package com.huicai.base.masterdata.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.masterdata.service.VendorService;
import com.huicai.sme.arap.mapper.BusinessDocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VendorServiceImpl implements VendorService {

    private final VendorMapper mapper;
    private final BusinessDocMapper businessDocMapper;

    @Override
    public IPage<VendorEntity> pageQuery(String keyword, Boolean isActive, Integer current, Integer size) {
        Page<VendorEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<VendorEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(VendorEntity::getCode, keyword)
                    .or().like(VendorEntity::getName, keyword)
                    .or().like(VendorEntity::getContactPerson, keyword));
        }
        if (isActive != null) {
            wrapper.eq(VendorEntity::getIsActive, isActive);
        }
        wrapper.orderByAsc(VendorEntity::getCode);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public List<VendorEntity> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<VendorEntity>()
                .eq(VendorEntity::getIsActive, true)
                .orderByAsc(VendorEntity::getCode));
    }

    @Override
    public VendorEntity getById(Long id) {
        VendorEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("供应商不存在");
        }
        return entity;
    }

    @Override
    public VendorEntity create(VendorEntity entity) {
        validateCode(entity.getCode(), null);
        if (entity.getIsActive() == null) entity.setIsActive(true);
        if (entity.getCreditLimit() == null) entity.setCreditLimit(java.math.BigDecimal.ZERO);
        if (entity.getCreditDays() == null) entity.setCreditDays(30);
        mapper.insert(entity);
        return entity;
    }

    @Override
    public VendorEntity update(VendorEntity entity) {
        VendorEntity existing = getById(entity.getId());
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
        return businessDocMapper.aggregateByVendor();
    }

    private void validateCode(String code, Long excludeId) {
        LambdaQueryWrapper<VendorEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(VendorEntity::getId, excludeId);
        }
        if (mapper.selectCount(wrapper) > 0) {
            throw new BusinessException("供应商编码已存在: " + code);
        }
    }
}
