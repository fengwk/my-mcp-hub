# mcp-tool-result-contract Specification

## Purpose
TBD - created by archiving change refactor-mcp-tools-to-mcp-first. Update Purpose after archive.
## Requirements
### Requirement: MCP text tools return protocol-native text content
Text-oriented tools such as `create_temp_dir` and `skill`, and `search` when that tool is enabled, SHALL expose successful responses to MCP clients as protocol-native `TextContent` blocks. Annotation-based tools MAY do this by returning `String` values or simple objects that Spring MCP automatically wraps into `CallToolResult`, while explicit MCP tools MAY continue constructing `CallToolResult` directly when they need manual control.

#### Scenario: Search returns JSON text content from object result
- **WHEN** the `search` MCP tool is enabled and the search service returns a successful search response object
- **THEN** the `search` tool returns a non-error MCP result whose content contains a `TextContent` block with the serialized search response body

#### Scenario: Temp directory returns created path as text content
- **WHEN** the temp directory service successfully creates an isolated working directory
- **THEN** the `create_temp_dir` tool returns a non-error `CallToolResult` containing a `TextContent` block with the absolute path

#### Scenario: Skill returns rendered skill content as text content
- **WHEN** a client requests a known skill by name
- **THEN** the `skill` tool returns a non-error `CallToolResult` containing a `TextContent` block with the rendered skill content

### Requirement: Scrape returns protocol-level media and resource content
The `scrape` tool SHALL map service responses to protocol-native MCP content blocks, using `ImageContent` for image media, `EmbeddedResource` for non-image binary media, and `TextContent` for textual results.

#### Scenario: Scrape returns image content for image media
- **WHEN** the scrape service returns media payload with an image MIME type
- **THEN** the tool returns a non-error `CallToolResult` containing an `ImageContent` block with the decoded payload and MIME type

#### Scenario: Scrape returns embedded resource for non-image media
- **WHEN** the scrape service returns media payload with a non-image MIME type
- **THEN** the tool returns a non-error `CallToolResult` containing an `EmbeddedResource` block with the source URI, MIME type, and binary payload

### Requirement: MCP tools return protocol errors consistently
All MCP tools SHALL report protocol-visible validation and execution failures with `isError = true` and at least one `TextContent` block that describes the failure.

#### Scenario: Validation error is returned as MCP error result
- **WHEN** a client sends invalid tool arguments such as an unsupported scrape format or malformed URL
- **THEN** the handler does not call the downstream service and returns an error `CallToolResult` with a `TextContent` block describing the validation failure

#### Scenario: Service failure is returned as MCP error result
- **WHEN** the underlying service reports an execution error for a tool call
- **THEN** the MCP tool returns `isError = true` with a `TextContent` block containing the error details
