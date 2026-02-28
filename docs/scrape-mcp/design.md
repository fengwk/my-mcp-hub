# Scrape MCP Design (Current)

## 1. Scope

The `scrape` tool supports:

1. Structured web page extraction (`markdown` / `links`).
2. Browser screenshot capture (`screenshot` / `fullscreenshot`).
3. Direct media URL passthrough (image/video/audio/pdf/attachment) as data URI.
4. Manual login command (`mmh-cli open-browser`) for the configured master profile.

This document describes the **current implementation** and is the source of truth.

## 2. Runtime Architecture

### 2.1 Browser Runtime Layer

- `BrowserWorkerPool` is the unified base pool.
- `DefaultBrowserWorkerPool` (`slave_{pid}_{n}` profiles): concurrent, isolated workers.
- `MasterBrowserWorkerPool` (configured profile, default `master`): serialized single worker with cross-process lock.
- All workers use persistent user data directories under:
  - `${mmh.browser.master-user-data-root}`

### 2.2 Profile and Locking Model

- `default` mode -> default pool, auto-generated `slave_{pid}_{n}` profile IDs.
- `master` mode -> master pool, profile ID resolved from `mmh.browser.default-profile-id`.
- Cross-process mutual exclusion for master profile:
  - lock file path: `${master-user-data-root}/{default-profile-id}/browser.lock`
- `open-browser` command and master scrape runtime use the same lock path.

### 2.3 Open Browser Command Lifecycle

- Entry: `scripts/mmh-cli open-browser`
- Routing: starts `cli-all` with MCP server disabled.
- Flow:
  1. Resolve `profileId` from `mmh.browser.default-profile-id`.
  2. Acquire `browser.lock`.
  3. Launch headed persistent context for master profile.
  4. Wait until user closes browser pages/context or timeout.
  5. Exit process.

No snapshot bootstrap/publish subsystem is used in current architecture.

## 3. Scrape Output Semantics

## 3.1 Format Mapping

- `markdown` -> markdown content
- `links` -> extracted link list
- `screenshot` -> PNG data URI
- `fullscreenshot` -> full-page PNG data URI

`html` is no longer exposed at MCP tool layer. Passing `format=html` returns a validation error with supported formats.

## 3.2 Direct Media URL Short-Circuit

Before normal page navigation, scrape runtime probes target URL via request API:

- If response is direct media/attachment, return media data URI directly.
- This behavior is independent of requested `format`.

Detection rules:

1. `Content-Disposition` contains `attachment` -> media.
2. `Content-Type` starts with `image/`, `video/`, `audio/` or equals `application/pdf` -> media.
3. `Content-Type` is `application/octet-stream` -> media only when URL path ends with known media extensions.

Returned media payload:

- `format`: `media`
- `screenshotMime`: detected MIME
- `screenshotBase64`: `data:{mime};base64,...`

## 3.3 MCP Result Template

When response contains `screenshotBase64`, the MCP tool returns protocol-level content objects:

- Image media (`image/*`) -> `McpSchema.ImageContent`
- Non-image media -> `McpSchema.EmbeddedResource` with `McpSchema.BlobResourceContents`

For text formats, the tool returns `McpSchema.TextContent` with metadata header + body content.

## 4. Configuration Ownership

## 4.1 `mmh.browser` (browser runtime and profile domain)

Includes:

- Profile/runtime ownership:
  - `default-profile-id`
  - `master-user-data-root`
  - `profile-id-regex`
  - `worker-pool-min-size-per-process`
  - `worker-pool-max-size-per-process`
  - `queue-offer-timeout-ms`
  - `slave-headless`
- Master login command behavior:
  - `master-login-args`
  - `master-login-initial-page-url`
  - `master-login-navigate-timeout-ms`
  - `master-login-refresh-interval-ms`
  - `master-login-timeout-ms`
  - `master-profile-lock-timeout-ms`
- Browser launch/session tuning:
  - `browser-channel`, `executable-path`, `launch-args`
  - `ignore-default-args`, `ignore-all-default-args`
  - `user-agent`, `user-agents`, `accept-language`, `locale`, `timezone-id`
  - `extra-headers`, `proxy-*`
  - `stealth-enabled`, `stealth-script`

## 4.2 `mmh.scrape` (content extraction domain)

Includes:

- `navigate-timeout-ms`
- `smart-wait-enabled`
- `stability-check-interval-ms`
- `stability-max-wait-ms`
- `stability-threshold`
- `stability-length-change-threshold`
- `strip-chrome-tags`
- `remove-base64-images`

Notes:

- MCP `scrape` tool exposes optional `waitFor` (ms). When `waitFor > 0`, runtime uses fixed wait and skips smart wait.
- Runtime uses smart wait by default: do a short `networkidle` best-effort first, then poll text length and stop when change ratio stays within threshold for consecutive rounds.

## 5. Module Layout

- Single CLI runtime module: `cli/cli-all`
- `cli-util` has been removed.
- `mmh-cli util` is retained as a compatibility alias to `all`.

## 6. Verification Baseline

- `env JAVA_HOME=$JAVA_HOME_17 mvn -pl core clean test`
- `env JAVA_HOME=$JAVA_HOME_17 mvn -pl cli/cli-all -am package -DskipTests`

Both commands must pass before release.
