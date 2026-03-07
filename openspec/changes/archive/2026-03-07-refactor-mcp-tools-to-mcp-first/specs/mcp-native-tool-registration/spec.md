## ADDED Requirements

### Requirement: MCP tools use native Spring MCP registrations
The server SHALL expose `search`, `create_temp_dir`, and `scrape` through active Spring MCP native tool registrations, and SHALL NOT rely on `@Tool`/`ToolCallback`/`ToolCallbackProvider` bridging for these tools. The `skill` tool implementation SHALL also use native MCP registration primitives, but MAY remain disabled by runtime configuration.

#### Scenario: Server starts with native tool registrations
- **WHEN** the application context enables the MCP server
- **THEN** the context provides native MCP tool registrations for `search`, `create_temp_dir`, and `scrape`
- **AND** the `skill` MCP tool is registered only when its dedicated enable switch is set to true

### Requirement: Simple MCP tools may use annotation-based registration
Simple tools whose input schema can be fully expressed by method parameters and basic annotations SHALL use Spring MCP annotation-based registration to reduce boilerplate, unless explicit specifications are required by protocol-specific constraints.

#### Scenario: Search and temp directory tools use annotation-based registration
- **WHEN** the server registers `search` and `create_temp_dir`
- **THEN** the implementation may use `@McpTool`-driven registration while still producing native MCP protocol results

### Requirement: Complex MCP tools may keep explicit specifications
Tools with protocol-specific schema constraints, dynamic metadata generation needs, or content mapping complexity SHALL use explicit `McpServerFeatures.SyncToolSpecification` registrations.

#### Scenario: Scrape keeps explicit MCP specification
- **WHEN** the server registers `scrape`
- **THEN** the implementation may keep an explicit specification so enum/range constraints and protocol-level content mapping remain fully controlled

#### Scenario: Skill keeps explicit MCP specification for dynamic descriptions
- **WHEN** the server registers `skill`
- **THEN** the implementation may keep an explicit specification so the tool description can be generated from the currently loaded skill catalog while the result remains protocol-native

### Requirement: Skill MCP tool can remain disabled by configuration
The server SHALL keep the refactored `skill` MCP tool implementation in code, while allowing runtime configuration to disable its registration by default until the feature is ready for exposure.

#### Scenario: Skill tool disabled by default
- **WHEN** the application starts with default configuration
- **THEN** the `skill` MCP tool implementation remains present in code but is not registered into the running MCP server

#### Scenario: Skill tool can be enabled explicitly
- **WHEN** runtime configuration explicitly enables the `skill` MCP tool
- **THEN** the server registers the native MCP `skill` tool specification

### Requirement: Tool handlers delegate through domain services
Each MCP tool handler SHALL parse protocol input from `CallToolRequest`, perform protocol-layer validation, and delegate execution to the existing domain services or facades without embedding transport-specific business logic in auto-configuration entry points.

#### Scenario: Search tool delegates after parsing request arguments
- **WHEN** a client calls the `search` tool with query parameters
- **THEN** the MCP handler validates the request arguments and delegates the normalized values to the search service layer

#### Scenario: Temp directory tool delegates without string bridge conversion
- **WHEN** a client calls the `create_temp_dir` tool
- **THEN** the MCP handler delegates to the temp directory service layer and builds the protocol result directly without a `ToolCallback` result converter
