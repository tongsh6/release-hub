-- 创建系统设置表
CREATE TABLE system_settings (
    id VARCHAR(50) PRIMARY KEY,
    gitlab_base_url VARCHAR(255),
    gitlab_token VARCHAR(800),
    feature_template VARCHAR(255),
    release_template VARCHAR(255),
    default_blocking_policy VARCHAR(255)
);

-- 插入默认行
INSERT INTO system_settings (id) VALUES ('GLOBAL');
