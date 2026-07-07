# MCP 协议：从"买彩票"到"买定离手"——AI 工具协议开发指南

> **编号**：HUICAI-REF-002
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：添加编号头部

---

## 一、先讲个故事：彩票机房

假设你开了个彩票机房，里面摆着三台机器：**摇奖机**、**兑奖机**、**打印彩票机**。

一开始，这三台机器是独立的，每台都有自己的操作面板：

```
摇奖机：红色按钮启动，绿色按钮停止
兑奖机：扫码枪扫条形码，屏幕显示结果
彩票机：键盘输入号码，按打印键出票
```

你需要记住每个机器的操作方式，还要亲自跑过去按按钮。**这就像没有 MCP 的 AI 开发**——每次要操作 Git 就用命令行，要看文件就用 cat，要查 Issues 就打开网页，工具之间没有统一接口。

后来，你给每台机器装了一个**统一遥控器**：

```
遥控器上只有三个按钮：
  【摇奖】→ 按一下，摇奖机自动启动
  【兑奖】→ 扫一下码，兑奖结果返回
  【打票】→ 输入号码，彩票自动打印
```

**这就是 MCP 做的事情**：给所有工具一个统一的接口协议，AI 不需要知道每个工具的具体操作方式，只需要通过 MCP 发送标准指令，工具自动执行并返回结果。

---

## 二、什么是 MCP？

### 2.1 官方定义

**Model Context Protocol（MCP）** 是由 Anthropic 提出的一种开放协议，它定义了 AI 模型（客户端）与外部工具/数据源（服务器端）之间的标准通信方式。

简单说：MCP = **AI 世界的 USB-C 接口**。

### 2.2 核心概念

```
┌──────────────┐      MCP 协议       ┌──────────────┐
│              │ ◄─────────────────► │              │
│   AI 模型    │     JSON-RPC        │    MCP 服务器  │
│  (客户端)    │      STDIO/SSE      │   (工具端)     │
│              │                     │              │
└──────────────┘                     └──────────────┘
```

| 概念 | 类比 | 说明 |
|------|------|------|
| **MCP Client** | 遥控器 | AI 模型，发送标准指令 |
| **MCP Server** | 彩票机 | 实际执行工具，每个 server 负责一类功能 |
| **Tool** | 机器上的按钮 | 具体的操作（git_status、create_issue 等） |
| **Transport** | 遥控器信号 | 通信方式（STDIO=有线，SSE=无线） |
| **JSON-RPC** | 遥控器编码 | 标准化的消息格式 |

### 2.3 MCP 的通信流程

```
1. 初始化阶段
   AI 模型 → "你好，你有哪些工具？"
   MCP 服务器 → "我有 git_status、git_log、git_commit 三个工具"

2. 调用阶段
   AI 模型 → "调用 git_status"
   MCP 服务器 → "当前分支 ai-evolution，有 2 个未提交文件"

3. 完成阶段
   AI 模型 → "断开连接"
   MCP 服务器 → "再见"
```

---

## 三、为什么要用 MCP？

### 3.1 没有 MCP 之前

```
AI 开发一个功能，需要：

1. 用 terminal 工具执行 "git status"          ← 每次都要手动写命令
2. 用 terminal 工具执行 "git diff --stat"     ← 命令格式记不住
3. 用 terminal 工具执行 "git commit -m 'xxx'" ← 路径错了就失败
4. 打开浏览器访问 GitHub 创建 Issues          ← 手动操作
5. 用 cat 命令查看文件                         ← 路径要完全正确

问题：
- 每个工具的操作方式完全不同
- 命令拼写错误、路径错误频繁发生
- 无法标准化、自动化
- 每次都要重新"学习"工具的使用方式
```

### 3.2 有了 MCP 之后

