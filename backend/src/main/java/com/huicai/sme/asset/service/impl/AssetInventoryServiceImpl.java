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
        // P0-fix: 原为 `if (entity.getStatus() == null) entity.setStatus("DRAFT")`。
        // AssetInventoryController#create 虽用 CreateRequest 包装，但内层仍是 Entity，
        // 其 status 字段照样可被客户端绑定（只多加一层包装，不构成隔离）。
        // 与 TaxServiceImpl.createOutput / createDeclaration / AssetDisposalServiceImpl.create
        // 同一写法（全仓指纹扫描命中第 4 处）。改为无条件强制 DRAFT。
        entity.setStatus("DRAFT");
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
        // P0-fix: 原为 setStatus("COMPLETED")，但 chk_inv_status 的允许集只有
        // DRAFT / IN_PROGRESS / CONFIRMED / VOUCHERED —— **不含 COMPLETED**
        // ⇒ 紧随其后的 update 必抛 23514，「完成盘点」这个动作**必然 500**。
        // 证据链：①本类 AssetInventoryStateMachineService 的类注释明写
        // 「封装资产盘点 3 状态 (IN_PROGRESS/CONFIRMED/VOUCHERED) 的状态流转检查」
        // —— CONFIRMED 正是设计中的「盘点完成态」；②全仓**无任何代码**给本表写 CONFIRMED
        // （该合法值一直空置）；③库内 t_asset_inventory **0 行** —— 与
        // 「完成盘点一调就崩，故从未产生过数据」完全吻合。
        // 故判为**代码写错值**，改用既有的合法态 CONFIRMED；
        // **不**给 CHECK 补 COMPLETED —— 那会造出「盘点完成」的第二种合法拼写，
        // 下游 status='CONFIRMED' 的查询将静默漏掉它们（AGENTS §4.2 第 19 条）。
        entity.setStatus("CONFIRMED");
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
