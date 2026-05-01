-- 代码仓库表：添加仓库类型字段
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS repo_type VARCHAR(32) NOT NULL DEFAULT 'SERVICE';
