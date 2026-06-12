package com.huicai.module.asset.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.asset.entity.AssetCardEntity;
import com.huicai.module.asset.entity.AssetCategoryEntity;
import com.huicai.module.asset.entity.AssetDepreciationEntity;
import com.huicai.module.asset.mapper.AssetCardMapper;
import com.huicai.module.asset.mapper.AssetCategoryMapper;
import com.huicai.module.asset.mapper.AssetDepreciationMapper;
import com.huicai.module.asset.service.AssetCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetCardServiceImpl implements AssetCardService {

    private final AssetCardMapper cardMapper;
    private final AssetCategoryMapper categoryMapper;
    private final AssetDepreciationMapper depreciationMapper;

    @Override
    public IPage<AssetCardEntity> pageQuery(String keyword, String status, Long categoryId,
                                              Integer current, Integer size) {
        Page<AssetCardEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<AssetCardEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(AssetCardEntity::getAssetCode, keyword)
                    .or().like(AssetCardEntity::getAssetName, keyword));
        }
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(AssetCardEntity::getStatus, status);
        }
        if (categoryId != null) {
            wrapper.eq(AssetCardEntity::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(AssetCardEntity::getCreatedAt);
        return cardMapper.selectPage(page, wrapper);
    }

    @Override
    public AssetCardEntity getById(Long id) {
        AssetCardEntity entity = cardMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("资产卡片不存在");
        }
        return entity;
    }

    @Override
    public AssetCardEntity create(AssetCardEntity entity) {
        validateCode(entity.getAssetCode(), null);
        if (entity.getStatus() == null) entity.setStatus("IN_USE");
        if (entity.getAccumulatedDepreciation() == null) {
            entity.setAccumulatedDepreciation(BigDecimal.ZERO);
        }
        if (entity.getResidualValue() == null) {
            entity.setResidualValue(BigDecimal.ZERO);
        }
        entity.setNetValue(entity.getOriginalValue()
                .subtract(entity.getAccumulatedDepreciation())
                .subtract(entity.getResidualValue())
                .max(BigDecimal.ZERO));
        cardMapper.insert(entity);
        return entity;
    }

    @Override
    public AssetCardEntity update(AssetCardEntity entity) {
        AssetCardEntity existing = getById(entity.getId());
        if (!existing.getStatus().equals("DRAFT") && !existing.getStatus().equals("IN_USE")) {
            // 允许更新
        }
        validateCode(entity.getAssetCode(), entity.getId());
        existing.setAssetCode(entity.getAssetCode());
        existing.setAssetName(entity.getAssetName());
        existing.setCategoryId(entity.getCategoryId());
        existing.setSpec(entity.getSpec());
        existing.setDeptId(entity.getDeptId());
        existing.setCustodianId(entity.getCustodianId());
        existing.setLocation(entity.getLocation());
        existing.setSerialNo(entity.getSerialNo());
        existing.setRemark(entity.getRemark());
        cardMapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        AssetCardEntity card = getById(id);
        if (!"IN_USE".equals(card.getStatus()) && !"IDLE".equals(card.getStatus())) {
            throw new BusinessException("仅可删除在用或闲置状态的资产");
        }
        cardMapper.deleteById(id);
    }

    @Override
    public BigDecimal calculateDepreciation(AssetCardEntity card, String period) {
        if (card.getOriginalValue() == null || card.getUsefulLife() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal original = card.getOriginalValue();
        BigDecimal residual = card.getResidualValue() == null ? BigDecimal.ZERO : card.getResidualValue();
        BigDecimal accumulated = card.getAccumulatedDepreciation() == null
                ? BigDecimal.ZERO : card.getAccumulatedDepreciation();
        BigDecimal depreciableBase = original.subtract(residual).subtract(accumulated);
        if (depreciableBase.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        String method = card.getDepreciationMethod() == null
                ? "STRAIGHT_LINE" : card.getDepreciationMethod();
        BigDecimal amount;
        switch (method) {
            case "DOUBLE_DECLINING":
                // 双倍余额递减法: 年折旧率 = 2/年限, 当期 = 期初账面净值 × 年折旧率 / 12
                BigDecimal rate = BigDecimal.valueOf(2)
                        .divide(BigDecimal.valueOf(card.getUsefulLife()), 6, RoundingMode.HALF_UP);
                amount = card.getNetValue().multiply(rate)
                        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
                // 最后两年改用直线法
                int yearsUsed = accumulated.multiply(BigDecimal.valueOf(12))
                        .divide(original, 0, RoundingMode.DOWN)
                        .divide(BigDecimal.valueOf(card.getUsefulLife()).multiply(BigDecimal.valueOf(12)), 0, RoundingMode.DOWN)
                        .intValue();
                if (card.getUsefulLife() - yearsUsed <= 2) {
                    amount = depreciableBase.divide(BigDecimal.valueOf(2 * 12), 2, RoundingMode.HALF_UP);
                }
                break;
            case "SUM_OF_YEARS":
                // 年数总和法: 年折旧率 = (年限-已使用年限) / (年限×(年限+1)/2)
                int life = card.getUsefulLife();
                int monthsUsed = card.getLastDepreciationPeriod() == null ? 0 :
                        computeMonthsBetween(card.getAcquisitionDate().toString().substring(0, 7), period);
                int yearsDepreciated = monthsUsed / 12;
                int remainingYears = Math.max(life - yearsDepreciated, 1);
                int sumYears = life * (life + 1) / 2;
                BigDecimal yearRate = BigDecimal.valueOf(remainingYears)
                        .divide(BigDecimal.valueOf(sumYears), 6, RoundingMode.HALF_UP);
                amount = depreciableBase.multiply(yearRate)
                        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
                break;
            case "STRAIGHT_LINE":
            default:
                // 平均年限法: 月折旧 = (原值 - 残值 - 已计提) / 剩余月数
                int monthsLeft = card.getUsefulLife() * 12 -
                        (card.getLastDepreciationPeriod() == null ? 0 :
                                computeMonthsBetween(card.getAcquisitionDate().toString().substring(0, 7), period));
                if (monthsLeft <= 0) {
                    amount = depreciableBase;
                } else {
                    amount = depreciableBase.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.HALF_UP);
                }
                break;
        }
        // 最后一期不能超过剩余可折旧额
        if (amount.compareTo(depreciableBase) > 0) {
            amount = depreciableBase;
        }
        return amount.max(BigDecimal.ZERO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void depreciatePeriod(String period) {
        log.info("开始计提折旧, period={}", period);
        // 按类别计提
        List<AssetCategoryEntity> categories = categoryMapper.selectList(null);
        int totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (AssetCategoryEntity category : categories) {
            List<AssetCardEntity> cards = cardMapper.selectToDepreciate(category.getId(), period);
            for (AssetCardEntity card : cards) {
                BigDecimal amount = calculateDepreciation(card, period);
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    // 记录折旧明细
                    AssetDepreciationEntity dep = new AssetDepreciationEntity();
                    dep.setAssetId(card.getId());
                    dep.setPeriod(period);
                    dep.setDepreciationAmount(amount);
                    dep.setAccumulatedDepreciation(card.getAccumulatedDepreciation().add(amount));
                    dep.setNetValue(card.getNetValue().subtract(amount));
                    depreciationMapper.insert(dep);
                    // 更新卡片累计折旧
                    cardMapper.accumulateDepreciation(card.getId(), amount, period);
                    totalCount++;
                    totalAmount = totalAmount.add(amount);
                }
            }
        }
        log.info("折旧完成: {} 笔, 金额={}", totalCount, totalAmount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void depreciateOne(Long assetId, String period) {
        AssetCardEntity card = getById(assetId);
        BigDecimal amount = calculateDepreciation(card, period);
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            AssetDepreciationEntity dep = new AssetDepreciationEntity();
            dep.setAssetId(card.getId());
            dep.setPeriod(period);
            dep.setDepreciationAmount(amount);
            dep.setAccumulatedDepreciation(card.getAccumulatedDepreciation().add(amount));
            dep.setNetValue(card.getNetValue().subtract(amount));
            depreciationMapper.insert(dep);
            cardMapper.accumulateDepreciation(card.getId(), amount, period);
        }
    }

    @Override
    public List<Map<String, Object>> recentCards(int limit) {
        return cardMapper.selectRecent(limit);
    }

    private void validateCode(String code, Long excludeId) {
        LambdaQueryWrapper<AssetCardEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssetCardEntity::getAssetCode, code);
        if (excludeId != null) {
            wrapper.ne(AssetCardEntity::getId, excludeId);
        }
        if (cardMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("资产编码已存在: " + code);
        }
    }

    private int computeMonthsBetween(String from, String to) {
        int fy = Integer.parseInt(from.substring(0, 4));
        int fm = Integer.parseInt(from.substring(5, 7));
        int ty = Integer.parseInt(to.substring(0, 4));
        int tm = Integer.parseInt(to.substring(5, 7));
        return (ty - fy) * 12 + (tm - fm);
    }
}
