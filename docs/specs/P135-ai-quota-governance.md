# SPEC-P135 —— AI 端点配额治理（成本护栏，非业务授权）

> **状态**：草案，待老丁审核（铁律 #10 三步闭环）
> **REQ**：`REQ-2026-151`
> **优先级**：P1（不涉越权，但**直接对外付费**）
> **前置**：由 `SPEC-P133` D-10 第 3 条裁定派生 —— AI 端点**不建业务权限码**，单独治理
> **竞品对标**（铁律 #15）：用友 YonSuite、金蝶云星空的 AI 能力均按**租户/账套的调用额度**
> 计费与限流（而非按角色授权）—— 因为 AI 的约束是**成本**，不是**职责**。

---

## §0 取证（实测，非推断）

### 0.1 现状：11 个写端点，**零授权、零配额**

| 控制器 | 写端点 |
|---|---|
| `NvidiaAiController` | `chat`、`chatWithSystem`、`analyzeImage`、`analyzeVideo`、`analyzeMultipleImages` |
| `AiTaskController` | `createTask`、`review`、`resolve`、`tagAnomaly` |
| `AiFeedbackLogController` | `create`、`deleteByBankTxn` |

- **全部无 `@PreAuthorize`**（实测 grep 零命中）
- **不在 `SecurityConfig` 的 permitAll 白名单**（白名单仅 9 条：login/refresh/test-password/
  health/actuator/swagger 等）⇒ 至少需登录
- **全仓无任何配额/限流设施**（实测 `RateLimit|quota|Semaphore|限流|配额` **零命中**）

⇒ **任何已登录用户可无限次调用**，而这些调用会**转发到外部付费 API**（NVIDIA NIM）。

### 0.2 可复用的基础设施

`RedisTemplate` / `StringRedisTemplate` 已在 4+ 处业务中使用（`VoucherNoServiceImpl`、
`OutputInvoiceStateMachineServiceImpl`、`InputInvoiceStateMachineServiceImpl` 等）
⇒ **无需引入新中间件**，用 Redis 计数器即可。

---

## §1 为什么**不用业务权限码**（D-10 第 3 条裁定的依据）

AI 能力不是「职责」，是「**成本**」：

| 维度 | 业务权限 | AI 配额 |
|---|---|---|
| 约束的东西 | 谁能改账 | 谁能花钱、花多少 |
| 粒度 | 角色（ACCOUNTANT/FINANCE_MGR…） | 账套/租户的**用量** |
| 用业务码的后果 | — | 出纳与财务主管都该能调 AI，**按角色分权会让日常核销寸步难行** |
| 不做的后果 | — | 单个账号循环调用即可**烧掉整月预算** |

⇒ 结论：AI 端点**不进权限码体系**（不进 `t_menu`、不进清单 `endpoints`），
改由**独立配额层**治理。

⚠️ 但**必须承认代价**：配额层不管「谁调」，只管「调多少」。
因此**登录认证仍然必须**（现状已满足：不在 permitAll 白名单）。

---

## §2 方案：Redis 日配额 + 企业维度

### 2.1 额度模型

```
quota key : ai:quota:{enterpriseId}:{yyyyMMdd}
类型      : String 计数器（INCR + EXPIRE）
额度      : 配置项 ai.quota.daily（默认见 §4）
豁免      : ADMIN 角色不计数（管理员要能排查问题，不能被配额挡住）
```

### 2.2 三条分支

| 情形 | 行为 |
|---|---|
| 未超额度 | 放行，`INCR` +1 |
| **超额度** | **429**「今日 AI 调用额度已用尽（X/Y），明日 0 点重置」 |
| **Redis 不可用** | 🔴 **fail-open 放行 + 告警**（见 §5 取舍） |
| ADMIN | 直接放行，不计数 |

### 2.3 额度查询端点

`GET /api/ai/quota` 返回 `{ used, limit, resetAt }`，供前端展示「今日还剩几次」。
**只读端点，不消耗额度**（否则查一次就扣一次）。

---

## §3 输入 / 输出契约

### 3.1 配置（`application.yml`）

```yaml
ai:
  quota:
    enabled: true
    daily: 200          # 每账套每日 AI 调用上限
    exempt-roles: [ADMIN]
```

⚠️ **不给默认值兜底**：`daily` 缺失时启动失败（与 `enforcement` 字段同思路）——
配额值是业务决策，写死在代码里会让人以为「200 是有依据的」。

### 3.2 输出契约（超限）

```json
{ "code": 429, "msg": "今日 AI 调用额度已用尽（200/200），明日 0 点重置" }
```

---

## §4 待老丁裁定

