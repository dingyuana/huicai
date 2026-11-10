# SPEC-P139：AI 任务 MQ 结果回调写回丢数据（回调链路上下文重建）

| 项 | 内容 |
|---|---|
| 状态 | ✅ **已实施（D-1~D-4 均裁定通过，2026-10-26 生产探针 D-4 验证全通过）** |
| REQ | REQ-2026-155 |
| 前序 | AGENTS §4.5 第 61 条未竟项（AI 模块 MQ 回调写回）；SPEC-P122（GUC/RLS 机制）；SPEC-P137（事务/上下文判据方法论） |
| 关联 | §1 铁律 #2（AI 输出=建议，人审后应用——本缺陷使「人审」没有可审的东西）、铁律 #5（审计追踪）、§4.5 第 27/33/61 条（降权验证/超管绕过/无事务 GUC NULL） |

---

## §0 取证（全部实测，2026-10-26）

### 0.1 生产探针（决定性证据）

`huicai-backend` 运行中的容器（`ai.enabled=true`），`t_ai_task` 存在 id=3 行（`task_no=LOCAL-PROBE-001`，`enterprise_id=1`）。经 RabbitMQ 管理 API 向 `huicai.ai.exchange`/`ai.result` 发布回调消息（taskId=3，status=COMPLETED，outputData=`{"probe":"P139-MQ-1"}`，confidence=0.99）：

| 环节 | 实测结果 |
|---|---|
| 发布 | `{"routed":true}`（路由成功，队列深度随后归 0） |
| 消费 | 日志线程 `ntContainer#0-1`：`收到 AI 任务结果: taskId=3, status=COMPLETED` |
| 写回 | `WARN AI 任务不存在: taskId=3` → return |
| DB 对比 | 行**原样不动**：`status` 未变（探针行本为 COMPLETED），`confidence/output_data/completed_at` **全 NULL**（若写回成功，outputData 与 confidence 必然变化） |

**结论**：行确实存在（超管视角可读），MQ 线程里 `selectById` 却返回 null ⇒ 「AI 任务不存在」文案是**假象**，真实原因是 RLS 过滤。

### 0.2 根因链（逐环代码取证）

1. **下发侧**：`AiTaskService.createAndDispatch`（web 线程，上下文存在）构造 `AiTaskMessage`——字段只有 `taskId/taskNo/taskType/bizType/bizId/inputData/callbackQueue`，**无 enterpriseId**（`AiTaskMessage.java:17-23`）。
2. **Python 回传**：`ai-service/app/workers/task_consumer.py#_publish_result` 用**固定字段白名单**重建结果消息（`taskId/taskNo/status/outputData/confidence/errorMessage`）——**即使 body 里有别的字段也会被丢弃**。
3. **消费侧**：`AiResultListener.onResult` → `AiTaskService.handleResult`（MQ 消费线程 `ntContainer#N-x`）。`handleResult` 有 `@Transactional`（事务在），但该线程 `EnterpriseContextHolder.get()` 为 null ⇒ `TenantGucSetter.apply` 早退（`TenantGucSetter.java:59`）⇒ 事务连接上 `SET LOCAL app.enterprise_id` **从不执行** ⇒ GUC NULL。
4. **RLS 过滤**：`t_ai_task` 为 `relrowsecurity=t / relforcerowsecurity=t`，谓词 `enterprise_id = (NULLIF(current_setting('app.enterprise_id', true), ''))::bigint` ⇒ NULL 比较 ⇒ **0 行**。
5. **静默吞掉**：`selectById(taskId)` = null ⇒ `log.warn("AI 任务不存在")` + return；即使抛异常 `onResult` 也是 `catch(Exception) log.error` ⇒ 消息照常 ack。**全过程无告警、无 DLQ、数据永久丢失**。

### 0.3 为什么既有「修好的」路径没覆盖这里

- 同类 `getById`/`page` 症状已由「类级 `@Transactional`」修复（`AiTaskService` 类注释即 P128 §7.1 的修复记录）——但那修的是 **web 线程**（有上下文，事务使 GUC 可落）。MQ 线程是**类级事务也救不了的**：事务在、上下文不在。
- `RequiresNewRunner`（P137）同样无效：REQUIRES_NEW 开的新事务**依然没有上下文**，GUC 依旧 NULL；且新事务看不见外层夹具（§4.5 第 62 条判据③）。**上下文缺失不是事务缺失**。
- L2 真库测不出来：`AbstractMapperTest` 连接是 Testcontainers 超级用户（§4.5 第 33 条），谓词根本不执行 ⇒ 任何「丢写」断言在 L2 恒绿。

