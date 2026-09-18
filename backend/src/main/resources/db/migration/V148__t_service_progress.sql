-- P79 代理服务进度跟踪（agency 跨客户维护，agency_id 隔离 + enterprise_id 存被服务客户）
-- 设计：节点化服务进度 INTAKE→BOOKING→REVIEW→FILING→DONE，事件驱动只进不退
-- 数据权限：本表加入 SHARED_TABLES（同 t_agency_user），查询手动过滤 agency_id，
-- 避免 EnterpriseDataPermissionInterceptor 注入当前 enterprise_id 破坏跨客户查询

CREATE TABLE t_service_progress (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id             BIGINT       NOT NULL,
    enterprise_id         BIGINT       NOT NULL,
    period                VARCHAR(6)   NOT NULL,
    stage                 VARCHAR(20)  NOT NULL,        -- INTAKE/BOOKING/REVIEW/FILING/DONE
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING/IN_PROGRESS/DONE
    assigned_to           BIGINT,                        -- agency_user_id
    started_at            TIMESTAMP,
    finished_at           TIMESTAMP,
    due_date              DATE,                           -- 申报期限/合同 SLA（V1 手工填）
    overtime_notified_at  TIMESTAMP,
    remark                VARCHAR(500),
    created_by            BIGINT,
    created_at            TIMESTAMP    DEFAULT now(),
    updated_by            BIGINT,
    updated_at            TIMESTAMP    DEFAULT now(),
    version               INTEGER      DEFAULT 0,
    deleted               INTEGER      DEFAULT 0,
    CONSTRAINT uq_service_progress UNIQUE (agency_id, enterprise_id, period, stage)
);

COMMENT ON TABLE t_service_progress IS '代理服务进度跟踪（P79，节点化 INTAKE/BOOKING/REVIEW/FILING）';
COMMENT ON COLUMN t_service_progress.stage IS 'INTAKE 取票 / BOOKING 记账 / REVIEW 审核结账 / FILING 报税 / DONE 全部完成';

CREATE INDEX idx_sp_agency_period_stage ON t_service_progress (agency_id, period, stage);
CREATE INDEX idx_sp_assignee ON t_service_progress (agency_id, assigned_to, status);
CREATE INDEX idx_sp_due ON t_service_progress (agency_id, due_date, status);
