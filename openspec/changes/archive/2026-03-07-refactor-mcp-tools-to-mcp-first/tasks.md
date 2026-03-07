## 1. MCP tool registration refactor

- [x] 1.1 Replace `UtilMcp` `@Tool` registrations with native `@McpTool` tools for `search` and `create_temp_dir`
- [x] 1.2 Keep `UtilMcpService` as the unified domain entry while reducing simple-tool MCP boilerplate
- [x] 1.3 Remove `StringToolCallResultConverter` and other obsolete bridge-only code paths
- [x] 1.4 Replace legacy `SkillMcp` `ToolCallbackProvider` registration with native MCP tool registration
- [x] 1.5 Keep the refactored `skill` MCP tool implementation disabled by default while retaining an explicit enable switch

## 2. Scrape MCP restructuring

- [x] 2.1 Split scrape request parsing, validation, and `CallToolResult` mapping into dedicated MCP components
- [x] 2.2 Keep `scrape` media/text/error behavior aligned with the new MCP result contract
- [x] 2.3 Simplify MCP configuration classes so they only assemble the remaining explicit tool specifications
- [x] 2.4 Refactor `skill` into explicit MCP definition/handler/result flow with dynamic tool description support

## 3. Search result contract enhancement

- [x] 3.1 Add `structuredContent` for successful search results while preserving human-readable text output
- [x] 3.2 Update MCP tests to assert both `TextContent` and structured search payloads

## 4. Test and verification updates

- [x] 4.1 Replace legacy `@Tool`-oriented tests with `@McpTool`-oriented MCP tests for `search` and `create_temp_dir`
- [x] 4.2 Update scrape MCP tests to target the refactored configuration and handlers
- [x] 4.3 Run diagnostics, focused Maven tests, and minimal build verification for the refactor
- [x] 4.4 Add MCP tests for the refactored `skill` tool and rerun focused verification
- [x] 4.5 Add coverage for the `skill` MCP tool enable/disable switch and rerun focused verification

## 5. Review follow-up fixes

- [x] 5.1 Align specs and design with the default-disabled `skill` MCP tool behavior
- [x] 5.2 Ensure `skill` MCP execution failures are returned as protocol error results
- [x] 5.3 Remove stale `cli-all` bootstrap references to deleted `UtilMcp` registration path and rerun CLI build verification
- [x] 5.4 Remove out-of-scope OpenSpec initialization artifacts under `.codex/`
- [x] 5.5 Fix delta spec wording so archive validation passes