### 0.4 写路径的一半（正确，无需改）

`createAndDispatch` 在 web 线程执行、上下文在场 ⇒ `BaseEntity.enterpriseId` 有 `FieldFill.INSERT`（`BaseEntity.java:23-24`）⇒ `MyMetaObjectHandler` 无条件覆盖为上下文企业 ⇒ 任务行 `enterprise_id` 落库正确。**缺口只在回调方向**。

---

## §1 方案（主案：上下文随消息全链路透传，监听线程重建）

### 1.1 老丁裁定方式说明

本轮唯一改动点过度方案唯一，但有一个策略性决定须裁定：**回传链怎么修**（D-1）与**旧消息兜底语义**（D-2）。以下按我的建议给出完整设计，裁定后实施。

### 1.2 改动清单

| # | 文件 | 改动 |
|---|---|---|
| ① | `backend/.../ai/mq/AiTaskMessage.java` | 新增 `private Long enterpriseId;` |
| ② | `backend/.../ai/service/AiTaskService.java#createAndDispatch` | 构造消息时 `message.setEnterpriseId(EnterpriseContextHolder.get());`（web 线程取自上下文；系统路径为 null 由 D-2 语义统一兜底） |
| ③ | `ai-service/app/workers/task_consumer.py#_publish_result` | result dict 增 `"enterpriseId": body.get("enterpriseId")`（一行；白名单重建改为透传该字段） |
| ④ | `backend/.../ai/mq/AiTaskResult.java` | 新增 `private Long enterpriseId;` |
| ⑤ | `backend/.../ai/mq/AiResultListener.java#onResult` | 调 `handleResult` **前**：`if (result.getEnterpriseId() != null) EnterpriseContextHolder.set(result.getEnterpriseId());`，包 `try { ... } finally { EnterpriseContextHolder.clear(); }`（必清，§4.5 第 29 条判据②：污染源必须自收；断言不能指望后来者） |
| ⑥ | `backend/.../ai/service/AiTaskService.java#handleResult` | 「任务不存在」分支按 D-2 裁定改语义：监听层已兜 D-2，这里保留 warn，但**文案必须坦诚**——改为「无上下文兜底已失败，须人工按 taskId 以超管查原行」级别的揭示（避免再出现 §0.1 那种假象文案） |

### 1.3 机理澄清（写进注释）

- 监听线程 `set` ThreadLocal 后，`handleResult` 的事务内 SQL 由 `EnterpriseDataPermissionInterceptor` **逐 SQL** 读取 ThreadLocal 并 `SET LOCAL`（P137 已证该机制）⇒ GUC 落点即为事务连接 ⇒ 谓词可命中。
- 上下文重建必须在**监听层**而非 `handleResult` 内部：listener 是 MQ 线程与业务语义的边界，任何走 `AiTaskService` 的新调用点也从这里受益。

---

## §2 端点队列 / 输入输出契约

- 下发：`huicai.ai.exchange` / `ai.task` ⇒ `AiTaskMessage`（**+enterpriseId**，web 线程上下文值）。
- 回传：`huicai.ai.exchange` / `ai.result` ⇒ `AiTaskResult`（**+enterpriseId**，Python 原样透传）。
- 兜底：`huicai.ai.dlq.exchange` / `ai.dlq` ⇒ `huicai.ai.dlq`（D-2 选 DLQ 时监听层显式转发；注意 `aiResultQueue` 本身**无** DLX arg，须 `rabbitTemplate.convertAndSend(DLQ_EXCHANGE, DLQ_ROUTING_KEY, ...)` 显式投递到 `AiRabbitConfig.AI_DLQ`）。

---

## §3 状态流转

不改变业务状态机：`PENDING → 处理中/FAILED/COMPLETED`、`applyStatus NOT_APPLIED/APPLIED/REJECTED` 均不动，唯一变化是**回调结果真的落库了**（此前形式上「永远不会到达 COMPLETED 的 outputData/confidence/completed_at」）。

## §4 异常处理

| 场景 | 行为 |
|---|---|
| 回调消息无 enterpriseId（旧生产者/系统路径） | D-2 裁定（建议 `log.error`（非 warn）+ 显式转发 DLQ，不 ack 丢弃） |
| taskId 查无此行（被物理删/传错） | 维持 `log.warn`（真不存在，非 RLS 假象——此刻上下文已在场，能查到就能查到） |
| `handleResult` 抛异常 | 维持 `catch log.error`；**不**改为 requeue 抛出（防无限重投循环） |

