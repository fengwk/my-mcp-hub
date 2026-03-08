## MODIFIED Requirements

### Requirement: Simple MCP tools may use annotation-based registration
Simple tools whose input schema can be fully expressed by method parameters and basic annotations SHALL use Spring MCP annotation-based registration to reduce boilerplate, unless explicit specifications are required by protocol-specific constraints. When such tools only need simple text or object success results, the annotated service method MAY return `String` or a simple object directly, and Spring MCP SHALL automatically wrap that return value into a native MCP tool result. If only a subset of annotation-based tools must be disabled by configuration, they SHALL be registered through independently controllable beans or equivalent wiring so one tool can be disabled without hiding unrelated tools.

#### Scenario: Search and temp directory tools use annotation-based registration
- **WHEN** the server registers `search` and `create_temp_dir`
- **THEN** the implementation may use `@McpTool`-driven registration while still producing native MCP protocol results

#### Scenario: Annotation-based tool returns simple success value
- **WHEN** an annotation-based MCP tool returns a `String` or simple object as its success value
- **THEN** Spring MCP wraps that value into a protocol-native `CallToolResult` without requiring the service method to construct the result manually

#### Scenario: Search can be disabled without affecting temp directory tool
- **WHEN** runtime configuration disables the `search` MCP tool
- **THEN** the server does not register `search`
- **AND** `create_temp_dir` remains registered through its own annotation-based tool bean

## ADDED Requirements

### Requirement: Search MCP tool can remain disabled by configuration
The server SHALL keep the simplified `search` MCP tool implementation in code while allowing runtime configuration to disable its registration by default until the search backend is stable enough for exposure.

#### Scenario: Search tool disabled by default
- **WHEN** the application starts with default configuration
- **THEN** the `search` MCP tool implementation remains present in code but is not registered into the running MCP server

#### Scenario: Search tool can be enabled explicitly
- **WHEN** runtime configuration explicitly enables the `search` MCP tool
- **THEN** the server registers the annotation-based `search` MCP tool and exposes its simplified MCP response contract

### Requirement: Tool handlers delegate through domain services
Each MCP tool entry point SHALL validate protocol input and delegate execution to the existing domain services or facades without embedding transport-specific business logic in auto-configuration entry points. Explicit MCP handlers MAY parse `CallToolRequest` directly when needed, while annotation-based MCP tools MAY rely on method parameter binding and perform validation inside the annotated tool method before delegating.

#### Scenario: Search tool delegates after binding annotated arguments
- **WHEN** a client calls the `search` tool with query parameters
- **THEN** the annotation-based MCP tool binds the normalized values, validates them as needed, and delegates to the search service layer

#### Scenario: Temp directory tool delegates without manual protocol result construction
- **WHEN** a client calls the `create_temp_dir` tool
- **THEN** the annotation-based MCP tool delegates to the temp directory service layer and may return the created path directly without manually constructing a `CallToolResult`
