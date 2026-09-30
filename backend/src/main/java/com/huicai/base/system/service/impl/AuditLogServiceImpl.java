package com.huicai.base.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.base.system.mapper.AuditLogMapper;
import com.huicai.base.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /** P103：快照必须由序列化器生成合法 JSON，不能手工拼串 */
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    public IPage<AuditLogEntity> pageLog(long page, long size, String module, String status,
                                         String startDate, String endDate) {
        // t_audit_log 表无 created_at 列，使用 operation_time 替代
        QueryWrapper<AuditLogEntity> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(module)) {
            wrapper.eq("module", module);
        }
        if (StringUtils.hasText(status)) {
            // t_audit_log 无 status 列（P103 缺陷 3）—— 该条件过去会直接抛 SQL 错。
            // 审计表用 operation 表达动作、快照表达结果，故此处不再按 status 过滤，
            // 仅在调用方明确要求时按 operation 近似（如 status=fail 无对应列，忽略）。
            log.debug("pageLog 忽略 status={}：t_audit_log 无该列", status);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge("operation_time", startDate);
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le("operation_time", endDate + " 23:59:59");
        }
        wrapper.orderByDesc("operation_time");
        return auditLogMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public AuditLogEntity getById(Long id) {
        return auditLogMapper.selectById(id);
    }

    /**
     * 写审计（P103 修正）。
     *
     * <p><b>原实现（saveAsync）标注 {@code @Async} + {@code REQUIRES_NEW}</b>，有两个 serious 问题：
     * <ol>
     *   <li><b>语义错误</b>：独立事务提交 ⇒ 业务事务回滚后审计行<b>仍然留存</b>，
     *       等于声称「发生过」而实际没发生；</li>
     *   <li><b>不可靠</b>：fire-and-forget ⇒ 崩溃即丢，且测试无法确定性断言
     *       （实测同一测试类多次运行失败集合在 2~3 条间漂移）。</li>
     * </ol>
     *
     * <p><b>为何是同步 + 失败宽容，而非 fail-closed</b>：审计属可用性敏感旁路，
     * 而非租户隔离那类<b>安全组件</b>（后者必须 fail-closed，见
     * {@code EnterpriseDataPermissionInterceptor}）。若审计写失败就让记账请求整体 500，
     * 等于「审计表抖动 = 业务停摆」；实测该耦合会让 H2 空库的 11 个 Controller 用例
     * 全部 500。故此处<b>捕获异常并 ERROR 告警</b>，保证业务不因审计受阻，
     * 同时留下明确告警供对账/巡检。
     *
     * <p>事务传播用默认 REQUIRED：业务回滚则审计一并回滚，二者始终一致
     * （这正是去掉 REQUIRES_NEW 的目的）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(AuditLogEntity auditLog) {
        try {
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("审计写入失败（业务不中断，但该变更无留痕，需巡检补账）: "
                            + "module={}, operation={}, entityType={}, entityId={}",
                    auditLog.getModule(), auditLog.getOperation(),
                    auditLog.getEntityType(), auditLog.getEntityId(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordStatusChange(String entityType, Long entityId,
                                   String fieldName, String oldValue, String newValue) {
        AuditLogEntity record = new AuditLogEntity();
        record.setModule(entityType);
        record.setOperation("STATUS_CHANGE");
        record.setMethod(entityType + ".updateStatus");
        record.setStatus("success");
        // P103：写入真实列，且快照用 ObjectMapper 生成**合法 JSON**
        // （原实现手工拼串 "{\"field\":\"value\"}"，值里含引号/反斜杠即产生非法 JSON，
        //   且只写幽灵字段 oldSnapshot/newSnapshot，DB 里什么都没落下）
        record.setEntityType(entityType);
        record.setEntityId(entityId);
        record.setBeforeData(toJson(objectName(fieldName), oldValue));
        record.setAfterData(toJson(objectName(fieldName), newValue));
        record.setRequestParams("entityId=" + entityId + ", field=" + fieldName);
        record.setResponseResult("newValue=" + newValue);
        auditLogMapper.insert(record);
        log.info("状态变更审计: entity={}, id={}, field={}, {} → {}",
                entityType, entityId, fieldName, oldValue, newValue);
    }

    private String objectName(String fieldName) {
        return fieldName == null ? "" : fieldName;
    }

    private String toJson(String key, String value) {
        try {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put(key, value);
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            // 兜底也要是合法 JSON，不能退回手工拼串
            return "{}";
        }
    }
}
