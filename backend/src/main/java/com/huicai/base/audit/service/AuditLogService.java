package com.huicai.base.audit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.audit.entity.AuditLogEntity;

public interface AuditLogService {
    IPage<AuditLogEntity> pageLog(long page, long size, String module, String status, String startDate, String endDate);
    AuditLogEntity getById(Long id);
    void saveAsync(AuditLogEntity auditLog);

    /**
     * 记录状态变更审计日志（同步写入，与业务同事务）.
     *
     * @param entityType  实体类型（如 OUTPUT_INVOICE / VOUCHER）
     * @param entityId    实体 ID
     * @param fieldName   变更字段名
     * @param oldValue    变更前值
     * @param newValue    变更后值
     */
    void recordStatusChange(String entityType, Long entityId,
                            String fieldName, String oldValue, String newValue);
}
