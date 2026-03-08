## 1. Spec and contract alignment

- [x] 1.1 Validate the new delta specs for `mcp-native-tool-registration` and `mcp-tool-result-contract` against the intended simplification scope
- [x] 1.2 Confirm the implementation scope is limited to `search` and `create_temp_dir`, while `scrape` and `skill` remain on explicit MCP specifications

## 2. Simplify annotation-based MCP tools

- [x] 2.1 Refactor `AnnotatedMcpTools.createTempDir()` to return the created path directly and convert failure cases to exceptions for protocol-level MCP errors
- [x] 2.2 Refactor `AnnotatedMcpTools.search(...)` to return `SearchResponse` directly, remove `structuredContent` success handling, and convert response error branches to exceptions
- [x] 2.3 Remove `TempDirMcpResultMapper` and `SearchMcpResultMapper`, and clean up any now-unused wiring or imports in the MCP package
- [x] 2.4 Remove the obsolete `mmh_search_result.ftl` template and any remaining code references that only supported the old formatted search text output

## 3. Update MCP tests

- [x] 3.1 Update `AnnotatedMcpToolsTest` to assert Spring MCP auto-wraps `create_temp_dir` success results as text content and still reports failures as `isError = true`
- [x] 3.2 Update `AnnotatedMcpToolsTest` to assert `search` success returns serialized JSON text content and that search failures are surfaced as protocol-level MCP errors
- [x] 3.3 Review adjacent MCP tests to ensure no remaining assertions depend on removed `structuredContent` or the deleted formatted search template

## 4. Verification

- [x] 4.1 Run `lsp_diagnostics` on the modified MCP classes and resolve any errors introduced by the simplification
- [x] 4.2 Run focused Maven tests for the affected MCP test classes under `core`
- [x] 4.3 Run a minimal Maven compile or package verification for `core` to confirm removed mappers and templates leave no stale references

## 5. Disable search MCP registration by default

- [x] 5.1 Introduce a dedicated `search` MCP tool configuration property with default disabled behavior, aligned with the existing configurable MCP tool pattern
- [x] 5.2 Split annotation-based MCP tool beans so `search` can be conditionally excluded while `create_temp_dir` remains registered by default
- [x] 5.3 Update MCP registration and tool tests to verify `search` is disabled by default, can be enabled explicitly, and does not affect `create_temp_dir`
- [x] 5.4 Run diagnostics, focused tests, and minimal compile verification for the new default-disabled `search` registration behavior
