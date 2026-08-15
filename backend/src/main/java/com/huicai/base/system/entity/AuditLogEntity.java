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
