package com.huicai.agency.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会计-客户分配关系实体 — t_agency_user_enterprise
 */
@Data
@TableName("t_agency_user_enterprise")
public class AgencyUserEnterpriseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agencyUserId;
    private Long enterpriseId;
    private Long assignedBy;
    private LocalDateTime assignedAt;
    private Long unassignedBy;
    private LocalDateTime unassignedAt;

    /**
     * 🔴 逻辑删除字段：必须加 @TableLogic，否则 MyBatis-Plus 全局配置的
     * logic-delete-field=deleted 会导致 updateById 跳过该字段（不生成 UPDATE SET deleted=?），
     * 取消分配后记录仍出现在列表中。
     */
    @TableLogic
    private Integer deleted;
}