| # | 议题 | 我的建议 |
|---|---|---|
| **D-1** | 每日额度初值 | 🔴 **必须老丁定**。这是花钱的数字，我不定。建议先按 **200 次/账套/日** 试运行，观察一周真实用量再调 |
| **D-2** | 计数维度：按**账套**还是按**用户** | 建议**按账套** —— 成本由账套承担，且避免「每人 200」的 N 倍放大 |
| **D-3** | Redis 挂了 ⇒ fail-open 还是 fail-closed | 建议 **fail-open + 告警**：配额是**成本护栏**不是**安全边界**，为它阻断财务业务不划算。但须在告警里明确「护栏已失效」 |
| **D-4** | ADMIN 是否豁免 | 建议**豁免**（否则管理员无法排查 AI 故障）；代价是 ADMIN 可无上限调用 —— 已登记 |
| **D-5** | 超限后是否给「提额」入口 | 本期**不做**，先观察。避免又造一个权限口子 |

---

## §5 已知局限（§7.7）

1. 🔴 **配额不是授权** —— 任何登录用户都能用完整个账套的额度（**拒绝服务**面）。
   本期接受；后续可加「每用户上限」作为二级。
2. **按自然日重置，不按滚动窗口** —— 23:59 用满 200 次，00:01 又有 200 次。
   本期接受（实现与解释都简单得多）。
3. **Redis 挂 ⇒ 无护栏**（D-3）。告警是唯一提示。
4. **ADMIN 无上限**（D-4）。
5. **未覆盖对外费用之外的滥用** —— 例如超大图片/长视频导致的**单次高价**，
   本期只管次数不管体积。若 NVIDIA 按 token/秒计费，需另加**单次体积上限**。

---

## §6 验收（BDD）

| ID | Given-When-Then |
|---|---|
| AT-135-1 | 额度未用尽 ⇒ 调用**成功**且 `used` +1 |
| AT-135-2 | 用满额度 ⇒ 返回 **429** 且消息含 `used/limit` |
| AT-135-3 | **负向断言**：被 429 后 **AI 任务未被创建**（不能只验响应码） |
| AT-135-4 | `GET /api/ai/quota` **不消耗额度** |
| AT-135-5 | **跨日重置**：key 含日期 ⇒ 次日计数归零（新 key） |
| AT-135-6 | Redis 不可用 ⇒ **放行**（fail-open）且产生告警日志 |
| AT-135-7 | ADMIN 豁免：连续调用 > 额度仍**放行** |
| AT-135-8 | **两个账套互不影响**：账套 A 用满不影响账套 B |

⚠️ **AT-135-8 不可省**：若 key 漏了 `enterpriseId`，则**一个账套用满会拖垮所有账套**。

---

## §7 明确排除

- ❌ 不给 AI 端点建业务权限码（D-10 第 3 条裁定）
- ❌ 不做「提额」审批入口（D-5）
- ❌ 不做单次调用体积上限（§5 局限 5，另立）
- ❌ 不改 AI 输出「必须人工确认才能入账」的既有铁律 #2

---

### D-1~D-5 裁定（老丁 2026-10-17）

| # | 裁定 |
|---|---|
| **D-1** | 🔴 **每日额度 = 200 次 / 账套 / 日**（「同意 200 次」）。⚠️ 仍登记为**试运行初值**，建议一周后按真实用量复核 |
| **D-2** | 计数维度**按账套**（`enterpriseId`），ADMIN 豁免 |
| **D-3** | Redis 不可用 ⇒ **fail-open + 告警**（护栏失效但不阻断业务） |
| **D-4** | **豁免实现**：持有 `system:user:list` 者不计数。🔴 这是「ADMIN 豁免」的**代理实现** —— `LoginUser` **不含平台角色**（实测只有 `userType`/`agencyId`/`agencyRole`），而 authorities 里放的是**权限码**（P131 实测）；故只能取一个**仅 ADMIN 持有**的码（实测持有者 = 1）做代理。⚠️ 若将来该码授予更多角色，豁免范围会随之扩大 |
| **D-5** | 本期**不做**提额入口 |

### 实施结果（2026-10-17）

**新增 4 个类**：`AiQuotaProperties` / `AiQuotaGuard` / `AiQuotaInterceptor` / `AiQuotaController`
（`GET /api/ai/quota` 查询**不消耗**额度）。
配置写入 `application.yml` 与 `src/test/resources/application.yml` 两处。

**守卫**：
- `AiQuotaGuardRealDBTest` **9 例**（打真 Redis）—— AT-135-1~8 + 结构守卫
- `AiQuotaConfigMirrorTest` **2 例**（配置镜像守卫）
- ✅ **反证已做且成立**：从 key 中移除 `enterpriseId` ⇒ **AT-135-8 转红**
  （证明「一个账套用满会拖垮所有账套」这一风险被真实守住）

#### 🔴 本轮踩的三个坑，其中两个是「同一个坑第二次咬人」

