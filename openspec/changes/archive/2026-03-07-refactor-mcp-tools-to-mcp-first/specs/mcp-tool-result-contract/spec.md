## ADDED Requirements

### Requirement: MCP text tools return protocol-native text content
Text-oriented tools such as `search`, `create_temp_dir`, and `skill` SHALL return `CallToolResult` objects whose success responses contain protocol-native `TextContent` blocks instead of relying on string conversion bridges.

#### Scenario: Search returns formatted text content
- **WHEN** the search service returns a formatted result payload
- **THEN** the `search` tool returns a non-error `CallToolResult` containing a `TextContent` block with the formatted response body

#### Scenario: Temp directory returns created path as text content
- **WHEN** the temp directory service successfully creates an isolated working directory
- **THEN** the `create_temp_dir` tool returns a non-error `CallToolResult` containing a `TextContent` block with the absolute path

#### Scenario: Skill returns rendered skill content as text content
- **WHEN** a client requests a known skill by name
- **THEN** the `skill` tool returns a non-error `CallToolResult` containing a `TextContent` block with the rendered skill content

### Requirement: Search exposes structured search results
The `search` tool SHALL include `structuredContent` in successful responses so MCP clients can consume normalized search metadata without re-parsing rendered text.

#### Scenario: Search returns machine-readable result list
- **WHEN** the search service returns search results
- **THEN** the `search` tool returns a non-error `CallToolResult` whose `structuredContent` contains the normalized status, paging fields, and result items with titles, URLs, and summaries

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
