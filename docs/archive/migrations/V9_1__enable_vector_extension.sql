-- V9.1: 启用 pgvector 扩展
-- 必须单独在 V10 之前执行，否则 V10 中 VECTOR 类型不可用
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
