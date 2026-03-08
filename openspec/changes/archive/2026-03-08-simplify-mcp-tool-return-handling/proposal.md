## Why

当前项目已经迁移到 Spring MCP 原生工具注册，但部分注解式简单工具仍然手工构建 `CallToolResult`，与 Spring MCP 对 `@McpTool` 简单返回值的自动协议封装能力重复，增加了 result mapper 与样板代码。与此同时，当前 `search` 工具依赖的搜索后端出现持续性空结果和超时问题，需要先把 `search` MCP 注册默认关闭，避免把不稳定能力继续暴露给默认运行实例。

## What Changes

- 审视当前所有 MCP 工具的注册方式与结果契约，识别哪些工具可以安全依赖 Spring MCP 对注解式返回值的自动协议封装。
- 对采用 `@McpTool` 注册、成功结果仅需返回纯文本或简单对象，且不需要 `structuredContent`、媒体内容、多 `content` block 或自定义协议级成功结果的工具，允许服务端方法直接返回 `String` 或简单对象，由 Spring MCP 自动封装为合法的 `CallToolResult`。
- 对上述可简化工具，移除多余的 result mapper 与成功路径上的手工 `CallToolResult` 包装代码；失败路径继续通过抛出异常或等价机制产出协议级 `isError=true` 结果，而不是仅把错误信息埋入业务对象字段。
- 保留 `scrape`、`skill` 等仍需显式协议控制的工具路径；`search` 在移除 `structuredContent` 需求后继续使用简化后的返回实现，但新增独立配置开关并默认关闭 MCP 注册，仅在显式开启时暴露。
- 更新相关 MCP specs，使要求聚焦于“对外协议结果必须合法且一致”，而不是强制所有文本或对象结果都在服务端实现中显式构建 `CallToolResult`。

## Capabilities

### New Capabilities
- 无

### Modified Capabilities
- `mcp-native-tool-registration`: 调整简单 MCP 工具的实现约束，允许符合条件的注解式工具直接返回简单文本或对象结果，由 Spring MCP 自动封装为原生协议结果；并允许 `search` MCP 工具通过配置默认关闭。
- `mcp-tool-result-contract`: 调整工具结果契约，区分“协议可观察结果”与“服务端实现形式”；允许简单成功结果通过框架自动封装产生，但继续要求校验失败和执行失败以协议级错误返回，并明确 `search` 的文本结果契约仅在工具启用时生效。

## Impact

- 影响代码：`/home/fengwk/prog/my-mcp-hub/core/src/main/java/fun/fengwk/mmh/core/mcp/`
- 影响测试：对应 MCP tool 单测与 MCP 注册装配测试，特别是简单工具成功路径自动封装结果、失败路径协议错误语义以及 `search` 默认禁用行为的断言
- 影响运行时：`create_temp_dir` 继续默认可用，`search` 工具改为默认不注册；在显式开启前，不再向默认运行实例暴露不稳定搜索能力
- 不影响依赖：继续使用现有 `spring-ai-starter-mcp-server`、`spring-ai-mcp-annotations` 与底层 MCP schema 类型
