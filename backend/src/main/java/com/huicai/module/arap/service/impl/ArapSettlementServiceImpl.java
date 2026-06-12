package com.huicai.module.arap.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import com.huicai.module.arap.entity.ArapSettlementEntryEntity;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.ArapSettlementEntryMapper;
import com.huicai.module.arap.mapper.ArapSettlementMapper;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.arap.service.ArapSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArapSettlementServiceImpl implements ArapSettlementService {

    private final ArapSettlementMapper mapper;
    private final ArapSettlementEntryMapper entryMapper;
    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;

    @Override
    public IPage<ArapSettlementEntity> pageQuery(String status, Integer current, Integer size) {
        Page<ArapSettlementEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<ArapSettlementEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(ArapSettlementEntity::getStatus, status);
        }
        wrapper.orderByDesc(ArapSettlementEntity::getCreatedAt);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public ArapSettlementEntity getById(Long id) {
        ArapSettlementEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("核销单不存在");
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArapSettlementEntity create(ArapSettlementEntity entity, List<ArapSettlementEntryEntity> entries) {
        if (StrUtil.isBlank(entity.getSettlementNo())) {
            String prefix = "RECEIVE".equals(entity.getSettlementType()) ? "JS" : "FS";
            entity.setSettlementNo(prefix + "-" + entity.getPeriod() + "-" + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase());
        }
        if (entity.getStatus() == null) entity.setStatus("DRAFT");
        if (entity.getDiscountAmount() == null) entity.setDiscountAmount(BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;
        for (ArapSettlementEntryEntity entry : entries) {
            total = total.add(entry.getSettledAmount());
        }
        entity.setTotalAmount(total);
        mapper.insert(entity);

        for (ArapSettlementEntryEntity entry : entries) {
            entry.setSettlementId(entity.getId());
            entryMapper.insert(entry);
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArapSettlementEntity confirm(Long id) {
        ArapSettlementEntity entity = getById(id);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可确认");
        }
        // 更新明细对应应收/应付的已核销金额
        List<ArapSettlementEntryEntity> entries = entryMapper.selectList(
                new LambdaQueryWrapper<ArapSettlementEntryEntity>()
                        .eq(ArapSettlementEntryEntity::getSettlementId, id)
        );
        for (ArapSettlementEntryEntity entry : entries) {
            if (entry.getReceivableId() != null) {
                ReceivableEntity r = receivableMapper.selectById(entry.getReceivableId());
                if (r != null) {
                    BigDecimal newSettled = r.getSettledAmount().add(entry.getSettledAmount());
                    r.setSettledAmount(newSettled);
                    r.setUnsettledAmount(r.getAmount().subtract(newSettled));
                    receivableMapper.updateById(r);
                }
            } else if (entry.getPayableId() != null) {
                PayableEntity p = payableMapper.selectById(entry.getPayableId());
                if (p != null) {
                    BigDecimal newSettled = p.getSettledAmount().add(entry.getSettledAmount());
                    p.setSettledAmount(newSettled);
                    p.setUnsettledAmount(p.getAmount().subtract(newSettled));
                    payableMapper.updateById(p);
                }
            }
        }
        entity.setStatus("CONFIRMED");
        mapper.updateById(entity);
        return entity;
    }

    @Override
    public void delete(Long id) {
        ArapSettlementEntity entity = getById(id);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        entryMapper.delete(new LambdaQueryWrapper<ArapSettlementEntryEntity>()
                .eq(ArapSettlementEntryEntity::getSettlementId, id));
        mapper.deleteById(id);
    }
}
