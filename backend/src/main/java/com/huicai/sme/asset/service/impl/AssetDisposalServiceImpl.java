package com.huicai.sme.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.entity.AssetDisposalEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.mapper.AssetDisposalMapper;
import com.huicai.sme.asset.service.AssetDisposalService;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
/**
 * 类级事务（P102/M5b，2026-10-03）：走租户表的路径必须有事务，否则
 * TenantRlsInitializer 切面不触发、app.enterprise_id 设不进去，
 * 应用以非超级用户连接时 RLS 会把本企业数据也过滤掉（读 0 行）。
 * 方法级 @Transactional 优先级更高，不受此影响。
 */
@Transactional
public class AssetDisposalServiceImpl implements AssetDisposalService {

    private final AssetDisposalMapper mapper;
    private final AssetCardMapper cardMapper;

    @Override
    public IPage<AssetDisposalEntity> pageQuery(String status, Integer current, Integer size) {
        Page<AssetDisposalEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<AssetDisposalEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(AssetDisposalEntity::getStatus, status);
        }
        wrapper.orderByDesc(AssetDisposalEntity::getCreatedAt);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public AssetDisposalEntity getById(Long id) {
        AssetDisposalEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("资产处置单不存在");
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetDisposalEntity create(AssetDisposalEntity entity) {
        AssetCardEntity card = cardMapper.selectById(entity.getAssetId());
        if (card == null) {
            throw new BusinessException("资产不存在");
        }
        if (!"IN_USE".equals(card.getStatus())) {
            throw new BusinessException("仅可在用资产可处置");
        }
        // 从卡片同步数据
        entity.setOriginalValue(card.getOriginalValue());
        entity.setAccumulatedDepreciation(card.getAccumulatedDepreciation());
        entity.setNetValue(card.getNetValue());
        // 计算处置损益
        BigDecimal income = entity.getDisposalIncome() == null ? BigDecimal.ZERO : entity.getDisposalIncome();
        BigDecimal expense = entity.getDisposalExpense() == null ? BigDecimal.ZERO : entity.getDisposalExpense();
        BigDecimal gainLoss = income.subtract(entity.getNetValue()).subtract(expense);
        entity.setGainLoss(gainLoss);
        // P0-fix: 原为 `if (entity.getStatus() == null) entity.setStatus("DRAFT")`。
        // AssetDisposalController#create 直接接 @RequestBody AssetDisposalEntity（违反铁律 #13），
        // 只在 null 时兜底 ⇒ 客户端显式传 status 会被照单全收。
        // chk_disposal_status 允许 DRAFT/APPROVED/VOUCHERED，故 POST status=APPROVED 可
        // **一步跳过** approve() 这次人工审批（违反铁律 #1：人是唯一审核主体）。
        // 与 TaxServiceImpl.createOutput / createDeclaration 同一写法，属第 3 处。
        // 改为**无条件**强制 DRAFT —— 创建态是唯一合法起点。
        entity.setStatus("DRAFT");
        mapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetDisposalEntity approve(Long id) {
        AssetDisposalEntity entity = getById(id);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可审批");
        }
        entity.setStatus("APPROVED");
        mapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AssetDisposalEntity entity = getById(id);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        mapper.deleteById(id);
    }
}
