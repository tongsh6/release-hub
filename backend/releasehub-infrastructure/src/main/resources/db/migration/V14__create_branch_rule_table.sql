-- 创建分支规则表
CREATE TABLE branch_rule (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    pattern VARCHAR(256) NOT NULL,
    type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT DEFAULT 0
);

-- 创建索引
CREATE INDEX idx_branch_rule_name ON branch_rule(name);
CREATE INDEX idx_branch_rule_type ON branch_rule(type);

-- 插入默认规则
INSERT INTO branch_rule (id, name, pattern, type, created_at, updated_at, version)
VALUES 
    ('feature', 'Feature Branches', 'feature/*', 'ALLOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('hotfix', 'Hotfix Branches', 'hotfix/*', 'ALLOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('release', 'Release Branches', 'release/*', 'ALLOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('main', 'Main Protection', 'main', 'BLOCK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