```
同一个功能，AI 只需要：

1. 调用 MCP 工具 git_status()            ← 标准化接口
2. 调用 MCP 工具 git_diff()              ← 参数明确
3. 调用 MCP 工具 git_commit()             ← 自动处理路径
4. 调用 MCP 工具 create_issue()          ← 无需浏览器
5. 调用 MCP 工具 read_file()             ← 路径自动映射

优势：
- 统一接口：所有工具都是 "调用→返回" 模式
- 标准化：参数类型、返回格式严格定义
- 权限控制：能限制工具只能操作特定目录
- 可组合：多个 MCP 服务器可以协同工作
- 可发现：AI 模型可以自动发现可用的工具
```

### 3.3 核心价值

| 维度 | 无 MCP | 有 MCP |
|------|--------|--------|
| **学习成本** | 每次新工具都要学新命令 | 一次学会，所有工具通用 |
| **错误率** | 路径写错、参数写错很常见 | 标准化参数校验，错误率低 |
| **可复用性** | 每个项目重新配置 | 一次配置，到处复用 |
| **权限控制** | 全系统权限，风险高 | 限制目录范围，安全可控 |
| **可发现性** | 不知道工具能做什么 | 自动列出所有可用工具 |
| **组合能力** | 工具之间无法通信 | 多个 MCP 服务器协同工作 |

---

## 四、项目中的 MCP 落地实践：慧财财务系统

### 4.1 MCP 服务器架构

```
┌─────────────────────────────────────────────────────────────┐
│                    AI 模型（Hermes）                         │
│                                                             │
│  通过 MCP 协议统一调度以下服务器...                          │
└─────────────────────────────────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  project-git │  │  git-manager │  │  project-fs  │
│              │  │              │  │              │
│  本地 Git 操作│  │  GitHub API  │  │  文件系统     │
│  status/diff │  │  Issues/PRs  │  │  src/tests/  │
│  commit/log  │  │  代码搜索    │  │  harness/    │
└──────────────┘  └──────────────┘  └──────────────┘
```

### 4.2 配置清单

| MCP 服务器 | 协议 | 用途 | 状态 |
|-----------|------|------|------|
| **project-git** | `mcp-server-git` (Python) | 本地 Git 版本管理 | ✅ 已启用 |
| **git-manager** | `@modelcontextprotocol/server-github` (Node.js) | GitHub Issues/PRs/代码搜索 | ✅ 已启用 |
| **project-fs** | `@modelcontextprotocol/server-filesystem` (Node.js) | 限制文件操作范围 | ✅ 已启用 |
| **project-tracker** | `@modelcontextprotocol/server-jira` (Node.js) | Jira 看板同步 | ⏸️ 待启用 |

### 4.3 关键配置

```yaml
# ~/.hermes/config.yaml
mcp_servers:
  # 本地 Git 操作
  project-git:
    command: /root/.hermes/hermes-agent/venv/bin/mcp-server-git
    args: ["--repository", "/data/disk/huicai"]
    enabled: true

  # GitHub API 操作
  git-manager:
    command: npx
    args: ["-y", "@modelcontextprotocol/server-github"]
    env:
      GITHUB_PERSONAL_ACCESS_TOKEN: "${GITHUB_TOKEN}"
    enabled: true

  # 限制文件系统范围
  project-fs:
    command: npx
    args: ["-y", "@modelcontextprotocol/server-filesystem",
           "/data/disk/huicai/backend/src",
           "/data/disk/huicai/ai-service/app",
           "/data/disk/huicai/frontend/src",
           "/data/disk/huicai/harness",
           "/data/disk/huicai/docs"]
    enabled: true
```

### 4.4 实际效果：52 个 MCP 工具

```
MCP 服务器        工具数量                  能力范围
─────────────────────────────────────────────────────
project-git     →  12 个工具     git_status/diff/log/commit
git-manager     →  20+ 个工具    create_issue/pull_request/search_code
project-fs      →  20+ 个工具    read_file/write_file/search_files
─────────────────────────────────────────────────────
总计            52 个 MCP 工具   覆盖开发全流程
```

---

## 五、MCP 与"买彩票"的关系

### 彩票机房的升级版

