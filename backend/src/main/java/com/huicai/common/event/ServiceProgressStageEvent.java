package com.huicai.common.event;

import java.time.Instant;

/**
 * 代理服务进度节点事件（P79 P1 事件埋点）
 * <p>
 * 事件源在下层（base/sme 的发票/凭证/结账/税务服务），进度推进服务在上层
 * （{@code com.huicai.agency.dashboard}）。下层不 import 上层（分层约束），
 * 故用本 POJO 事件经 {@code ApplicationEventPublisher} 解耦：
 * <ul>
 *   <li>事件源：业务完成点 {@code publisher.publishEvent(new ServiceProgressStageEvent(...))}</li>
 *   <li>agency 监听器：{@code @EventListener} 接收后调 {@code ServiceProgressService.advanceStage}</li>
 * </ul>
 * 监听器 try-catch 吞异常 + agencyId=null 降级，保证"进度推进绝不拖垮业务"。
 *
 * @param agencyId     代账公司（租户）；纯 SME 用户为 null → 监听器跳过不推进
 * @param enterpriseId 被服务客户企业（SecurityContext 当前企业）
 * @param period       服务期间 YYYYMM
 * @param stage        目标节点 INTAKE/BOOKING/REVIEW/FILING（DONE 不单独发）
 * @param eventTime    事件发生时间（epoch millis，审计用）
 */
public record ServiceProgressStageEvent(
        Long agencyId,
        Long enterpriseId,
        String period,
        String stage,
        long eventTime
) {
    public ServiceProgressStageEvent(Long agencyId, Long enterpriseId, String period, String stage) {
        this(agencyId, enterpriseId, period, stage, System.currentTimeMillis());
    }

    public static final String STAGE_INTAKE = "INTAKE";
    public static final String STAGE_BOOKING = "BOOKING";
    public static final String STAGE_REVIEW = "REVIEW";
    public static final String STAGE_FILING = "FILING";
}
