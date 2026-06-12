package com.huicai.module.system.service;

/**
 * 项目代码审核服务
 * 每日早晨自动执行, 对项目代码状态做一次全面巡检
 */
public interface CodeAuditService {

    /**
     * 执行一次完整的代码审核, 生成审核报告
     *
     * @return 审核报告文本 (Markdown 格式)
     */
    String performAudit();
}