## §5 验收（BDD → 测试用例映射，测试方法名即 AT ref）

| AT | Given-When-Then | 测试 |
|---|---|---|
| AT-139-1 | Given 结果消息携带 enterpriseId=E，When `onResult` 执行到 `handleResult` 调用时刻，Then ThreadLocal 上下文 == E（mock AiTaskService 在桩里读 `EnterpriseContextHolder.get()` 记录） | `AiResultListenerContextTest#restoresContextFromMessageBeforeWrite` |
| AT-139-2 | Given 同上执行完成（含抛异常路径），Then `finally` 后上下文== null（防连接池/线程复用污染，§4.5 第 29 条） | `AiResultListenerContextTest#clearsContextAfterHandlingEvenOnError` |
| AT-139-3 | Given 下发消息由带上下文 E 的线程调用 `createAndDispatch`，When 捕获 `convertAndSend` 参数，Then `AiTaskMessage.enterpriseId == E` | `AiTaskDispatchContextTest#dispatchMessageCarriesContextEnterprise` |
| AT-139-4 | Given Python `_publish_result(body,...)` 收到含 enterpriseId 的 body，Then 结果 dict 含同值 | `ai-service/tests/test_task_consumer.py#publish_result_echoes_enterprise_id` |
| AT-139-5 | Given 消息无 enterpriseId，When `onResult`，Then **不静默丢**（D-2 裁定后按其断言：DLQ 收到 / 或 error 日志 + 上下文未误置） | `AiResultListenerContextTest#missingEnterpriseIdFollowsDeclaredPolicy` |

**守卫原则**：AT-139-1/2 是**语义断言**（读 ThreadLocal），比结构守卫更能防「注解在但不生效」类漂移；不加单独文本守卫，避免把「set 写过」当「生效过」。

## §6 反证计划（实现后执行）

| F | 注入 | 期望红 |
|---|---|---|
| F-1 | 监听层摘掉 `EnterpriseContextHolder.set(...)`（消息仍带 enterpriseId） | AT-139-1 转红 |
| F-2 | 删掉 `finally { clear() }` | AT-139-2 转红 |
| F-3 | 下发侧不 `setEnterpriseId` | AT-139-3 转红 |
| F-4 | Python 白名单不含 enterpriseId 回传 | AT-139-4 转红 |
| F-5 | D-2 路径摘除（无 enterpriseId 时直接 return） | AT-139-5 转红 |

⚠️ **如实登记**：F-1~F-4 在 **L1**（Mockito/纯单测）成立；RLS 真过滤行为在 L2 **测不出来**（超管绕过 §4.5 第 33 条）——「上下文真的使 GUC 生效、selectById 不再返回 null」的最终判据是 §0.1 同款**生产探针**：实现合入 + 重建容器后重发同款消息，DB 行 output_data/confidence/completed_at 应发生变化。已按 §4.5 第 50 条排队验证。

## §7 已知局限（如实登记）

1. **旧消息/系统路径的计划任务**：若未来有「系统定时任务」下发 AI 任务（当时无企业上下文），`enterpriseId` 为 null ⇒ 走 D-2 兜底。当前仓库无此类调用点（`createAndDispatch` 全部来自登录态端点），此为前瞻性限制。
2. **MQ 线程写回的审计字段**：`handleResult.updateById` 无登录态，`updated_by` 走 P118 的兜底默认值（与「系统操作」一致）；本 SPEC 不改语义。
3. **DLQ 内容重放**：无 enterpriseId 的消息进 DLQ 后，人工以超管按 taskId 查原行取 enterprise_id 再手动补跑（不自动重放）。
4. **ack 语义不变**：所有结果消息都被 ack（`onResult` 全 catch）——DLQ 是**显式转发**，不是死信重排。

## §8 待老丁裁定

