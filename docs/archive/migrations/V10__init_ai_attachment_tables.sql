-- ============================================================
-- V10: 附件与AI任务表
-- 附件、文件、AI任务调度、审计增强
-- ============================================================

-- 1. 附件表
CREATE TABLE IF NOT EXISTS t_attachment (
    id              BIGINT PRIMARY KEY,
    biz_type        VARCHAR(32)  NOT NULL,
    biz_id          BIGINT,
    file_name       VARCHAR(255) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    bucket_name     VARCHAR(64)  NOT NULL,
    file_size       BIGINT,
    content_type    VARCHAR(128),
    file_hash       VARCHAR(128),
    ocr_data        JSONB,
    vector          public.vector(768),
    uploaded_by     BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_attachment_biz ON t_attachment(biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_attachment_hash ON t_attachment(file_hash);

COMMENT ON TABLE  t_attachment IS '附件表';
COMMENT ON COLUMN t_attachment.bucket_name IS 'MinIO 桶名';
COMMENT ON COLUMN t_attachment.ocr_data IS 'OCR 识别结果 JSON';
COMMENT ON COLUMN t_attachment.vector IS '文本嵌入向量(用于相似度检索)';

-- 2. AI任务表
CREATE TABLE IF NOT EXISTS t_ai_task (
    id              BIGINT PRIMARY KEY,
    task_no         VARCHAR(32)  NOT NULL,
    task_type       VARCHAR(32)  NOT NULL,
    biz_type        VARCHAR(32),
    biz_id          BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    input_data      JSONB,
    output_data     JSONB,
    error_message   TEXT,
    confidence      NUMERIC(5,4),
    reviewed        BOOLEAN      DEFAULT FALSE,
    reviewed_by     BIGINT,
    reviewed_at     TIMESTAMP,
    apply_status    VARCHAR(20)  DEFAULT 'NOT_APPLIED',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_ai_task_no UNIQUE (task_no),
    CONSTRAINT chk_ai_task_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_ai_task_apply CHECK (apply_status IS NULL OR apply_status IN ('APPLIED', 'REJECTED', 'NOT_APPLIED'))
);

CREATE INDEX IF NOT EXISTS idx_ai_task_type ON t_ai_task(task_type);
CREATE INDEX IF NOT EXISTS idx_ai_task_status ON t_ai_task(status);
CREATE INDEX IF NOT EXISTS idx_ai_task_biz ON t_ai_task(biz_type, biz_id);

COMMENT ON TABLE  t_ai_task IS 'AI 任务表';
COMMENT ON COLUMN t_ai_task.task_type IS '任务类型: OCR, CLASSIFY, MATCH, ANALYZE, ANOMALY';
COMMENT ON COLUMN t_ai_task.reviewed IS '是否已人工审核';
COMMENT ON COLUMN t_ai_task.apply_status IS '应用状态: APPLIED-已应用, REJECTED-已拒绝, NOT_APPLIED-未应用';
COMMENT ON COLUMN t_ai_task.confidence IS 'AI 置信度 0~1';

-- 3. AI异常标记表
CREATE TABLE IF NOT EXISTS t_ai_anomaly_tag (
    id              BIGINT PRIMARY KEY,
    biz_type        VARCHAR(32)  NOT NULL,
    biz_id          BIGINT       NOT NULL,
    anomaly_type    VARCHAR(32)  NOT NULL,
    severity        VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    description     VARCHAR(500),
    ai_task_id      BIGINT,
    resolved        BOOLEAN      DEFAULT FALSE,
    resolved_by     BIGINT,
    resolved_at     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_anomaly_task FOREIGN KEY (ai_task_id) REFERENCES t_ai_task(id),
    CONSTRAINT chk_anomaly_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_anomaly_biz ON t_ai_anomaly_tag(biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_anomaly_resolved ON t_ai_anomaly_tag(resolved);

COMMENT ON TABLE  t_ai_anomaly_tag IS 'AI 异常标记';
