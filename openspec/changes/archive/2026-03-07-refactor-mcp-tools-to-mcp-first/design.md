## Context

当前 `search` 与 `create_temp_dir` 已收敛到 `@McpTool` 注解式原生 MCP 路径，`scrape` 与 `skill` 使用显式 `McpServerFeatures.SyncToolSpecification` 返回协议级 `CallToolResult`。其中 `skill` 已完成原生 MCP 改造，但按运行配置默认保持禁用，仅在显式开启时注册。当前剩余收敛重点是保持 change 产物与该运行时行为一致，并确保所有工具在执行失败时都返回协议级 error result。项目已经依赖 `spring-ai-starter-mcp-server` 和 Java MCP schema 类型，因此本次设计目标是基于现有依赖统一到 Spring MCP 原生工具协议，并直接清理历史桥接层。

## Goals / Non-Goals

**Goals:**
- 统一 `search`、`create_temp_dir`、`scrape`、`skill` 的 MCP 原生注册方式。
- 让工具层直接构建 `CallToolResult`，删除 `StringToolCallResultConverter` 与 `@Tool` 桥接路径。
- 将请求解析、结果映射与领域服务调用拆分为可测试组件，降低 `scrape` 配置类耦合。
- 保持现有业务能力与 CLI/MCP server 运行方式不变。

**Non-Goals:**
- 不保留旧的 `@Tool` 注册入口、旧测试桩或兼容适配层。
- 不修改 `SearchFacade`、`PageScrapeService` 的核心抓取或搜索业务逻辑。
- 不引入新的传输协议、外部依赖或运行模式。
- 不在本次变更中扩展新的 MCP 工具。

## Decisions

### 1. 简单工具使用 `@McpTool`，复杂或动态描述工具保留 `SyncToolSpecification`
- 选择：`search`、`create_temp_dir` 使用 `@McpTool` 注解式注册；`scrape` 与 `skill` 保留 `McpServerFeatures.SyncToolSpecification` Bean，其中 `skill` 默认关闭，仅通过显式配置开关注册。
- 原因：当前依赖中已包含 `spring-ai-mcp-annotations`，注解式注册可明显减少简单工具的 definition/handler 装配样板；同时 `scrape` 仍需要显式控制 enum/range schema 和 image/resource/text 映射，`skill` 则需要根据当前已加载的 skills 动态生成 tool description，且现阶段不希望默认暴露。
- 备选方案：所有工具都继续显式声明 `SyncToolSpecification`。
  - 放弃原因：对于简单工具会引入不必要的 definition/handler/configuration 样板。

### 2. 引入独立的 MCP 映射组件，但只在需要时保留 handler
- 选择：为复杂工具和共享逻辑保留 mapper / helper 组件；简单工具尽量把请求解析与 `CallToolResult` 构建收敛到 `@McpTool` 方法中；对 `skill` 这类动态描述工具保留显式 definition/handler。
- 原因：这样可以在不牺牲测试性的前提下减少简单工具样板代码，同时仍为 `scrape` 保留清晰的协议层边界。
- 备选方案：所有工具统一拆成 definition + handler + mapper。
  - 放弃原因：对简单工具收益不足。

### 3. 保留 `UtilMcpService` 作为领域聚合统一入口，但移除兼容职责
- 选择：保留现有 `UtilMcpService` 作为 `search`、`create_temp_dir`、`scrape` 的统一领域入口；`skill` 继续通过 `SkillManager` 访问技能目录；简单工具在 `@McpTool` 方法内直接调用 service 并完成协议映射，复杂工具继续通过独立 handler 调用。
- 原因：可以最小化对搜索与抓取业务链路的影响，同时把 MCP-specific 逻辑留在 `core.mcp` 包内。
- 备选方案：直接删除 `UtilMcpService`，让工具直接调用多个 facade/service。
  - 放弃原因：会扩大重构面，增加对搜索和抓取主链的扰动。

### 4. Search 同时返回文本与结构化结果
- 选择：`search` 成功结果同时返回格式化 `TextContent` 与 `structuredContent`，其中结构化部分暴露标准化的搜索元数据和结果项。
- 原因：搜索结果天然是列表数据，只返回文本会迫使上层 Agent 二次解析；同时返回两种载荷更符合 MCP 对机器可消费结果的设计。
- 备选方案：仅返回 `TextContent`。
  - 放弃原因：会降低上层 Agent/客户端对搜索结果的稳定消费能力。

### 5. 统一错误输出格式并允许破坏性清理
- 选择：所有工具错误都返回 `CallToolResult`，设置 `isError = true`，并至少带一个 `TextContent`；删除所有仅为历史桥接存在的 converter、包装类和旧式入口。
- 原因：满足 MCP 协议要求，也符合“彻底重构、不保留历史包袱”的目标。
- 备选方案：保留旧错误字符串格式和兼容注册入口。
  - 放弃原因：会让新旧协议边界继续共存。

## Risks / Trade-offs

- [风险] 旧的 `UtilMcp` 单测和基于 `@Tool` 的调用路径会整体失效。 → Mitigation：直接替换为基于 `SyncToolSpecification`/handler 的新测试，不保留兼容测试。
- [风险] `scrape` 的结果映射拆分后，媒体与文本分支可能出现行为回归。 → Mitigation：迁移并补强现有 `ScrapeToolSpecificationConfigurationTest` 覆盖的场景。
- [风险] `create_temp_dir` 错误行为从纯字符串返回切换到协议级 error result。 → Mitigation：统一在 MCP mapper 中定义错误输出并更新断言。
- [风险] 彻底移除桥接类后，若还有隐藏引用会在编译期暴露。 → Mitigation：通过 LSP diagnostics 和 Maven 测试快速清理剩余引用。
- [风险] `skill` 默认禁用与 spec / design 表述不一致时，后续 review 容易误判实现状态。 → Mitigation：同步更新 change 产物，并通过配置测试固定默认关闭/显式开启行为。

## Migration Plan

1. 为 change 完成 `tasks.md`，明确代码与测试改造步骤。
2. 将 `search`、`create_temp_dir` 迁移为 `@McpTool`，保留 `CallToolResult` 的直接返回。
3. 保留 `scrape` 与 `skill` 为显式 `SyncToolSpecification`，并继续使用独立 mapper / handler / definition 控制复杂协议行为；`skill` 由单独配置开关决定是否注册。
4. 删除 `StringToolCallResultConverter`、`UtilMcp`、`SkillMcp` 旧 provider 入口以及不再使用的兼容性测试与入口。
5. 更新和补充单元测试、集成测试，执行 `lsp_diagnostics`、最小必要 Maven 测试与编译验证。

## Open Questions

- 无。
