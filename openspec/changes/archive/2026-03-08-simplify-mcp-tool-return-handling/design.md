## Context

当前 `core.mcp` 下有 4 个 MCP 工具：`search`、`create_temp_dir` 使用注解式 `@McpTool`，`scrape`、`skill` 使用显式 `McpServerFeatures.SyncToolSpecification` 注册。`search` 与 `create_temp_dir` 已收敛到简化后的成功返回实现：前者直接返回 `SearchResponse`，后者直接返回路径字符串，由 Spring MCP 自动封装为合法 `CallToolResult`。但近期搜索后端出现持续性的空结果、超时和上游引擎异常，导致 `search` 对外暴露后会稳定返回无结果或错误体验，因此需要把 `search` MCP 工具默认关闭，同时保留已完成的简化实现，待后端恢复稳定后再通过配置开启。

## Goals / Non-Goals

**Goals:**
- 仅对真正可简化的注解式工具去掉成功路径上的手工 `CallToolResult` 包装。
- 让 `create_temp_dir` 直接返回字符串，让 `search` 直接返回搜索结果对象。
- 让 `search` MCP 工具默认不注册，同时保留其简化后的实现和启用路径。
- 保持失败路径为协议级 `isError = true` 结果，不把错误仅埋入业务对象字段。
- 删除因简单成功包装而存在的 mapper、模板和对应测试桩。
- 保持 `scrape`、`skill` 的显式协议能力不变。

**Non-Goals:**
- 不把 `scrape` 或 `skill` 强行迁移到注解式自动返回模式。
- 不删除 `search` 的实现代码或其返回契约，只调整默认暴露方式。
- 不引入新的依赖、传输协议或新的 MCP 工具。
- 不为 `search` 新增 `message=success` 一类仅用于包装语义的字段。
- 不修改搜索抓取等领域能力的核心业务逻辑，只调整 MCP 适配层与必要的服务返回约定。

## Decisions

### 1. 仅简化注解式且无特殊成功结果需求的工具
- 选择：本次仅简化 `search` 与 `create_temp_dir`；`scrape` 与 `skill` 保持显式 `SyncToolSpecification`。
- 原因：代码事实表明，`scrape` 成功路径需要 `ImageContent` / `EmbeddedResource`，`skill` 需要基于运行时技能列表生成动态 description；这两类需求都超出 `@McpTool` 默认简单返回值映射的适用范围。相反，`create_temp_dir` 仅返回单个文本路径，`search` 在移除 `structuredContent` 后可接受直接返回对象文本，均符合自动封装条件。
- 备选方案：统一简化全部工具。
  - 放弃原因：会丢失复杂工具对协议层内容类型和动态元数据的精确控制。

### 1.1 `search` 与 `create_temp_dir` 拆分为独立注解式工具 Bean
- 选择：不再把 `search` 与 `create_temp_dir` 放在同一个 `AnnotatedMcpTools` Bean 中，而是拆成可独立装配的注解式工具 Bean，使 `search` 可单独受配置开关控制，`create_temp_dir` 继续默认可用。
- 原因：Spring MCP 注解注册是按 Bean 扫描工具方法，若继续共用一个 Bean，则无法只关闭 `search` 而保留 `create_temp_dir`。
- 备选方案：保留同一个 Bean，并尝试在运行时过滤单个方法的注册结果。
  - 放弃原因：复杂度高，且与当前仓库现有注册模式不一致。

### 1.2 引入 `search` MCP 工具配置开关并默认关闭
- 选择：新增与 `SkillProperties` 一致风格的搜索 MCP 配置属性，例如 `mmh.search.mcpToolEnabled=false`，并使用条件装配让 `search` 工具仅在显式开启时注册。
- 原因：这是最小破坏方式，既能立即停止默认实例暴露不稳定搜索能力，又保留后续开启与验证路径。
- 备选方案：直接删除 `search` 工具注册代码。
  - 放弃原因：会丢失当前已完成的简化实现，也不利于后端恢复后快速重新启用。

### 2. `create_temp_dir` 成功时直接返回 `String`
- 选择：`AnnotatedMcpTools.createTempDir()` 改为返回路径字符串；当创建失败或路径为空时直接抛出异常，由注解式 `@McpTool` 回调自动转换为 MCP 错误结果。
- 原因：该工具的成功路径只需要单个文本内容，当前 `TempDirMcpResultMapper` 仅做简单文本包装，没有额外协议价值。改为直接返回 `String` 后可删除 mapper，并保留与当前客户端可见结果等价的文本内容。
- 备选方案：保留 `CreateTempDirResponse` -> `TempDirMcpResultMapper` -> `CallToolResult` 的现状。
  - 放弃原因：成功路径完全重复框架已提供的自动封装能力。

### 3. `search` 成功时直接返回 `SearchResponse`，不再返回格式化文本或 `structuredContent`
- 选择：`AnnotatedMcpTools.search(...)` 在成功路径直接返回 `SearchResponse`，不启用 `generateOutputSchema`，让框架按默认文本模式将对象序列化为 JSON 文本并封装为 `CallToolResult`。
- 原因：用户已明确不再需要 `structuredContent`，也接受直接消费对象 JSON；继续保留 `SearchMcpResultMapper` 与 `mmh_search_result.ftl` 只会维持额外样板代码。保持默认文本模式还能避免把 `search` 再次切换到新的结构化输出契约。
- 备选方案 A：返回格式化字符串。
  - 放弃原因：虽然同样可简化，但会丢失现有 `SearchResponse` 的字段结构，不如直接返回对象更接近已有领域模型。
