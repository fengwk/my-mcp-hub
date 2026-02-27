# AGENTS.md

This file guides agentic coding agents working in this repository.

## Project overview
- Multi-module Maven repository targeting Java 17.
- Modules:
  - core (Spring Boot MCP server logic and shared utilities)
  - cli (aggregator module)
  - cli/cli-all (CLI app bundling all tools)
- Lombok is enabled via `lombok.config` at repo root.

## Repository layout
- `core/src/main/java`: main application and library code.
- `core/src/test/java`: unit and integration tests.
- `core/src/main/resources`:
  - `mcp/templates/*.ftl` FreeMarker templates.
  - `logback-spring.xml` logging configuration.
  - `string.properties`, `string_zh_CN.properties` i18n bundles.
- `cli/cli-all/src/main/resources/application.yml`: CLI config.
- `scripts/`: helper scripts (build/run CLI jars).

## Toolchain
- Use JDK 17 only.
- Always run Maven with explicit JAVA_HOME.
- Maven wrapper is not present; use system `mvn`.

## Build commands
- Full build (all modules):
  - `env JAVA_HOME=$JAVA_HOME_17 mvn clean verify`
- Build without tests:
  - `env JAVA_HOME=$JAVA_HOME_17 mvn clean package -DskipTests`
- Build a single module with dependencies:
  - `env JAVA_HOME=$JAVA_HOME_17 mvn -pl core -am package`
- Build CLI modules:
  - `env JAVA_HOME=$JAVA_HOME_17 mvn -pl cli -am package`
- Use the helper build script (skips tests):
  - `scripts/app build`

## Lint/format
- No dedicated formatter or lint config found in this repo.
- Run `mvn verify` to trigger any checks configured by `convention4j-parent`.
- Avoid reformatting entire files; follow existing style.

## Test commands
- All tests (all modules):
  - `env JAVA_HOME=$JAVA_HOME_17 mvn test`
- Tests for a module only:
  - `env JAVA_HOME=$JAVA_HOME_17 mvn -pl core test`
- Single test class:
  - `env JAVA_HOME=$JAVA_HOME_17 mvn -pl core -Dtest=SearchFacadeImplTest test`
- Single test method:
  - `env JAVA_HOME=$JAVA_HOME_17 mvn -pl core -Dtest=SearchFacadeImplTest#shouldReturnBadRequestWhenQueryBlank test`
- Integration tests use `*IT` naming.
  - If Failsafe is enabled by the parent, run with:
    - `env JAVA_HOME=$JAVA_HOME_17 mvn verify`
- Single integration test (if Failsafe is enabled):
  - `env JAVA_HOME=$JAVA_HOME_17 mvn -pl core -Dit.test=SearchFacadeIT verify`
- Spring Boot integration tests use `@SpringBootTest` and may require network access.

## Run commands
- Run a CLI jar built under `cli/cli-*/target`:
  - `scripts/mmh-cli all`
- The script prefers `JAVA_HOME_17` when available.

## Code style
### Formatting
- Indent 4 spaces; K&R braces on the same line.
- Align chained builder calls on new lines with consistent indentation.
- Keep lines reasonably short; use Java text blocks for long strings.
- Use blank lines between import groups and logical code blocks.

### Imports
- Group imports with blank lines: project -> third-party -> java.
- Place static imports last in their own group.
- Avoid wildcard imports.

### Naming
- Packages are all-lowercase, dot-separated.
- Classes and interfaces use UpperCamelCase.
- Methods and variables use lowerCamelCase.
- Constants use UPPER_SNAKE_CASE.
- Test classes use `*Test` for unit tests and `*IT` for integration tests.

### Lombok usage
- Prefer Lombok for boilerplate: `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`.
- `lombok.config` enforces `equals/hashCode` and `toString` to call super.
- Use `final` fields for dependencies with constructor injection.

### Spring and configuration
- Use `@Component` and `@SpringBootApplication` where appropriate.
- Configuration properties use `@ConfigurationProperties` with `mmh.*` prefix.
- Prefer constructor injection over field injection.
- Keep auto-configuration imports in:
  - `core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Error handling
- Validate inputs early; return a typed error in response objects when possible.
- For invalid configuration, throw `IllegalArgumentException` with clear context.
- Avoid swallowing exceptions; include the message when returning an error.

### Logging
- Use `@Slf4j` and parameterized logging (`log.info("x: {}", x)`).
- Avoid logging secrets or tokens.
- Keep logs concise; tests may log extra diagnostics when useful.

### Collections and nulls
- Use `List.of` / `Map.of` for small immutable collections.
- Prefer `StringUtils.isBlank` / `isNotBlank` for string checks.
- Avoid returning null collections; return empty lists when feasible.

### JSON and HTTP
- JSON parsing uses Jackson `ObjectMapper`.
- Keep JSON node parsing defensive (null checks).
- HTTP client proxy settings are parsed from strings; validate carefully.

### MCP tool definitions
- MCP tools use `@Tool` and `@ToolParam` with clear descriptions.
- For multi-line descriptions, use Java text blocks.
- Format tool output via `McpFormatter` and FreeMarker templates.

### Tests
- Use JUnit 5 (`org.junit.jupiter`), Mockito, and AssertJ.
- Use `@ExtendWith(MockitoExtension.class)` for unit tests with mocks.
- Use `@SpringBootTest` for integration tests.
- Prefer descriptive test method names (e.g. `shouldReturnBadRequestWhenQueryBlank`).
- Keep tests deterministic; minimize reliance on external services.

## Cursor/Copilot rules
- No `.cursor/rules/`, `.cursorrules`, or `.github/copilot-instructions.md` found.

## When in doubt
- Read nearby implementations in `core/src/main/java` and follow the pattern.
- Favor minimal, localized changes that preserve established conventions.
