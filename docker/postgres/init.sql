-- PostgreSQL 16 初始化脚本
-- 慧财财务 (Huicai Financial)

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 验证扩展安装
SELECT name, default_version, installed_version
FROM pg_available_extensions
WHERE name IN ('vector', 'pg_trgm', 'pgcrypto');