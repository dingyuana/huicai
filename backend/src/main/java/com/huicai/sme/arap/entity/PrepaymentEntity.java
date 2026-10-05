package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预付款/预收款实体 — 供应商预付或客户预收.
 * 注意：不继承 BaseEntity，因为 createdBy(String)/createdAt(LocalDate)/updatedAt(LocalDate) 类型与基类不兼容。
 *
 * <p><b>2026-10-05（P106 / AGENTS §4.5 第 34 条）</b>：因不继承 BaseEntity 且本类原先
 * <b>全类没有任何 {@code @TableField}</b> ⇒ MyBatis-Plus 的 {@code TableInfo.withInsertFill}
 * 恒为 {@code false} ⇒ {@code MyMetaObjectHandler.insertFill} <b>根本不被调用</b>
 * （不是「调用了但没填」，是「压根没进」）⇒ {@code enterpriseId} 永不被上下文覆盖；
 * 而 {@code t_prepayment.enterprise_id} 是 V105 加的 {@code NOT NULL DEFAULT 1}
 * ⇒ <b>任何非企业 1 的上下文创建的预付款都静默落进企业 1</b>。
 *
 * <p>因此 {@code enterpriseId} 必须显式声明 {@code FieldFill.INSERT}：只要有任意一个字段
 * 声明了 fill，{@code withInsertFill} 即为 true，元对象回调才会被触发。
 * <b>不能改用「继承 BaseEntity」</b> —— 已用真实库核对列类型：
 * {@code created_at}/{@code updated_at} 是 {@code date}（基类是 {@code LocalDateTime}）、
 * {@code created_by} 是 {@code varchar(50)}（基类是 {@code Long}）⇒ 继承会引入 3 处类型不匹配。
 */
@Data
@TableName("t_prepayment")
public class PrepaymentEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 由 {@code MyMetaObjectHandler} 无条件覆盖为当前企业上下文（铁律：禁止客户端指定） */
    @TableField(fill = FieldFill.INSERT)
    private Long enterpriseId;

    @TableLogic
    private Integer deleted;

    private Long vendorId;

    private Long customerId;

    private Long docId;

    private Long voucherId;

    private String period;

    private LocalDate txDate;

    /** 预付款金额 */
    private BigDecimal amount;

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 未核销金额 */
    private BigDecimal unsettledAmount;

    /** 摘要 */
    private String summary;

    /** 状态: DRAFT / SUBMITTED / AUDITED / POSTED */
    @StatusChangeable(entity = "PREPAYMENT", fieldName = "status")
    private String status;

    /** 关联单据类型 (如 bank_txn) */
    private String sourceDocType;

    /** 关联单据 ID */
    private Long sourceDocId;

    /** 备注 */
    private String remark;

    private String createdBy;

    private LocalDate createdAt;

    private LocalDate updatedAt;
}
