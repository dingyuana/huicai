package com.huicai.agency.dashboard.listener;

import com.huicai.agency.dashboard.service.ServiceProgressService;
import com.huicai.common.event.ServiceProgressStageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 代理服务进度节点事件监听器（P79 P1 事件埋点的收口）
 * <p>
 * 监听 4 个下层业务模块（发票导入/凭证过账/结账/申报）发布的
 * {@link ServiceProgressStageEvent}，调用 {@code ServiceProgressService.advanceStage}
 * 推进 t_service_progress 节点。
 * <p>
 * 设计要点（铁律#1：进度是旁路，绝不拖垮业务）：
 * <ul>
 *   <li><b>agencyId 降级</b>：纯 SME 用户（LoginUser.agencyId=null）不推进，debug 日志跳过</li>
 *   <li><b>吞异常</b>：advanceStage 任何异常只 log.error，不上抛——进度推进失败不影响业务事务</li>
 *   <li><b>同事务语义</b>：{@code @EventListener} 同步执行。业务事务回滚则本节点推进一并回滚
 *       （业务没成功，进度不该推进），语义正确</li>
 * </ul>
 * 分层：本类在 agency（上层）监听 common.event（最下层）发布的 POJO 事件，
 * 下层事件源无需 import 任何 agency 类，无反向依赖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceProgressStageEventListener {

    private final ServiceProgressService progressService;

    @EventListener
    public void onStageEvent(ServiceProgressStageEvent e) {
        try {
            // 纯 SME 用户（非代账场景）agencyId 为空 → 不推进，降级
            if (e.agencyId() == null) {
                log.debug("P79 进度事件跳过：agencyId 为空（纯 SME 用户），stage={}", e.stage());
                return;
            }
            if (e.enterpriseId() == null || e.period() == null || e.stage() == null) {
                log.warn("P79 进度事件字段缺失，跳过：agency={} enterprise={} period={} stage={}",
                        e.agencyId(), e.enterpriseId(), e.period(), e.stage());
                return;
            }
            boolean changed = progressService.advanceStage(e.agencyId(), e.enterpriseId(), e.period(), e.stage());
            if (changed) {
                log.info("P79 节点推进：agency={} enterprise={} period={} {} → DONE",
                        e.agencyId(), e.enterpriseId(), e.period(), e.stage());
            } else {
                log.debug("P79 节点事件无变更（已 DONE 幂等 或 乱序忽略）：stage={} period={}", e.stage(), e.period());
            }
        } catch (Exception ex) {
            // 进度推进绝不拖垮业务（铁律）：吞掉所有异常，仅告警
            log.error("P79 节点推进失败（不影响业务事务）：agency={} enterprise={} period={} stage={}",
                    e.agencyId(), e.enterpriseId(), e.period(), e.stage(), ex);
        }
    }
}
