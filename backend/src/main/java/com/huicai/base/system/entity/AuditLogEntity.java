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
    private Long userId;
    @TableField(exist = false)
    private String username;
    private String operation;
    private String method;
    @TableField(typeHandler = JsonbTypeHandler.class)
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
