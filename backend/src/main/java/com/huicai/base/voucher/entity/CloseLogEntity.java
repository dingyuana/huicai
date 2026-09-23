package com.huicai.base.voucher.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 期末结账日志实体 — 记录每次结账相关动作（检查/生成/审核记账/结账/反结账）
 * <p>
 * P85：替代 {@code listCloseLog()} 的空实现。审计用途，只追加不改写。
 * <p>
 * 注意：不依赖 BaseEntity.createdBy（该字段带 {@code @TableField(exist=false)}，
 * 写了也不会落库），操作者单独用 {@link #operatorId} 列持久化。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_close_log")
public class CloseLogEntity extends BaseEntity {

    /** 会计期间(YYYYMM) */
    private String period;

    /**
     * 动作类型
     * CHECK-结账前检查 / GENERATE-生成结转凭证 / REVIEW_POST-一键审核记账 /
     * CLOSE-期末关账 / REOPEN-反结账
     */
    private String action;

    /** 操作者 ID */
    private Long operatorId;

    /** 结果: success / fail */
    private String result;

    /** 明细（成功时的凭证号清单 / 失败时的异常消息，JSON 或纯文本） */
    private String detail;

    /** 耗时(毫秒) */
    private Long durationMs;
}
