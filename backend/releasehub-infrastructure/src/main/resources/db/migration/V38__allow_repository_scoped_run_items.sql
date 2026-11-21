-- 收尾执行项以 (window, repository) 为范围，不能借用首个迭代标识。
-- 保留原有三元组 Item 的标识、步骤与归属，不改写历史证据。
ALTER TABLE run_item ALTER COLUMN iteration_key DROP NOT NULL;

-- 回滚应用时先停用新收尾入口；已有仓库级执行记录仍须保留，不能恢复非空约束或删除记录。
