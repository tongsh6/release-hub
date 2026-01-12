-- 创建版本策略表
CREATE TABLE version_policy (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    scheme VARCHAR(32) NOT NULL,
    bump_rule VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT DEFAULT 0
);

-- 创建索引
CREATE INDEX idx_version_policy_scheme ON version_policy(scheme);

-- 插入内置策略数据
INSERT INTO version_policy (id, name, scheme, bump_rule, created_at, updated_at, version)
VALUES 
    ('MAJOR', 'SemVer MAJOR', 'SEMVER', 'MAJOR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('MINOR', 'SemVer MINOR', 'SEMVER', 'MINOR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('PATCH', 'SemVer PATCH', 'SEMVER', 'PATCH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('DATE', 'Date Version', 'DATE', 'NONE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