- 备选方案 B：启用 `generateOutputSchema = true` 返回 `structuredContent`。
  - 放弃原因：这会重新引入结构化输出契约，与本次“移除 `structuredContent` 需求”的前提冲突。

### 4. 失败路径统一改为抛异常，而不是把错误埋入返回对象
- 选择：对于 `search` 与 `create_temp_dir`，工具方法在发现响应对象中的 `error` 字段、空成功值或输入校验失败时直接抛出异常，让 Spring MCP 注解回调自动产出 `isError = true` 的 `CallToolResult`。
- 原因：当前注解链路已经支持将方法抛出的异常自动映射为错误结果；如果继续把错误作为对象字段返回，客户端只能拿到“成功的 JSON 文本”，看不到协议级错误语义。这与现有 `mcp-tool-result-contract` 中“校验失败和执行失败必须以 MCP error result 可见”的目标不一致。
- 备选方案：给 `SearchResponse` 或 `CreateTempDirResponse` 增加 `message` 字段，成功写 `success`，失败写错误信息。
  - 放弃原因：会把协议错误降级为业务数据，破坏 MCP 客户端对 `isError` 的稳定判断，也会污染领域模型。

### 5. 优先在 MCP 适配层完成解包，尽量少改服务接口
- 选择：优先保留 `UtilMcpService` 现有入口；在 `AnnotatedMcpTools` 内对 `SearchResponse` / `CreateTempDirResponse` 进行成功值解包与异常转换，仅在实现过程中确认收益足够时再收敛服务返回类型。
- 原因：`UtilMcpService` 仍是 `search`、`create_temp_dir`、`scrape` 的统一领域入口。直接修改其接口会扩大重构面，并影响非目标工具。先在适配层完成“响应对象 -> 简单返回值/异常”的转换，能以更小改动实现本次目标。
- 备选方案：同步修改 `UtilMcpService` 及实现，让其直接返回 `String` / `SearchResponse` 并抛异常。
  - 放弃原因：虽然更彻底，但会扩大接口调整范围；在本 change 中不是必须前提。

### 6. 删除仅为简单成功包装服务的 MCP 组件与测试依赖
- 选择：删除 `TempDirMcpResultMapper`、`SearchMcpResultMapper`，并移除仅被 `search` 使用的 `mmh_search_result.ftl` 模板；更新 `AnnotatedMcpToolsTest` 以断言框架自动封装后的文本 JSON / 文本内容与错误结果。
- 原因：这些组件的存在前提就是手工构建成功 `CallToolResult`。当成功路径改为自动封装后，继续保留只会制造无效层次。
- 备选方案：保留 mapper 但只让其处理错误路径。
  - 放弃原因：错误路径已可由异常自动封装，继续保留 mapper 会让实现风格更混杂。

## Risks / Trade-offs

- [风险] `search` 的成功文本会从格式化列表改为 JSON 文本，依赖旧文本样式的上层提示词或断言可能失效。 → Mitigation：在 spec 与测试中明确新契约，并只保留对象字段级稳定性断言，不再依赖旧模板格式。
- [风险] 若 `UtilMcpService` 继续返回带 `error` 字段的对象，工具层遗漏解包逻辑会把失败误当成成功结果。 → Mitigation：在 `AnnotatedMcpTools` 中集中处理“error 字段 -> 抛异常”的分支，并补充失败场景单测。
- [风险] 框架对对象返回值的默认文本序列化可能包含字段顺序差异。 → Mitigation：测试优先断言 JSON 关键字段或反序列化后的对象结构，而非整段文本精确匹配。
- [风险] 删除 mapper 后，若还有隐藏调用点会导致编译失败。 → Mitigation：通过 LSP diagnostics、聚焦测试和最小必要 Maven 编译及时暴露残留引用。
- [风险] `search` 默认关闭后，依赖该工具的使用方可能误以为功能被删除。 → Mitigation：在工具注册测试、配置属性和变更说明中明确“默认关闭但可显式开启”。

## Migration Plan

1. 更新 specs，去掉 `search` 的 `structuredContent` 要求，并放宽简单注解式工具的服务端实现形式约束。
2. 调整 `AnnotatedMcpTools`：`create_temp_dir` 成功返回 `String`，`search` 成功返回 `SearchResponse`；失败分支统一抛异常。
3. 删除 `TempDirMcpResultMapper`、`SearchMcpResultMapper` 以及仅为旧 `search` 文本格式服务的模板资源。
4. 拆分注解式 MCP 工具 Bean，并新增 `search` MCP 工具配置开关，默认关闭 `search` 注册。
5. 更新 MCP 注册与工具测试，覆盖 `search` 默认禁用、显式开启以及 `create_temp_dir` 持续可用的行为。
6. 执行 `lsp_diagnostics`、聚焦 Maven 测试与最小必要 Maven 编译验证，确保删除旧 mapper 后且 `search` 默认关闭时无残留引用。

## Open Questions

- 无。
