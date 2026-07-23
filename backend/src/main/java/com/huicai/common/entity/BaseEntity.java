package com.huicai.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体基类 — 统一审计字段 + 逻辑删除 + 乐观锁 + 多租户
 * <p>
 * 所有业务 Entity 继承此类，避免重复定义审计字段。
 * 注意：createdBy/updatedBy 不加 @TableField(fill=...) 注解，
 * 由 Service 层手动 set，保持与现有代码兼容。
 * </p>
 */
@Data
public abstract class BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 多租户企业 ID（S-26 Agency 分支新增） */
    private Long enterpriseId;

    /** 创建人 */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人 */
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除：0=正常，1=删除 */
    @TableLogic
    private Integer deleted;

    /** 乐观锁版本号 — 默认不持久化，仅在需要乐观锁的 Entity 中单独声明 @Version */
    @TableField(exist = false)
    private Integer version;
}
