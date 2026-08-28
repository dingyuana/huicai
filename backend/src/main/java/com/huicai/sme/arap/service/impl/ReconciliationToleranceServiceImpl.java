package com.huicai.sme.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.dto.ReconciliationToleranceDTO;
import com.huicai.sme.arap.dto.vo.ReconciliationToleranceVO;
import com.huicai.sme.arap.entity.ReconciliationToleranceEntity;
import com.huicai.sme.arap.mapper.ReconciliationToleranceMapper;
import com.huicai.sme.arap.service.ReconciliationToleranceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationToleranceServiceImpl implements ReconciliationToleranceService {

    private static final long DEFAULT_ENTERPRISE_ID = 1L;
    private static final BigDecimal DEFAULT_TOLERANCE_VALUE = new BigDecimal("5.00");
    private static final BigDecimal DEFAULT_TOLERANCE_RATE = new BigDecimal("10.00");

    private final ReconciliationToleranceMapper toleranceMapper;

    /**
     * 按 party 或全局找生效容差。DB 表实际列：
     * party_type / party_id / tolerance_type(ABSOLUTE|PERCENT) / tolerance_value。
     * 映射：ABSOLUTE→toleranceAmount, PERCENT→toleranceRate。
     */
    @Override
    public ReconciliationToleranceEntity getTolerance(Long partyId, String partyType) {
        ReconciliationToleranceEntity entity = toleranceMapper.findTolerance(
                DEFAULT_ENTERPRISE_ID, partyId, partyType);

        if (entity == null) {
            entity = new ReconciliationToleranceEntity();
            entity.setToleranceValue(DEFAULT_TOLERANCE_VALUE);
            entity.setToleranceType("ABSOLUTE");
        }
        entity.setToleranceAmount("ABSOLUTE".equals(entity.getToleranceType())
                ? entity.getToleranceValue() : null);
        entity.setToleranceRate("PERCENT".equals(entity.getToleranceType())
                ? entity.getToleranceValue() : null);
        return entity;
    }

    @Override
    public BigDecimal getToleranceAmount(Long partyId, String partyType) {
        ReconciliationToleranceEntity e = getTolerance(partyId, partyType);
        return e.getToleranceAmount() != null ? e.getToleranceAmount() : DEFAULT_TOLERANCE_VALUE;
    }

    @Override
    public BigDecimal getToleranceRate(Long partyId, String partyType) {
        ReconciliationToleranceEntity e = getTolerance(partyId, partyType);
        return e.getToleranceRate() != null ? e.getToleranceRate() : DEFAULT_TOLERANCE_RATE;
    }

    @Override
    public ReconciliationToleranceVO getDefaultConfig() {
        LambdaQueryWrapper<ReconciliationToleranceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReconciliationToleranceEntity::getEnterpriseId, DEFAULT_ENTERPRISE_ID)
                .isNull(ReconciliationToleranceEntity::getPartyId)
                .eq(ReconciliationToleranceEntity::getIsActive, true)
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
        wrapper.eq(ReconciliationToleranceEntity::getEnterpriseId, DEFAULT_ENTERPRISE_ID)
                .eq(ReconciliationToleranceEntity::getPartyId, partyId)
                .eq(ReconciliationToleranceEntity::getPartyType, partyType)
                .eq(ReconciliationToleranceEntity::getIsActive, true)
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
        entity.setEnterpriseId(DEFAULT_ENTERPRISE_ID);
        entity.setPartyId(dto.getPartyId());
        entity.setPartyType(dto.getPartyType());
        entity.setToleranceValue(dto.getToleranceAmount() != null
                ? dto.getToleranceAmount() : DEFAULT_TOLERANCE_VALUE);
        entity.setToleranceType("ABSOLUTE");
        entity.setIsActive(true);
        entity.setDeleted(0);

        toleranceMapper.insert(entity);
        log.info("创建容差配置: id={}, partyId={}, partyType={}",
                entity.getId(), entity.getPartyId(), entity.getPartyType());
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
            entity.setToleranceValue(dto.getToleranceAmount());
        }
        if (dto.getToleranceRate() != null) {
            entity.setToleranceValue(dto.getToleranceRate());
            entity.setToleranceType("PERCENT");
        }
        if (dto.getPartyId() != null) {
            entity.setPartyId(dto.getPartyId());
        }
        if (dto.getPartyType() != null) {
            entity.setPartyType(dto.getPartyType());
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
        vo.setToleranceAmount("ABSOLUTE".equals(entity.getToleranceType())
                ? entity.getToleranceValue() : null);
        vo.setToleranceRate("PERCENT".equals(entity.getToleranceType())
                ? entity.getToleranceValue() : null);
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
