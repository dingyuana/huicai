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

    @TableField(exist = false)
    private String method;
    @TableField(typeHandler = JsonbTypeHandler.class, exist = false)
    private String requestParams;
    @TableField(typeHandler = JsonbTypeHandler.class, exist = false)
    private String responseResult;
    @TableField(typeHandler = JsonbTypeHandler.class, exist = false)
    private String oldSnapshot;
    @TableField(typeHandler = JsonbTypeHandler.class, exist = false)
    private String newSnapshot;
    private String ipAddress;
    @TableField(exist = false)
    private String userAgent;
    @TableField(exist = false)
    private Integer executionTimeMs;
    @TableField(exist = false)
    private String status;
    private String module;
    @TableField(exist = false)
    private LocalDateTime createdAt;
}
