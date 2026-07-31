-- V94.5: 已弃用 — 基础科目数据迁移至 V102.5
-- 保留空文件避免 Flyway 校验失败，ON CONFLICT DO NOTHING 确保幂等
SELECT 1 WHERE 1 = 1;