## Why

当前工程同时存在 `@Tool` + `StringToolCallResultConverter` 的桥接路径和原生 `SyncToolSpecification` 路径，导致工具注册、返回模型、错误语义与输入校验不一致。随着 `scrape` 已经需要图片与资源级内容块，继续沿用字符串桥接会偏离 MCP 规范，也不符合 Spring MCP 原生工具协议的实现方式。

## What Changes

- 将 `search`、`create_temp_dir`、`scrape`、`skill` 统一收敛到 Spring MCP 原生工具协议，停止通过 `@Tool`/`ToolCallbackProvider` 桥接到 MCP；其中简单工具优先使用 `@McpTool` 注解方式，复杂或需要动态描述的工具保留显式 `SyncToolSpecification`，但 `skill` tool 在本次变更后默认保持禁用，仅保留代码与开启开关。
- 引入统一的 MCP tool 定义、请求解析、结果映射与错误输出约定，让工具层直接返回 `CallToolResult` 与协议级 content block。
- 重构 `scrape` 与 `skill` 的适配层职责，拆分 schema/handler/result mapping，减少配置类或 provider 类承载业务逻辑。
- 调整服务接口与测试，使业务层与 MCP 协议层解耦，保留现有搜索、临时目录、抓取等领域能力。
- **BREAKING**：工具注册实现和部分内部类职责将调整，依赖 `@Tool`/`ToolCallback`/`ToolCallbackProvider` 的内部装配方式会被替换。

## Capabilities

### New Capabilities
- `mcp-native-tool-registration`: 统一定义 MCP 原生工具注册方式，要求工具通过 Spring MCP 原生协议对象暴露能力而非 `ToolCallback` 字符串桥接，并允许简单工具使用 `@McpTool` 降低样板代码。
- `mcp-tool-result-contract`: 统一定义 MCP 工具输出契约，覆盖文本、图片、资源和错误结果的协议级返回规则。

### Modified Capabilities

- 无

## Impact

- 影响代码：`core/src/main/java/fun/fengwk/mmh/core/mcp/`、`core/src/main/java/fun/fengwk/mmh/core/service/`、`core/src/main/java/fun/fengwk/mmh/core/utils/` 及对应测试。
- 影响运行时：Spring MCP server 的 tool 装配方式、`scrape`/`search`/`create_temp_dir` 的对外协议返回。
- 影响依赖：继续使用现有 `spring-ai-starter-mcp-server` 与 Java MCP schema 类型，不新增协议栈。
