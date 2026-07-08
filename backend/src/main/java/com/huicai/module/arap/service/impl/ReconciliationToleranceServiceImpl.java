package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.dto.ReconciliationToleranceDTO;
import com.huicai.module.arap.dto.vo.ReconciliationToleranceVO;
import com.huicai.module.arap.entity.ReconciliationToleranceEntity;
import com.huicai.module.arap.mapper.ReconciliationToleranceMapper;
import com.huicai.module.arap.service.ReconciliationToleranceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationToleranceServiceImpl implements ReconciliationToleranceService {

    private static final long DEFAULT_TENANT_ID = 1L;
    private static final BigDecimal DEFAULT_TOLERANCE_AMOUNT = new BigDecimal("5.00");
    private static final BigDecimal DEFAULT_TOLERANCE_RATE = new BigDecimal("10.00");

    private final ReconciliationToleranceMapper toleranceMapper;

    @Override
    public ReconciliationToleranceEntity getTolerance(Long partyId, String partyType) {
        ReconciliationToleranceEntity entity = toleranceMapper.findTolerance(
                DEFAULT_TENANT_ID, partyId, partyType, LocalDate.now());
        
        if (entity == null) {
            entity = new ReconciliationToleranceEntity();
            entity.setToleranceAmount(DEFAULT_TOLERANCE_AMOUNT);
            entity.setToleranceRate(DEFAULT_TOLERANCE_RATE);
        }
        return entity;
    }

    @Override
    public BigDecimal getToleranceAmount(Long partyId, String partyType) {
        return getTolerance(partyId, partyType).getToleranceAmount();
    }

    @Override
    public BigDecimal getToleranceRate(Long partyId, String partyType) {
        return getTolerance(partyId, partyType).getToleranceRate();
    }

    @Override
    public ReconciliationToleranceVO getDefaultConfig() {
        LambdaQueryWrapper<ReconciliationToleranceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReconciliationToleranceEntity::getTenantId, DEFAULT_TENANT_ID)
               .isNull(ReconciliationToleranceEntity::getPartyId)
               .eq(ReconciliationToleranceEntity::getDeleted, 0);
        
        ReconciliationToleranceEntity entity = toleranceMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("未找到全局容差配置");
        }
        return convertToVO(entity);
    }

    @Override
    public ReconciliationToleranceVO getByParty(Long partyId, String partyType) {
        LambdaQueryWrapper<ReconciliationToleranceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReconciliationToleranceEntity::getTenantId, DEFAULT_TENANT_ID)
               .eq(ReconciliationToleranceEntity::getPartyId, partyId)
               .eq(ReconciliationToleranceEntity::getPartyType, partyType)
               .eq(ReconciliationToleranceEntity::getDeleted, 0);
        
        ReconciliationToleranceEntity entity = toleranceMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("未找到容差配置");
        }
        return convertToVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconciliationToleranceVO create(ReconciliationToleranceDTO dto) {
        ReconciliationToleranceEntity entity = new ReconciliationToleranceEntity();
        entity.setTenantId(DEFAULT_TENANT_ID);
        entity.setPartyId(dto.getPartyId());
        entity.setPartyType(dto.getPartyType());
        entity.setToleranceAmount(dto.getToleranceAmount() != null ? dto.getToleranceAmount() : DEFAULT_TOLERANCE_AMOUNT);
        entity.setToleranceRate(dto.getToleranceRate() != null ? dto.getToleranceRate() : DEFAULT_TOLERANCE_RATE);
        entity.setEffectiveFrom(dto.getEffectiveFrom() != null ? dto.getEffectiveFrom() : LocalDate.now());
        entity.setEffectiveTo(dto.getEffectiveTo());
        entity.setDeleted(0);
        
        toleranceMapper.insert(entity);
        log.info("创建容差配置: id={}, partyId={}, partyType={}", entity.getId(), entity.getPartyId(), entity.getPartyType());
        return convertToVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconciliationToleranceVO update(Long id, ReconciliationToleranceDTO dto) {
        ReconciliationToleranceEntity entity = toleranceMapper.selectById(id);
        if (entity == null || entity.getDeleted() != 0) {
            throw new BusinessException("容差配置不存在");
        }

        if (dto.getToleranceAmount() != null) {
            entity.setToleranceAmount(dto.getToleranceAmount());
        }
        if (dto.getToleranceRate() != null) {
            entity.setToleranceRate(dto.getToleranceRate());
        }
        if (dto.getEffectiveFrom() != null) {
            entity.setEffectiveFrom(dto.getEffectiveFrom());
        }
        if (dto.getEffectiveTo() != null) {
            entity.setEffectiveTo(dto.getEffectiveTo());
        }

        toleranceMapper.updateById(entity);
        log.info("更新容差配置: id={}", id);
        return convertToVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ReconciliationToleranceEntity entity = toleranceMapper.selectById(id);
        if (entity == null || entity.getDeleted() != 0) {
            throw new BusinessException("容差配置不存在");
        }
        if (entity.getPartyId() == null) {
            throw new BusinessException("全局配置不允许删除");
        }

        entity.setDeleted(1);
        toleranceMapper.updateById(entity);
        log.info("删除容差配置: id={}", id);
    }

    private ReconciliationToleranceVO convertToVO(ReconciliationToleranceEntity entity) {
        ReconciliationToleranceVO vo = new ReconciliationToleranceVO();
        vo.setId(entity.getId());
        vo.setPartyId(entity.getPartyId());
        vo.setPartyType(entity.getPartyType());
        vo.setToleranceAmount(entity.getToleranceAmount());
        vo.setToleranceRate(entity.getToleranceRate());
        vo.setEffectiveFrom(entity.getEffectiveFrom());
        vo.setEffectiveTo(entity.getEffectiveTo());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
