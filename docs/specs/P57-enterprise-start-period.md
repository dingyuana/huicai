# P57 企业级建账期间通用化（start_period）

> **编号**：HUICAI-SPC-P57
> **版本**：1.0 | **修改日期**：2026-08-07 | **修改人**：Hermes
> **关联需求**：REQ-2026-077
> **前置**：P55（期间软删修复）、P17（报表中心）
> **状态**：草案待审核

---

## 1. 输入契约

### 1.1 问题背景
系统现无"企业建账期间"概念，全链路隐含假设"建账从当前月开始"：
- 前端 11 处写死 `dayjs().format('YYYYMM')` 作为默认期间（报表×4、税务申报、分析×2、ARAP×3、资产折旧）
- 期初建账页默认选中**最新**期间（`periods[0]` 降序）→ 用户退后几年建账时误录到最新期间
- `validateOpeningBeforePost` 用"企业**最早期**期间"判断 → 企业从中间期间开始建账时校验失效

**目标**：用户可选择任意期间（含几年前）作为建账起点，全链路默认期间、期初录入、过账校验均基于该期间正常工作。

### 1.2 新增强制约束
- `t_enterprise` 增加 `start_period VARCHAR(6)`（建账期间，YYYYMM），NULL 表示未建账
- 期初建账成功（`initOpeningBalances` 写入数据或零余额确认）时自动回填 `start_period`
- 已建账企业的 `start_period` 不可通过编辑接口随意修改（需先解锁/清空期初）
- 过账校验基于 `start_period` 而非"最早期期间"

### 1.3 输入参数
| 参数 | 类型 | 约束 |
|------|------|------|
| period | String(6) | YYYYMM，必须存在于 t_period 且 enterprise_id = 当前企业 |

### 1.4 权限要求
- 查询当前期间：登录用户（任意角色）
- 期初建账/锁定/清空：沿用现有权限（含 @Auditable 审计）

---

## 2. 输出契约

### 2.1 新增接口：GET /api/v1/enterprise/current-period
返回企业"默认期间"（供前端所有"默认期间"查询使用）：

```json
{
  "code": 200,
  "data": {
    "currentPeriod": "202401",
    "startPeriod": "202401",
    "hasDataPeriod": null
  }
}
```

**计算逻辑（优先级从高到低）**：
1. 若存在 `start_period` 之后有凭证/余额数据的期间 → 返回**最近有数据期间**（`hasDataPeriod`）
2. 否则返回 `start_period` 本身
3. `start_period` 为空 → 返回企业期间列表中**最新**期间（与现状一致，向前兼容）

### 2.2 修改：EnterpriseSwitchVO
增加字段 `startPeriod`（String），切换企业时返回。

### 2.3 失败响应
- 企业不存在/已删除 → 404「企业不存在」
- 未登录 → 401

---

## 3. 状态流转

### 3.1 建账期间生命周期
```
start_period = NULL ──期初建账成功──▶ start_period = 录入期间
      │                                    │
      └── 期间存在性校验失败 ◀── 清空全部期初 ──┘
```
- `initOpeningBalances` 成功（含零余额确认）→ 若 `start_period` 为空则回填
- `clearOpeningBalances`（该期间为唯一建账期间且清空后无其他余额期间）→ 可置回 NULL
- 禁止：已建账企业直接通过编辑接口修改 `start_period`

### 3.2 过账校验重写（validateOpeningBeforePost）
```
period < start_period        → 拒绝「该期间早于企业建账期间，未启用」
period == start_period       → 要求 opening_status ∈ {entered, locked}，否则拒绝「请先期初建账」
period > start_period        → 允许（start_period 已完成期初录入即可）
start_period == NULL         → 回退旧逻辑（最早期期间校验），兼容存量企业
```

### 3.3 负向断言（禁止路径）
- 禁止：`period < start_period` 过账凭证
- 禁止：`start_period` 期间未录入期初时过账
- 禁止：已建账企业修改 `start_period` 字段（编辑接口忽略该字段）

---

## 4. 异常处理

