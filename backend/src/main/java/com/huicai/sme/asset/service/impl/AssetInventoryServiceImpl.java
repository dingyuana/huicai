package com.huicai.sme.asset.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.entity.AssetInventoryEntity;
import com.huicai.sme.asset.entity.AssetInventoryEntryEntity;
import com.huicai.sme.asset.mapper.AssetInventoryEntryMapper;
import com.huicai.sme.asset.mapper.AssetInventoryMapper;
import com.huicai.sme.asset.service.AssetInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetInventoryServiceImpl implements AssetInventoryService {

    private final AssetInventoryMapper mapper;
    private final AssetInventoryEntryMapper entryMapper;

    @Override
    public IPage<AssetInventoryEntity> pageQuery(String status, Integer current, Integer size) {
        Page<AssetInventoryEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<AssetInventoryEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(AssetInventoryEntity::getStatus, status);
        }
        wrapper.orderByDesc(AssetInventoryEntity::getCreatedAt);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public AssetInventoryEntity getById(Long id) {
        AssetInventoryEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("盘点单不存在");
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetInventoryEntity create(AssetInventoryEntity entity, List<AssetInventoryEntryEntity> entries) {
        if (entity.getStatus() == null) entity.setStatus("DRAFT");
        if (entity.getTotalCount() == null) entity.setTotalCount(entries == null ? 0 : entries.size());
        mapper.insert(entity);
        if (entries != null) {
            for (AssetInventoryEntryEntity entry : entries) {
                entry.setInventoryId(entity.getId());
                if (entry.getDiffQuantity() == null) {
                    int diff = entry.getActualQuantity() - entry.getBookQuantity();
                    entry.setDiffQuantity(diff);
                    if (diff > 0) entry.setDiffType("PROFIT");
                    else if (diff < 0) entry.setDiffType("LOSS");
                    else entry.setDiffType("NORMAL");
                }
                entryMapper.insert(entry);
            }
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetInventoryEntity complete(Long id, List<AssetInventoryEntryEntity> entries) {
        AssetInventoryEntity entity = getById(id);
        if (!"DRAFT".equals(entity.getStatus()) && !"IN_PROGRESS".equals(entity.getStatus())) {
            throw new BusinessException("当前状态不可完成盘点");
        }
        // 清理旧明细
        entryMapper.delete(new LambdaQueryWrapper<AssetInventoryEntryEntity>()
                .eq(AssetInventoryEntryEntity::getInventoryId, id));
        int profit = 0;
        int loss = 0;
        if (entries != null) {
            for (AssetInventoryEntryEntity entry : entries) {
                entry.setInventoryId(id);
                int diff = entry.getActualQuantity() - entry.getBookQuantity();
                entry.setDiffQuantity(diff);
                if (diff > 0) {
                    entry.setDiffType("PROFIT");
                    profit++;
                } else if (diff < 0) {
                    entry.setDiffType("LOSS");
                    loss++;
                } else {
                    entry.setDiffType("NORMAL");
                }
                entryMapper.insert(entry);
            }
        }
        entity.setStatus("COMPLETED");
        entity.setProfitCount(profit);
        entity.setLossCount(loss);
        mapper.updateById(entity);
        return entity;
    }

    @Override
    public void delete(Long id) {
        AssetInventoryEntity entity = getById(id);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        entryMapper.delete(new LambdaQueryWrapper<AssetInventoryEntryEntity>()
                .eq(AssetInventoryEntryEntity::getInventoryId, id));
        mapper.deleteById(id);
    }
}
