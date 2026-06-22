package com.huicai.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.module.system.entity.AuditLogEntity;
import com.huicai.module.system.mapper.AuditLogMapper;
import com.huicai.module.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public IPage<AuditLogEntity> pageLog(long page, long size, String module, String status,
                                         String startDate, String endDate) {
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(module)) {
            wrapper.eq(AuditLogEntity::getModule, module);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AuditLogEntity::getStatus, status);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(AuditLogEntity::getCreatedAt, startDate);
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(AuditLogEntity::getCreatedAt, endDate + " 23:59:59");
        }
        wrapper.orderByDesc(AuditLogEntity::getCreatedAt);
        return auditLogMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public AuditLogEntity getById(Long id) {
        return auditLogMapper.selectById(id);
    }

    @Override
    @Async
    public void saveAsync(AuditLogEntity auditLog) {
        auditLogMapper.insert(auditLog);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordStatusChange(String entityType, Long entityId,
                                   String fieldName, String oldValue, String newValue) {
        AuditLogEntity log = new AuditLogEntity();
        log.setModule(entityType);
        log.setOperation("STATUS_CHANGE");
        log.setMethod(entityType + ".updateStatus");
        log.setRequestParams("entityId=" + entityId + ", field=" + fieldName);
        log.setResponseResult("newValue=" + newValue);
        log.setOldSnapshot("{\"" + fieldName + "\":\"" + (oldValue == null ? "" : oldValue) + "\"}");
        log.setNewSnapshot("{\"" + fieldName + "\":\"" + (newValue == null ? "" : newValue) + "\"}");
        log.setStatus("SUCCESS");
        auditLogMapper.insert(log);
        log.info("状态变更审计: entity={}, id={}, field={}, {} → {}",
                entityType, entityId, fieldName, oldValue, newValue);
    }
}
