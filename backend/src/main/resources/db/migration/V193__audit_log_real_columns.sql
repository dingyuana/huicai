-- SPEC-P152 日志与操作审计补全：
-- t_audit_log 表自 V1 基线以来缺失 LogAspect/AuditTrackingAspect/LoginBruteForceGuard
-- 精心构造的 6 个字段（method/request_params/response_result/execution_time_ms/
-- status/user_agent），实体一直用 @TableField(exist=false) 幽灵字段假装存在，
-- 切面写了也白写（AGENTS §4.5「名实不符」形态）。此处补齐真实列，实体字段转正。
--
-- 类型口径：
-- * request_params / response_result 用 TEXT 而非 JSONB —— 现存写方
--   （recordStatusChange 的 "newValue=..." 拼串、异常 message）并非合法 JSON，
--   JSONB 列会整行插入失败；审计参数字段是展示/排障用途，TEXT 不会因格式丢数据。
--   before_data/after_data 保持 JSONB（P103 起由 ObjectMapper 生成合法 JSON）。

ALTER TABLE t_audit_log ADD COLUMN IF NOT EXISTS method            VARCHAR(255);
ALTER TABLE t_audit_log ADD COLUMN IF NOT EXISTS request_params    TEXT;
ALTER TABLE t_audit_log ADD COLUMN IF NOT EXISTS response_result   TEXT;
ALTER TABLE t_audit_log ADD COLUMN IF NOT EXISTS execution_time_ms INTEGER;
ALTER TABLE t_audit_log ADD COLUMN IF NOT EXISTS status            VARCHAR(20);
ALTER TABLE t_audit_log ADD COLUMN IF NOT EXISTS user_agent        VARCHAR(500);

-- 操作日志页的「成功/失败」过滤与按模块巡检都要走这两条索引
CREATE INDEX IF NOT EXISTS idx_audit_log_status       ON t_audit_log (status);
CREATE INDEX IF NOT EXISTS idx_audit_log_module_time  ON t_audit_log (module, operation_time DESC);

COMMENT ON COLUMN t_audit_log.method            IS '目标方法名（LogAspect/AuditTrackingAspect 采集）';
COMMENT ON COLUMN t_audit_log.request_params    IS '请求参数 JSON（LogAspect 采集，密码脱敏后）；TEXT 容纳非 JSON 兜底串';
COMMENT ON COLUMN t_audit_log.response_result   IS '响应结果或异常 message（排障依据）';
COMMENT ON COLUMN t_audit_log.execution_time_ms IS '执行耗时毫秒（慢操作巡检）';
COMMENT ON COLUMN t_audit_log.status            IS 'success / fail（操作结果，页端过滤）';
COMMENT ON COLUMN t_audit_log.user_agent        IS '客户端 UA（人员操作溯源）';
