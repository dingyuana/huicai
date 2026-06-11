package com.huicai.module.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.system.entity.AuditLogEntity;

public interface AuditLogService {
    IPage<AuditLogEntity> pageLog(long page, long size, String module, String status, String startDate, String endDate);
    AuditLogEntity getById(Long id);
    void saveAsync(AuditLogEntity auditLog);
}
