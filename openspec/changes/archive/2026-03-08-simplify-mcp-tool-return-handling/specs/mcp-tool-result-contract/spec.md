## MODIFIED Requirements

### Requirement: MCP text tools return protocol-native text content
Text-oriented tools such as `create_temp_dir` and `skill`, and `search` when that tool is enabled, SHALL expose successful responses to MCP clients as protocol-native `TextContent` blocks. Annotation-based tools MAY do this by returning `String` values or simple objects that Spring MCP automatically wraps into `CallToolResult`, while explicit MCP tools MAY continue constructing `CallToolResult` directly when they need manual control.

#### Scenario: Search returns JSON text content from object result
- **WHEN** the `search` MCP tool is enabled and the search service returns a successful search response object
- **THEN** the `search` tool returns a non-error MCP result whose content contains a `TextContent` block with the serialized search response body

#### Scenario: Temp directory returns created path as text content
- **WHEN** the temp directory service successfully creates an isolated working directory
- **THEN** the `create_temp_dir` tool returns a non-error MCP result containing a `TextContent` block with the absolute path

#### Scenario: Skill returns rendered skill content as text content
- **WHEN** a client requests a known skill by name
- **THEN** the `skill` tool returns a non-error MCP result containing a `TextContent` block with the rendered skill content

## REMOVED Requirements

### Requirement: Search exposes structured search results
**Reason**: The simplified `search` tool no longer needs a separate `structuredContent` payload because clients accept the search response as JSON text content.

**Migration**: Consumers of `search` MUST read the serialized search response from the returned `TextContent` body instead of `structuredContent`.
