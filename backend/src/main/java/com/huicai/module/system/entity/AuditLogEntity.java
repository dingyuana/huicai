package com.huicai.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.module.system.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_audit_log")
public class AuditLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String operation;
    private String method;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String requestParams;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String responseResult;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String oldSnapshot;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String newSnapshot;
    private String ipAddress;
    private String userAgent;
    private Integer executionTimeMs;
    private String status;
    private String module;
    private LocalDateTime createdAt;
}