| # | 议题 | 建议 | 影响 |
|---|---|---|---|
| D-1 | 回传链方案 | **按 §1.2 全链路透传（①~⑥）** | 备选被拒：a) `RequiresNewRunner` 无上下文同病（§0.3）；b) 裸 SQL/给表开白名单绕 RLS：违 M5b 隔离架构、开危险先例；c) 超管角色执行：鸿沟更大 |
| D-2 | 无 enterpriseId 结果消息的兜底语义 | **log.error + 显式转发 `huicai.ai.dlq`**（不静默、不致丢失可见性） | 选 warn-only 则消息仍丢但日志可见；选抛异常则消息无限重投，不建议 |
| D-3 | Python 侧测试基建 | **新增最小 `ai-service/tests/test_task_consumer.py`（pytest）**，AT-139-4 断言 | 否则 Python 回传的改动**无任何自动护栏**；ai-service 目前零测试 |
| D-4 | 生产探针复验（§6 ⚠️） | **合入+重建容器后重发 §0.1 同款消息**，DB 行应变化 | 这是唯一能证「RLS 路径真修好」的动作（L2 测不出） |

---

## §9 实施中发现（2026-10-26，D-4 生产探针倒逼出两项连带生产缺陷）

> ⚠️ **本节内容是在 D-4 探针（重建容器后重发生产同款消息）过程中顺带发现的**，不在原始 SPEC §1.2 改动清单里，但已在同批修复并登记为回归锁。

### 9.1 连带缺陷①：`JsonbTypeHandler` 吞错静默落 `{}`

**现象**：探针 A/D 直发带 `outputData` 的结果 → DB `output_data` 仍为 `'{}'`，正文全丢，但写入返回 200、日志显示「收到 AI 任务结果」—— **看起来成功、实际结果全丢**。

**根因**：`AiTaskService.handleResult` 旧实现用 `outputData.toString()` 生成 Java Map.toString 格式（`{k=v}`，非合法 JSON）。`t_ai_task.output_data` 是 `jsonb`，走 `JsonbTypeHandler` 解析时抛 `PSQLException: column is of type jsonb but expression is of type character varying`，handler **catch 后 `setValue("{}")` 静默吞错**（§4.3 第 10 条反模式的再现实例）—— 调用方拿到的永远是合法 JSONB `{}`，AI 结果正文永久丢失。

**修法**：在 `AiTaskService` 构造器注入 `ObjectMapper`，写 `objectMapper.writeValueAsString(outputData)`；序列化失败改抛 `BusinessException`（fail-loud，让错误可见）。守卫 `AiTaskHandleResultJsonTest#handleResultSerializesOutputDataAsJson` 锁死。

**同类隐患排查**：全仓 grep `JsonbTypeHandler` 所有调用点，除 `AiTaskService` 外其余路径均从 DB 读 JSONB 转 String 或走 `@TableField(typeHandler=...)` 正确字段映射，**仅此一处用 `toString()` 写 jsonb 列**。

### 9.2 连带缺陷②：Python 消费者 PRECONDITION_FAILED 死循环

**现象**：D-4 重发 ai.task 消息后，`huicai.ai.task.queue` 消息深度从 1 变为 0（被消费），但 backend 日志无「收到 AI 任务结果」——消息被吞却无回传。

**根因**：`task_consumer.py#start()` 原用无参主动声明队列（`declare_queue(settings.task_queue, durable=True)`）。而 Java `AiRabbitConfig` 声明的同名队列携带 DLX 参数（`x-dead-letter-exchange=huicai.ai.dlq.exchange`, `x-dead-letter-routing-key=ai.dlq`）。**RabbitMQ 对已存在且参数不同的队列重新声明会报 PRECONDITION_FAILED**，但原代码 catch 只是 sleep 5 秒后重试，**进入永真死循环**，消费者从未真正启动（日志里反复出现 `AI 消费者启动失败: PRECONDITION_FAILED...`）。

**修法**：改为 `passive=True`——拓扑由 Java（唯一事实源）声明，Python 被动读，避免参数冲突。同步补全 `_exchange` 字段与 passive declare（见 §9.3）。

### 9.3 连带缺陷③：结果消息经 default 交换机发布，routing_key 失效

**现象**：在 §9.2 修复后消费者终于启动，发 ai.task 消息后 task 队列归 0，但 backend 仍没收到结果。

**根因**：`_publish_result` 用 `self._channel.default_exchange.publish(routing_key=settings.result_routing_key)`。default（amq.default）交换机**只按「队列名」路由**（无 routing_key 概念）；`settings.result_routing_key = "ai.result"` 不等于 result 队列名 `huicai.ai.result.queue` ⇒ **消息无机路由、被 RabbitMQ 静默丢弃**，全链路此前从未走通。

