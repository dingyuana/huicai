package com.huicai.sme.asset.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.entity.AssetCategoryEntity;
import com.huicai.sme.asset.mapper.AssetCategoryMapper;
import com.huicai.sme.asset.service.AssetCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetCategoryServiceImpl implements AssetCategoryService {

    private final AssetCategoryMapper mapper;

    @Override
    public IPage<AssetCategoryEntity> pageQuery(String keyword, Integer current, Integer size) {
        Page<AssetCategoryEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<AssetCategoryEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(AssetCategoryEntity::getCode, keyword)
                    .or().like(AssetCategoryEntity::getName, keyword));
        }
        wrapper.orderByAsc(AssetCategoryEntity::getCode);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public List<AssetCategoryEntity> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<AssetCategoryEntity>()
                .orderByAsc(AssetCategoryEntity::getCode));
    }

    @Override
    public AssetCategoryEntity getById(Long id) {
        AssetCategoryEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("资产类别不存在");
        }
        return entity;
    }

    @Override
    public AssetCategoryEntity create(AssetCategoryEntity entity) {
        validateCode(entity.getCode(), null);
        if (entity.getLevel() == null) entity.setLevel(1);
        if (entity.getResidualRate() == null) entity.setResidualRate(new java.math.BigDecimal("0.05"));
        if (entity.getDepreciationMethod() == null) entity.setDepreciationMethod("STRAIGHT_LINE");
        if (entity.getUsefulLife() == null) entity.setUsefulLife(5);
        mapper.insert(entity);
        return entity;
    }

    @Override
    public AssetCategoryEntity update(AssetCategoryEntity entity) {
        AssetCategoryEntity existing = getById(entity.getId());
        validateCode(entity.getCode(), entity.getId());
        existing.setCode(entity.getCode());
        existing.setName(entity.getName());
        existing.setParentId(entity.getParentId());
        existing.setDepreciationMethod(entity.getDepreciationMethod());
        existing.setUsefulLife(entity.getUsefulLife());
        existing.setResidualRate(entity.getResidualRate());
        existing.setAssetSubjectId(entity.getAssetSubjectId());
        existing.setDepreciationSubjectId(entity.getDepreciationSubjectId());
        existing.setExpenseSubjectId(entity.getExpenseSubjectId());
        existing.setRemark(entity.getRemark());
        mapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private void validateCode(String code, Long excludeId) {
        LambdaQueryWrapper<AssetCategoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssetCategoryEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(AssetCategoryEntity::getId, excludeId);
        }
        if (mapper.selectCount(wrapper) > 0) {
            throw new BusinessException("资产类别编码已存在: " + code);
        }
    }
}
