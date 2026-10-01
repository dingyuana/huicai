package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_user")
public class UserEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String realName;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Long deptId;
    private String status;
    private String remark;
    private String lastLoginIp;
    private LocalDateTime lastLoginAt;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * P104 第 3 批补齐：原先<b>缺少 {@code @TableLogic}</b>，而 {@code t_user}
     * 确实有 {@code deleted} 列 ⇒ {@code deleteById} 执行的是<b>物理删除</b>。
     *
     * <p>危害：用户是审计主体（P103 的 {@code t_audit_log.operator_id} 指向它），
     * 物理删除后其历史操作记录无法回溯到「谁」，铁律 #12 的逻辑删除要求被绕过。
     * 本仓其余同批实体（{@code DeptEntity} / {@code RoleEntity} / {@code MenuEntity}
     * 等）均已声明，故属遗漏而非有意设计。
     *
     * <p>已确认全仓无生产代码调用 {@code userMapper.deleteById}，改为逻辑删除零风险。
     */
    @TableLogic
    private Integer deleted;

    // S-26: 多租户字段
    private String userType;
    private Long agencyId;
    private Long enterpriseId;
    private String agencyRole;

    @TableField(exist = false)
    private List<Long> roleIds;

    @TableField(exist = false)
    private String deptName;
}
