-- V27: 升级 BranchRule 模型 — 新增 scope / enabled / description 字段
-- 同时迁移 type: ALLOW→TEMPLATE, BLOCK→REGEX

-- 添加新列
ALTER TABLE branch_rule
    ADD COLUMN IF NOT EXISTS description VARCHAR(512);

ALTER TABLE branch_rule
    ADD COLUMN IF NOT EXISTS scope_level VARCHAR(32) NOT NULL DEFAULT 'GLOBAL';

ALTER TABLE branch_rule
    ADD COLUMN IF NOT EXISTS scope_project_id VARCHAR(64);

ALTER TABLE branch_rule
    ADD COLUMN IF NOT EXISTS scope_sub_project_id VARCHAR(64);

ALTER TABLE branch_rule
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 迁移 type 数据：ALLOW → TEMPLATE, BLOCK → REGEX
UPDATE branch_rule SET type = 'TEMPLATE' WHERE type = 'ALLOW';
UPDATE branch_rule SET type = 'REGEX' WHERE type = 'BLOCK';
