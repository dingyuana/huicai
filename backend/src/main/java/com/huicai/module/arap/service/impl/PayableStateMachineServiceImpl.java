package com.huicai.module.arap.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.service.PayableStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 应付单状态机实现.
 *
 * <p>依据 SPEC docs/specs/P20-arap-state-machine-spec.md
 * 状态变更通过 BaseMapper.updateById 写入数据库，
 * StatusChangeAspect 自动拦截并写入 t_audit_log。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayableStateMachineServiceImpl implements PayableStateMachineService {

    private final PayableMapper payableMapper;

    @Override
    @Transactional
    public void confirm(Long payableId, Long userId) {
        PayableEntity entity = getEntity(payableId);
        if (!ArapStatus.isDraft(entity.getStatus())) {
            throw BusinessException.badRequest("仅草稿状态可确认，当前: " + entity.getStatus());
        }
        entity.setStatus(ArapStatus.CONFIRMED);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        payableMapper.updateById(entity);
        log.info("应付单确认: id={}, userId={}", payableId, userId);
    }

    @Override
    @Transactional
    public void onReconciliationUpdate(Long payableId, BigDecimal unsettledAmount, Long userId) {
        PayableEntity entity = getEntity(payableId);
        if (!ArapStatus.isConfirmed(entity.getStatus()) && !ArapStatus.isSettled(entity.getStatus())) {
            throw BusinessException.badRequest("仅已确认/已结清状态可更新核销状态，当前: " + entity.getStatus());
        }
        // 更新未结清金额
        entity.setUnsettledAmount(unsettledAmount);
        // 更新状态
        String newStatus = unsettledAmount.compareTo(BigDecimal.ZERO) == 0
                ? ArapStatus.SETTLED
                : ArapStatus.CONFIRMED;
        entity.setStatus(newStatus);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        payableMapper.updateById(entity);
        log.info("应付单核销更新: id={}, status={}, unsettledAmount={}", payableId, newStatus, unsettledAmount);
    }

    @Override
    @Transactional
    public void reverse(Long payableId, Long userId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw BusinessException.badRequest("冲销必须填写原因");
        }
        PayableEntity entity = getEntity(payableId);
        if (!ArapStatus.isReversible(entity.getStatus())) {
            throw BusinessException.badRequest("当前状态不可冲销: " + entity.getStatus());
        }
        entity.setStatus(ArapStatus.REVERSED);
        // 追加冲销原因到备注
        String newRemark = entity.getRemark() == null ? "" : entity.getRemark() + " | ";
        newRemark += "[" + userId + "] 冲销原因: " + reason;
        entity.setRemark(newRemark);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        payableMapper.updateById(entity);
        log.info("应付单冲销: id={}, userId={}, reason={}", payableId, userId, reason);
    }

    private PayableEntity getEntity(Long id) {
        PayableEntity entity = payableMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("应付单不存在: id=" + id);
        }
        return entity;
    }
}