回到开头的彩票机房。你的彩票机房现在有 6 台机器，每台机器都需要不同的操作方式：

```
摇奖机  →   红色按钮(启动) + 绿色按钮(停止)
兑奖机  →   扫码枪(扫条形码)
彩票机  →   键盘(输入号码) + 打印键(出票)
开奖机  →   触摸屏(选择期数)
统计机  →   鼠标(点击报表)
广播机  →   麦克风(喊话)
```

你每天要在这 6 台机器之间来回跑，**每台机器都有自己的操作方式**。更糟的是，有时候你记错了操作方式，按错了按钮，机器就报错。

**这就好比"买彩票"——每次操作都像碰运气，你不知道这次能不能成功。**

### MCP 就是统一遥控器

MCP 给每台机器装了一个**统一遥控器**，遥控器上只有几个标准按钮：

```
【执行操作】  →  输入: 操作名称 + 参数  →  输出: 结果
【查询状态】  →  输入: 无              →  输出: 当前状态
【列出工具】  →  输入: 无              →  输出: 所有可用操作
```

现在你不需要记住每台机器的操作方式了，**只需要按遥控器上的标准按钮**，机器自动执行。

**这就是"买定离手"**——你不需要管机器内部怎么运作，只需要告诉 MCP 你要什么，它自动帮你完成。

### 从"碰运气"到"确定性"

```
买彩票模式（无 MCP）：
  "我记得 git 的 commit 命令是 git commit -m..."
  "等等，--message 还是 -m？"
  "哦，还要 git add... 忘了 add 了"
  → 报错，重来，再试一次

买定离手模式（有 MCP）：
  "调用 MCP 工具 git_commit，参数：message=xxx"
  → MCP 自动处理 add + commit，返回结果
  → 确定性，不会出错
```

---

## 六、MCP 的生态与未来

### 6.1 当前生态

| 类型 | 代表 MCP 服务器 | 能力 |
|------|----------------|------|
| **版本管理** | `server-github`、`server-git` | Issues/PRs/Commits/Code Search |
| **文件系统** | `server-filesystem` | 受限文件读写、目录浏览 |
| **项目管理** | `server-jira`、`server-linear` | 任务看板、Sprint 管理 |
| **数据库** | `server-postgres`、`server-sqlite` | SQL 查询、Schema 浏览 |
| **浏览器** | `server-playwright` | 网页操作、截图、自动化 |
| **AI 平台** | `server-huggingface` | 模型推理、数据集搜索 |

### 6.2 发展趋势

1. **标准化**：MCP 正在成为 AI Agent 领域的标准协议，类似 HTTP 对 Web 的意义
2. **工具化**：越来越多的 SaaS 平台提供 MCP 接口（GitHub、Slack、Jira 等）
3. **组合化**：多个 MCP 服务器可以组合成复杂的工作流
4. **安全化**：MCP 的权限控制机制越来越完善

---

## 七、总结

| 问题 | 回答 |
|------|------|
| **MCP 是什么？** | AI 模型与外部工具之间的标准通信协议，类比"USB-C 接口" |
| **为什么要用？** | 统一工具接口、降低错误率、标准化权限控制、可自动发现工具 |
| **怎么用？** | 配置 `~/.hermes/config.yaml` 的 `mcp_servers` 段，添加 MCP 服务器 |
| **效果如何？** | 本项目已配置 4 个 MCP 服务器，提供 52 个标准化工具 |
| **和买彩票什么关系？** | 没有 MCP 是"碰运气"，有了 MCP 是"买定离手"——确定性操作 |

---

> **附录：慧财财务系统 MCP 配置备忘**
>
> 配置位置：`~/.hermes/config.yaml` → `mcp_servers` 段
> 项目级别配置：`/data/disk/huicai/.mcp.json`
> 环境变量：`~/.hermes/.env`（GITHUB_TOKEN、JIRA_URL、JIRA_TOKEN）
> 重新加载：`/reload-mcp`（会话内命令）
> 重启：`/reset`（新会话）
