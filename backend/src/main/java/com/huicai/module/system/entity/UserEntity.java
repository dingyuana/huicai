package com.huicai.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
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
    private Integer deleted;

    @TableField(exist = false)
    private List<Long> roleIds;

    @TableField(exist = false)
    private String deptName;
}
