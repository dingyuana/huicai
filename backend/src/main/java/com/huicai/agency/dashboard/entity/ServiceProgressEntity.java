package com.huicai.agency.dashboard.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 代理服务进度跟踪（P79）
 * <p>
 * agency 跨客户维护：{@code agency_id} 为租户（代账公司）隔离维度，
 * {@code enterprise_id}（继承自 BaseEntity）存被服务的客户企业。
 * 节点化服务流程 INTAKE→BOOKING→REVIEW→FILING→DONE，事件驱动只进不退。
 * <p>
 * 数据权限：本表列入 SHARED_TABLES（同 t_agency_user），查询手动过滤 agency_id，
 * 由 EnterpriseDataPermissionInterceptor 跳过自动注入（避免破坏跨客户查询）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_service_progress")
public class ServiceProgressEntity extends BaseEntity {

    /** 代账公司（租户）ID */
    private Long agencyId;

    /** 服务期间 YYYYMM */
    private String period;

    /** 节点：INTAKE/BOOKING/REVIEW/FILING/DONE */
    private String stage;

    /** 状态：PENDING/IN_PROGRESS/DONE */
    private String status;

    /** 经办代理用户 ID（t_agency_user） */
    private Long assignedTo;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    /** 申报期限/合同 SLA（V1 手工填写，超期预警基准） */
    private LocalDate dueDate;

    /** 超期提醒已发送时间（幂等） */
    private LocalDateTime overtimeNotifiedAt;

    /** force-done 备注/审计说明 */
    private String remark;
}
