# Scrape MCP 开发计划

## 1. 项目概述

在 `core` 模块新增基于 Playwright 的 `scrape` 工具能力，并将 worker + Playwright 生命周期、快照协调、并发控制抽取为通用浏览器运行时层，供后续 `BrowserMcp` 类工具复用。当前阶段以 `scrape` 落地为主，同时完成可扩展架构骨架，避免后续重复建设。

## 2. 范围与里程碑

| 里程碑 | 覆盖范围/交付物 | 依赖 | 验收 |
|---|---|---|---|
| M1 运行时基础层 | 通用 Browser Runtime + Snapshot Coordination + 配置骨架 | - | `scrape` 可通过通用执行器跑通最小链路 |
| M2 Scrape 可用版本 | MCP `scrape`、登录命令、模板输出、核心测试 | M1 | 五种 format 可用，登录态可跨进程共享 |
| M3 扩展预留验证 | 第二工具伪实现接入、配置拆分兼容验证 | M2 | 新工具接入不改运行时主干 |

## 3. 任务分解

| ID | 任务 | 优先级 | 依赖 | 状态 | 验收 |
|---|---|---|---|---|---|
| T1 | 修复测试基线：`workflowTrace` 与现有实现不一致问题 | 高 | - | pending | `UtilMcpTest` 基线可稳定运行 |
| T2 | 设计并实现通用 `BrowserTask`/`BrowserTaskExecutor` 抽象 | 高 | T1 | pending | `scrape` 通过通用执行器运行，不直接依赖具体工具逻辑 |
| T3 | 实现通用 `BrowserWorkerManager`（进程内池化、限流、超时） | 高 | T2 | pending | 支持多请求并发执行，出现超载时返回可识别错误 |
| T4 | 抽取快照协调层（初始化、读写、CAS 发布、锁）到 `service/browser/coordination` | 高 | T2 | pending | `master` 自动初始化，CAS 防回滚生效 |
| T5 | 落地登录命令链路（`mmh-cli open-browser`，profile 由配置提供） | 高 | T4 | pending | headed 登录后可持续发布快照并安全退出 |
| T6 | 接入 `scrape` 工具（参数校验、格式输出、等待策略） | 高 | T3,T4 | pending | `markdown/html/links/screenshot/fullscreenshot` 单格式输出可用 |
| T7 | 页面解析链路（正文清理、Markdown 渲染、后处理、质量门禁） | 中 | T6 | pending | `onlyMainContent` 与回退逻辑符合设计 |
| T8 | 配置拆分：公共项迁移到 `mmh.browser.*`，保留 `mmh.scrape.*` 业务项 | 中 | T3,T4,T6 | pending | 双配置兼容策略明确并有测试覆盖 |
| T9 | 扩展预留验证：新增 `BrowserTask` 伪实现作为第二工具样例 | 中 | T6,T8 | pending | 新工具接入无需修改执行器主流程 |
| T10 | 测试与文档收口（单测、组件测、集成测、命令说明） | 中 | T5,T6,T7,T8,T9 | pending | `env JAVA_HOME=$JAVA_HOME_17 mvn -pl core test` 通过 |

## 4. 外部阻塞与待解决事项

- Playwright 浏览器二进制下载与运行环境依赖（CI/离线环境）
- `mmh-cli open-browser` 在不同桌面环境下的可用性（无头服务器不支持 headed）
- 跨进程文件锁在不同文件系统上的行为一致性（本地磁盘优先）

## 5. 待确认事项

- 第二阶段是否直接落一个最小 `BrowserMcp` 工具（例如仅截图）作为真实复用验证
- `mmh.browser.*` 与 `mmh.scrape.*` 的最终配置优先级策略（公共优先或工具优先）
- 快照目录默认位置是否需要按环境（本地/容器）区分
