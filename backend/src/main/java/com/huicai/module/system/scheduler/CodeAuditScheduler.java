package com.huicai.module.system.scheduler;

import com.huicai.module.system.service.CodeAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日代码审核定时任务
 * 触发时间: 每天早晨 09:00 (cron: 0 0 9 * * ?)
 * 这是项目的第一个定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeAuditScheduler {

    private final CodeAuditService codeAuditService;

    /**
     * 每天早晨 09:00 自动执行一次代码审核
     * cron 表达式: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "${huicai.code-audit.cron:0 0 9 * * ?}")
    public void runDailyAudit() {
        log.info("[CodeAuditScheduler] ========== 每日代码审核任务开始 ==========");
        long start = System.currentTimeMillis();
        try {
            String report = codeAuditService.performAudit();
            // 报告前 3 行作为执行摘要打印到日志
            String[] lines = report.split("\n", 4);
            log.info("[CodeAuditScheduler] 审核完成, 报告首段:\n{}",
                    lines.length > 2 ? lines[0] + "\n" + lines[2] : "报告为空");
        } catch (Exception e) {
            log.error("[CodeAuditScheduler] 每日代码审核任务执行失败", e);
        } finally {
            long cost = System.currentTimeMillis() - start;
            log.info("[CodeAuditScheduler] ========== 每日代码审核任务结束 (耗时 {} ms) ==========", cost);
        }
    }
}