**① 🔴 切片上下文坑（P133 已沉淀，本轮仍踩）**
`AiQuotaController` 是 `@RestController`（在 `@WebMvcTest` 白名单内），
但它依赖的 `AiQuotaGuard` 是普通 `@Component`（**切片不扫**）
⇒ **一次性 97 个用例全挂** `NoSuchBeanDefinitionException: AiQuotaGuard`。
且 `AiQuotaInterceptor` 恰好**也在**白名单里（它实现了 `HandlerInterceptor`）⇒ 同样受影响。
正解与 P133 一致：`ObjectProvider` **可选依赖**，切片里没有就**不安装执行**。
> **判据：新增的拦截器/控制器若依赖非白名单 bean，一律用可选依赖。**
> ⚠️ 这个坑的特征是「一次改动挂掉近百家用例」，且报错只说「缺 bean」、
> 完全不提「谁依赖了谁」—— 排查成本极高。

**② 🔴 测试配置同名遮蔽主配置**
`src/test/resources/application.yml` 是**独立的最小测试配置**（H2），
它与主配置**同名**且在 classpath 上**优先** ⇒ 主配置的键在测试里**全部不可见**。
症状极具欺骗性：报 `Field error on field 'daily': rejected value [null]`，
看起来像「配置写错了」或「`@NotNull` 用错地方」，真实原因是**测试根本没读那份配置**。

🔴 **若当初没给 `daily` 加 `@NotNull`**：该键会静默缺省为 null ⇒
`limit <= 0` ⇒ 配额守卫**一律拒绝或一律放行**，AT-135-2（超限 429）会
以「额度为 0 ⇒ 一律 429」的方式**假绿** —— 而真机上配额从未被测过。

⇒ 新增 **`AiQuotaConfigMirrorTest`** 断言两份配置都声明 `ai.quota.daily`
且**默认值一致**。

> **判据沉淀：当一份配置<b>同名地</b>存在多份（主/测试/环境），
> 「必填校验」的实际覆盖面<b>只等于被加载的那一份</b>。
> 凡「必填」是设计的一部分，就必须<b>逐份声明 + 机器校验</b>。**

**③ key 格式与 SPEC 逐字一致**
首版实现用 ISO 日期（`2026-10-17`），SPEC 写 `yyyyMMdd`。
两者都能实现「跨日重置」，但不一致会让**按 SPEC 写的运维排查命令**
（如 `redis-cli get ai:quota:1:20261017`）查不到数据 ⇒ 已统一为 `yyyyMMdd`。

（另：另有两处**守卫自身**的错 —— 配置解析器 `break` 写错导致误报「主配置缺 exempt-permissions」；
结构守卫误把「控制器必须是 AiQuotaController」当作覆盖条件，而配额实际按**路径前缀**覆盖。）


---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: AiQuotaGuard
module: ai
table: null

rules:
  - id: C-01
    source: "P135 §2.1"
    rule: "AI 调用按 enterpriseId 维度做每日配额计数，key 含日期以实现自然日重置"
    mapping: { key: "ai:quota:{enterpriseId}:{yyyyMMdd}", counter: INCR, ttl: "次日 0 点后过期" }
    implementation: "AiQuotaGuard"
  - id: C-02
    source: "P135 §2.2"
    rule: "超额度返回 429 且消息含 used/limit；被拒后不得产生任何副作用"
    mapping: { http: 429, payload: [used, limit, message] }
    implementation: "AiQuotaGuard + AiQuotaController"
  - id: C-03
    source: "P135 §2.2 / §5"
    rule: "Redis 不可用时 fail-open 放行并告警 —— 配额是成本护栏，不是安全边界"
    mapping: { on_redis_error: allow_and_warn }
    implementation: "AiQuotaGuard"
  - id: C-04
    source: "P135 §3.1"
    rule: "配额上限必须来自配置且缺失时拒绝启动，不得使用代码内默认值"
    mapping: { config: ai.quota.daily, on_missing: fail_startup }
    implementation: "AiQuotaProperties"

acceptance_tests:
  - id: AT-135-1
    given: "今日额度未用尽"
    when: "调用任一 AI 写端点"
    then: "成功且配额 used 加 1"
  - id: AT-135-2
    given: "今日额度已用满"
    when: "再次调用"
    then: "返回 429 且消息含 used 与 limit"
  - id: AT-135-3
    given: "被 429 拒绝"
    when: "检查数据库"
    then: "AI 任务未创建（负向断言）"
  - id: AT-135-4
    given: "任意额度状态"
    when: "调用 GET /api/ai/quota"
    then: "不消耗额度"
  - id: AT-135-5
    given: "日期变更"
    when: "再次调用"
    then: "计数归零（新 key）"
  - id: AT-135-6
    given: "Redis 不可用"
    when: "调用 AI 端点"
    then: "放行并产生告警日志"
  - id: AT-135-7
    given: "ADMIN 角色"
    when: "连续调用超过日额度"
    then: "仍放行（豁免）"
  - id: AT-135-8
    given: "账套 A 额度已用满"
    when: "账套 B 调用"
    then: "放行 —— 两账套配额互不影响"
