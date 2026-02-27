# my-mcp-hub

`my-mcp-hub` 是一个基于 Spring AI MCP 的 CLI 工具集合，提供搜索、网页抓取、临时目录等能力，面向本地 Agent / MCP Client 通过 `stdio` 调用。

## 功能概览

- `search`：网页搜索，返回标题、URL、摘要。
- `scrape`：网页抓取，支持 `markdown/html/links/screenshot/fullscreenshot`。
- `create_temp_dir`：创建独占临时目录并返回绝对路径。

## 项目结构

- `core`：MCP 工具定义与运行时实现（Playwright、抓取逻辑、模板格式化）。
- `cli/cli-all`：统一 CLI 启动入口。
- `scripts`：构建与运行脚本（含 Linux/macOS Bash + Windows PowerShell/CMD）。

当前 Maven 模块（JDK 17）：

- 根模块：`core`、`cli`
- CLI 子模块：`cli/cli-all`

## 环境要求

- JDK 17（必须）
- Maven 3.8+
- Linux/macOS 或 Windows（PowerShell 5+）

> 说明：Playwright 运行时依赖操作系统图形库。Linux 如出现 host dependency warning，请按 Playwright 提示安装系统依赖。

## 快速开始

### Linux / macOS

1. 构建（推荐先执行）

```bash
env JAVA_HOME=$JAVA_HOME_17 mvn clean verify
```

2. 启动 MCP（stdio）

```bash
./scripts/mmh-cli all
```

3. 打开 master 登录浏览器（仅登录，不启动 MCP 服务）

```bash
./scripts/mmh-cli open-browser
```

### Windows

1. 构建

```powershell
$env:JAVA_HOME = $env:JAVA_HOME_17
mvn clean verify
```

2. 启动 MCP（stdio）

```cmd
scripts\mmh-cli.cmd all
```

3. 打开 master 登录浏览器（仅登录）

```cmd
scripts\mmh-cli.cmd open-browser
```

## 脚本说明

- `scripts/mmh-cli`：Linux/macOS 启动 CLI MCP。
- `scripts/mmh-cli.ps1` + `scripts/mmh-cli.cmd`：Windows 启动 CLI MCP。
- `scripts/app`：仓库级辅助脚本（常用 `build`）。
- `scripts/app.ps1` + `scripts/app.cmd`：`app` 的 Windows 版本。

> 注意：`scripts/app start` 依赖 `web/target/*.jar`，当前仓库无 `web` 模块。MCP CLI 运行请使用 `scripts/mmh-cli`。

## MCP Tools 说明

### 1) `search`

- 参数：
  - `query`（必填）
  - `limit`（可选，默认 10）
  - `timeRange`（可选：`day/week/month/year`）
  - `page`（可选，默认 1）
- 返回：结果列表或错误信息。

### 2) `scrape`

- 参数：
  - `url`（必填，仅 `http/https`）
  - `format`（可选，默认 `markdown`）
  - `profileMode`（可选，`default/master`）
  - `onlyMainContent`（可选，默认 `false`）
- 支持格式：`markdown/html/links/screenshot/fullscreenshot`
- 行为特性：
  - 默认启用 smart wait（内容稳定检测）
  - 直链媒体 URL（图片/音视频/pdf/附件）直接返回媒体 data URI
  - screenshot/fullscreenshot 返回图片 data URI

### 3) `create_temp_dir`

- 无参数，返回临时目录绝对路径。
- 适合下载/解压/中间文件隔离。

## 关键配置

主配置文件：`cli/cli-all/src/main/resources/application.yml`

重点配置项：

- MCP 服务器
  - `spring.ai.mcp.server.request-timeout`（默认 `45s`）
- 浏览器池（并发容量）
  - `mmh.browser.worker-pool-max-size-per-process`（默认 `5`）
  - `mmh.browser.queue-offer-timeout-ms`（默认 `15000`）
- 抓取策略
  - `mmh.scrape.navigate-timeout-ms`（默认 `30000`）
  - `mmh.scrape.smart-wait-enabled`（默认 `true`）
  - `mmh.scrape.stability-check-interval-ms`（默认 `1000`）
  - `mmh.scrape.stability-max-wait-ms`（默认 `15000`）
  - `mmh.scrape.stability-threshold`（默认 `2`）

## 日志与清理策略

`mmh-cli` 会将日志写入：

- 默认根目录：`~/.my-mcp-hub/logs`
- 每进程隔离目录：`agent-<id>`（默认 `<id>` 为进程 pid）

可用环境变量：

- `MMH_LOG_BASE_DIR`：日志根目录
- `MMH_AGENT_ID`：自定义 agent 目录标识
- `MMH_LOG_RETENTION_DAYS`：日志保留天数（正整数，默认 7）
- `MMH_LOG_CLEAN_ALLOW_OUTSIDE_DEFAULT`：是否允许清理默认目录外路径（`true/false`）

安全规则：

- 只会自动清理 `agent-<pid>` 格式目录
- 对应 pid 仍在运行时不会删除目录
- 非默认日志根目录默认不清理（除非显式放开）

## 验证命令

```bash
# core 测试
env JAVA_HOME=$JAVA_HOME_17 mvn -pl core test

# CLI 打包（带依赖）
env JAVA_HOME=$JAVA_HOME_17 mvn -pl cli -am package -DskipTests

# 全量构建
env JAVA_HOME=$JAVA_HOME_17 mvn clean verify
```

## 常见问题

### 1) `slave_<pid>_<n>` 目录为什么不立即消失？

这是默认 worker 复用设计。运行中 worker 保活时目录会存在；进程退出或僵尸目录清理阶段会回收。

### 2) `default browser worker pool is busy` 怎么处理？

优先调大并发容量与等待时间：

- `mmh.browser.worker-pool-max-size-per-process`
- `mmh.browser.queue-offer-timeout-ms`

### 3) 某些站点偶发 `MCP error -32001: Request timed out`？

先确认网络可达，再结合站点特性调整：

- `spring.ai.mcp.server.request-timeout`
- `mmh.scrape.navigate-timeout-ms`
- `mmh.scrape.stability-max-wait-ms`
