package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.base.system.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_audit_log")
public class AuditLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 操作人ID — 映射 t_audit_log.operator_id（P58 修复审计操作人落库） */
    @TableField(value = "operator_id")
    private Long userId;
    /** 操作人名称 — 映射 t_audit_log.operator_name（P58 修复审计操作人落库） */
    @TableField(value = "operator_name")
    private String username;
    private String operation;

    // ===== P103 新增：以下 5 个属性对应 t_audit_log 的真实列 =====
    // 修复前实体完全没有它们，导致 idx_audit_log_entity 索引永久失效、
    // 审计行无法回溯到具体业务对象（铁律 #5「编号关联溯源」名存实亡）。

    /** 业务对象类型（实体名），对应 entity_type —— idx_audit_log_entity 首列 */
    @TableField("entity_type")
    private String entityType;

    /** 业务对象主键，对应 entity_id */
    @TableField("entity_id")
    private Long entityId;

    /** 业务对象编号（人类可读），对应 entity_no */
    @TableField("entity_no")
    private String entityNo;

    /**
     * 变更前全量快照 JSON，对应 {@code before_data JSONB}。
     * 修复前只有幽灵字段 {@code oldSnapshot}（{@code exist=false}，完全不参与 SQL），
     * 致使开发库 189 行审计记录的 before_data 非空计数为 <b>0</b>。
     */
    @TableField(value = "before_data", typeHandler = JsonbTypeHandler.class)
    private String beforeData;

    /** 变更后全量快照 JSON，对应 {@code after_data JSONB} */
    @TableField(value = "after_data", typeHandler = JsonbTypeHandler.class)
    private String afterData;

    /** 目标方法名，对应 method（SPEC-P152 补列，此前是 exist=false 幽灵字段） */
    @TableField("method")
    private String method;

    /**
     * 请求参数（LogAspect 已脱敏），对应 request_params TEXT。
     * <p>刻意不用 JSONB：现存写方（recordStatusChange 的 "newValue=..." 拼串、
     * 异常 message）不是合法 JSON，JSONB 列会整行插入失败。</p>
     */
    @TableField("request_params")
    private String requestParams;

    /** 响应结果或异常 message，对应 response_result TEXT */
    @TableField("response_result")
    private String responseResult;

    private String ipAddress;

    /** 客户端 UA，对应 user_agent */
    @TableField("user_agent")
    private String userAgent;

    /** 执行耗时毫秒，对应 execution_time_ms */
    @TableField("execution_time_ms")
    private Integer executionTimeMs;

    /** 操作结果 success/fail，对应 status（页端过滤列） */
    @TableField("status")
    private String status;

    private String module;

    /**
     * 创建时间 — 复用 operation_time 列（表无独立 created_at；SPEC-P152 由
     * exist=false 转正，页端「操作时间」列与详情弹窗直接可用）。
     * 插入时留 null 走 DB 默认值 CURRENT_TIMESTAMP。
     */
    @TableField("operation_time")
    private LocalDateTime createdAt;
}
