# ADR-002: DDD 六边形架构分层

**日期**: 2025-12-01
**状态**: Accepted

## 上下文

ReleaseHub 的领域复杂度高，涉及发布窗口生命周期、版本语义、冲突检测、多仓库编排等多个需要精细建模的领域概念。传统的三层架构（Controller → Service → Repository）容易导致业务逻辑散落在 Service 层，领域概念被贫血模型稀释。

## 决策

采用 **DDD 六边形架构**，将后端拆分为 6 个 Maven 模块：

```
bootstrap → interfaces → application → domain → common
                            ↑
                     infrastructure
```

- **releasehub-domain**：纯领域模型，零框架依赖。包含聚合根、值对象、领域服务、领域事件
- **releasehub-application**：应用服务 + 用例编排 + Port 接口定义。不含基础设施实现
- **releasehub-infrastructure**：Port 的 Adapter 实现（JPA、GitLab、GitHub、Security）
- **releasehub-interfaces**：REST Controller + DTO + 全局异常处理
- **releasehub-common**：跨模块共享（异常类、分页、响应封装）
- **releasehub-bootstrap**：Spring Boot 入口 + 配置 + 集成测试

通过 **Maven Enforcer Plugin** 和 **ArchUnit** 强制执行分层依赖规则：
- domain 不依赖任何外部框架
- application 不依赖 infrastructure
- infrastructure 实现 application 的 Port 接口

## 后果

### 正面影响
- 领域逻辑集中、可独立测试（无框架启动开销）
- Port/Adapter 边界清晰，换基础设施只需新增 Adapter
- ArchUnit + Maven Enforcer 自动防止分层腐化

### 负面影响
- 模块数量多（6 个），新增功能需要理解分层映射
- 简单的 CRUD 操作也需要跨模块编写代码
- DTO 映射（domain ↔ infrastructure ↔ API）增加代码量

## 备选方案

### 方案 A: 传统三层架构（Controller → Service → Repository）
- 优点: 简单，开发速度快
- 缺点: 业务逻辑容易散落，领域概念不显式
- 为何未选择: ReleaseHub 的领域复杂度不值得牺牲长期可维护性

### 方案 B: 微服务拆分
- 优点: 独立部署、独立扩缩容
- 缺点: MVP 阶段分布式复杂度远大于收益
- 为何未选择: 当前规模下单体六边形架构已足够，未来可按聚合边界演进为微服务