**修法**：① 在 `start()` 中被动声明 `huicai.ai.exchange`（DIRECT，参数由 Java 控制），存为 `self._exchange`；② `_publish_result` 改 `self._exchange.publish(...)`；③同步修 `import logging` → `from app.core.logging import get_logger`（std logging 在容器里无 handler，消费者处理日志全静默不可见）；④loguru 占位符统一改用 `{}`。

**判据**：`list_consumers` 显示 task/result 队列均有消费者、`publish_result` 的 exchange/routing_key 组合在 direct 语义下正确路由到 result 队列——**三个条件缺一不可**。

### 9.4 验证结果（D-4 终态）

| 探针 | 触发路径 | DB 结果 | output_data | enterprise_id |
|---|---|---|---|---|
| A（直发，ent=1） | `ai.result` queue + enterpriseId=1 | COMPLETED | `{"probe":"P139-json-fix"}` ✅ | 1 ✅ |
| B（全链路，ent=1） | `ai.task` → Python OCR → `ai.result` | COMPLETED | `{"engine":"tesseract","fields":...,"raw_text":"..."}` ✅ | 1 ✅ |
| D（直发，ent=4 跨账套） | `ai.result` queue + enterpriseId=4 | COMPLETED | `{"probe":"P139-d-cross-ent","score":0.96}` ✅ | 4 ✅ |
| C（无 enterpriseId，负向） | `ai.result` queue 无 enterpriseId | 未动 | DB 行原样 | — |
| DLQ | 无 enterpriseId 消息 → 显式转发 | 队列 1 条 | msg.enterpriseId=null ✅ | — |

- L1 单测：**1768/0/0/5**（含 4 例新 test + 1 例 JSON 串联锁）。
- Python pytest：**2/2 GREEN**（AT-139-4 + 连带③锁）。
- 反证：F-1 摘 set（2 红）、F-2 摘 finally（1 红）、F-3 摘下发 set（1 红）、F-4 Python 先红 2/2 → 还原 4/4 绿。

---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: AiResultListener
module: base.ai
table: t_ai_task

rules:
  - id: C-01
    source: "P139 §1.2 ②/1.3"
    rule: "createAndDispatch 构造 AiTaskMessage 时必须携带当前上下文的 enterpriseId（无上下文时为 null，由 D-2 兜底）"
    mapping: { field: enterpriseId, source: EnterpriseContextHolder, nullable: true }
    implementation: "backend/src/main/java/com/huicai/base/ai/service/AiTaskService.java#createAndDispatch"
  - id: C-02
    source: "P139 §1.2 ③"
    rule: "Python 回传结果必须原样透传 enterpriseId 字段"
    mapping: { passthrough: enterpriseId, drop: forbidden }
    implementation: "ai-service/app/workers/task_consumer.py#_publish_result"
  - id: C-03
    source: "P139 §1.2 ⑤/§1.3"
    rule: "监听线程调用 handleResult 前必须按消息 enterpriseId 重建 EnterpriseContextHolder，且 finally 必清（无论成败）"
    mapping: { set_before_call: enterpriseId, finally_clear: true }
    implementation: "backend/src/main/java/com/huicai/base/ai/mq/AiResultListener.java#onResult"
  - id: C-04
    source: "P139 §4/D-2"
    rule: "结果消息无 enterpriseId 时不得静默丢弃：log.error + 显式转发 huicai.ai.dlq（pending D-2 裁定）"
    mapping: { on_missing: log_error_and_dlq, requeue: false }
    implementation: "backend/src/main/java/com/huicai/base/ai/mq/AiResultListener.java#onResult"
  - id: C-05
    source: "P139 §5/§6"
    rule: "回归锁：透传/重建/finally清理/D-2兜底任一环被摘除，必须使对应 @Test 转红（反证 F-1~F-5 全成立）"
    mapping: { falsification: F1_F2_F3_F4 }
    implementation: "backend/src/test/java/.../AiResultListenerContextTest.java"

acceptance_tests:
  - id: AT-139-1
    ref: "AiResultListenerContextTest#restoresContextFromMessageBeforeWrite"
  - id: AT-139-2
    ref: "AiResultListenerContextTest#clearsContextAfterHandlingEvenOnError"
  - id: AT-139-3
    ref: "AiTaskDispatchContextTest#dispatchMessageCarriesContextEnterprise"
  - id: AT-139-4
    ref: "ai-service/tests/test_task_consumer.py#publish_result_echoes_enterprise_id"
  - id: AT-139-5
    ref: "AiResultListenerContextTest#missingEnterpriseIdFollowsDeclaredPolicy"