| 场景 | 错误码 | 降级策略 |
|------|--------|---------|
| period 早于 start_period | 400 | 提示「该期间早于企业建账期间 XXXX，未启用」 |
| start_period 期间未建账即过账 | 400 | 提示「请先在期初建账模块录入期初余额」 |
| start_period 为空且企业无期间 | 400 | 提示「企业尚未创建会计期间」 |
| 查询当前期间时企业不存在 | 404 | — |

---

## 验收标准（BDD）

### 场景 1：期初建账成功回填 start_period
- **Given** 企业 A 无 start_period，期间 202401~202412 已创建
- **When** 用户在 202401 录入期初余额并保存成功
- **Then** t_enterprise.start_period = '202401'
- **And** 未重复回填（再次建账其他期间不覆盖 start_period）

### 场景 2：企业退后几年建账（2021 年）
- **Given** 企业 B 在期间管理创建 202101~202112，并在 202101 完成期初建账
- **When** 前端打开资产负债表页（不手动输入期间）
- **Then** 默认期间 = 202101（或其后最近有数据期间），期初余额可见
- **And** 不再默认当前月（202608）

### 场景 3：过账校验基于 start_period
- **Given** 企业 B start_period = 202101，202101 期初已 locked
- **When** 用户在 202012 创建并过账凭证
- **Then** 拒绝：400「该期间早于企业建账期间」
- **And** 凭证状态不变（仍 AUDITED）

### 场景 4：start_period 期间未建账时禁止过账
- **Given** 企业 B start_period = 202101，opening_status = none
- **When** 用户在 202101 过账凭证
- **Then** 拒绝：400「请先在期初建账模块录入期初余额」
- **And** 不产生余额更新

### 场景 5：存量企业兼容（start_period 为空）
- **Given** 企业 C 已存在期间和凭证数据，start_period = NULL
- **When** 用户过账凭证
- **Then** 走旧逻辑（最早期期间校验），行为与改造前一致

### 场景 6：期初建账页默认选中最早未建账期间
- **Given** 企业 D 期间 202101~202112，202101~202106 已建账，202107 起未建账
- **When** 打开期初建账页
- **Then** 默认选中 202107（最早未建账期间），而非最新 202112

---

## 影响面清单

| 层 | 文件 | 变更 |
|----|------|------|
| DB | V134__add_start_period_to_enterprise.sql | t_enterprise 加 start_period 列 |
| 后端 Entity | EnterpriseEntity.java | 加 startPeriod 字段 |
| 后端 VO | EnterpriseSwitchVO.java | 加 startPeriod 字段 |
| 后端 Controller | EnterpriseController.java | 新增 GET /current-period |
| 后端 Service | SubjectBalanceServiceImpl.java | initOpeningBalances 回填 start_period；clearOpeningBalances 可置空；validateOpeningBeforePost 重写 |
| 后端 Mapper | ReportDataMapper.java / EnterpriseMapper.java | 查最近有数据期间 |
| 前端 API | api/modules/enterprise.ts（或新增 report.ts 函数） | 新增 getCurrentPeriod() |
| 前端 11 处 | 报表×4 / 税务 / 分析×2 / ARAP×3 / 资产 | 默认期间改为异步获取 currentPeriod，回退当前月 |
| 前端期初页 | BeginningBalanceView.vue | 默认选中最早未建账期间 |

---

## 风险与决策点

1. **start_period 修改权限**：本期仅支持"期初建账自动回填"，不开放编辑接口改 start_period（避免与余额数据不一致）；后续如需调整，走"清空期初→重新建账"链路
2. **current-period 接口缓存**：不引入 Redis 缓存（计算轻量，每次查询实时计算），避免缓存一致性成本；若后续性能问题再优化
3. **11 处前端改造范围**：本期全部改造（用户确认完整方案），默认期间获取失败时回退 `dayjs().format('YYYYMM')` 保证不白屏
4. **测试策略**：新增 6 个 BDD 场景对应 @Test；核心 Service 单测（Mockito）+ Mapper 真实 DB 测试（Testcontainers）
