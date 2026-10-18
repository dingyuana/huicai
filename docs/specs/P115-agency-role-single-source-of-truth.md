# SPEC-P115 — 代理角色单一事实源收敛（`agency_role`）

> **状态**：✅ **已实施完成**（2026-10-10，Plan 6 步中 5 步落地，步 4 经复核确认无需改代码）
> **关联需求**：新登记 REQ-2026-141；承接 2026-10-10 架构再评估结论第 1 项
> **日期**：2026-10-10
> **触发**：老丁要求「先评估审核修复需求方案，使用 spec+plan+test 执行」，
> 并裁定「先修 A：合并 agency_role 事实源」「监督模型取账无忧式（记账员/审核员命名角色对）」

---

## §0 取证：缺陷是**潜在**的，不是已实化的

### §0.1 同一事实有两处存储

| 位置 | 字段 |
|---|---|
| `backend/src/main/java/com/huicai/base/system/entity/UserEntity.java:55` | `t_user.agency_role` |
| `backend/src/main/java/com/huicai/agency/user/entity/AgencyUserEntity.java:20` | `t_agency_user.agency_role` |

### §0.2 写入方实际只有两个（上一轮说的「三个」不准确，此处更正）

| # | 位置 | 写入范围 | 评价 |
|---|---|---|---|
| 1 | `AgencyUserServiceImpl.java:114 + 122` | **双写**两表（同一事务） | ✅ 一致 |
| 2 | `base/system/dto/UserSaveDTO.java:102` | **只写 `t_user`** | ⚠️ **唯一分叉方** |

> ⚠️ **自我更正**：初版评估把 `suspend/reactivate` 也算作写入方。复核后不成立 ——
> 它们只同步 `status`，**不碰 `agency_role`**（`AgencyUserServiceImpl.java:183-191`）。
> 按「不能把没读过的代码算进证据」的纪律，此处按实际修正为两个。

**且代理用户根本没有「改角色」端点**：`AgencyUserController` 仅 create / suspend /
reactivate / terminate，**无 update**。故角色一旦建错，只能 terminate + 重建。

### §0.3 读取方只有一处，与分叉方读的不是同一张表

| 位置 | 读的是 |
|---|---|
| `SecurityUtils.java:56` → `LoginUser.java:50` → `UserEntity.getAgencyRole()` | **`t_user`** |
| `AgencyUserEnterpriseServiceImpl.java:53`（判定能否被派工） | **`t_agency_user`** |

⇒ 若经 API 把 `UserSaveDTO.agencyRole` 从 `ACCOUNTANT` 改成 `REVIEWER`：
**权限判定立即生效**（读 t_user），**派工校验仍按旧角色**（读 t_agency_user）。
两套口径分叉，且无任何守卫能发现。

### §0.4 实测分叉尚未发生（诚实标注）

```sql
-- 探针（开发库 huicai）
SELECT u.username, u.agency_role, au.agency_role FROM t_user u
JOIN t_agency_user au ON au.user_id = u.id AND au.deleted = 0
WHERE u.user_type = 'AGENCY';
```

| username | t_user.agency_role | t_agency_user.agency_role | verdict |
|---|---|---|---|
| accountant01 | ACCOUNTANT | ACCOUNTANT | ok |
| reviewer01 | REVIEWER | REVIEWER | ok |
| assistant01 | ASSISTANT | ASSISTANT | ok |
| 王会计 / liu | ACCOUNTANT | ACCOUNTANT | ok |

`total_agency_users = 5, diverged = 0` ⇒ **当前数据一致**。

⚠️ **这是「当前没坏」，不是「不会坏」**。分叉写入方 `UserSaveDTO.agencyRole` 仍然可达
（前端 `UserList.vue` 只发 `roleIds`，故只能经 **API 直达**，UI 不可达）。
SPEC 的目标是**关闭这条路径并加守卫**，不是修一个已坏的数据。

---

## §1 范围

**做**：
1. 声明 `t_agency_user.agency_role` 为**唯一事实源**（语义正确：该表即「代理内成员资格」，
   自带 `agency_id` 与生命周期）
2. `t_user.agency_role` 定位为**受控镜像**，只允许 `AgencyUserServiceImpl.create`
   在**同一事务**内维护（已在做，保持不变）
3. **关闭唯一分叉写入方**：`UserSaveDTO` 移除 `agencyRole`
4. 补守卫：真库断言 + 结构断言，含反向自证

**不做**（登记遗留，另立 SPEC）：
- **撤 `t_user.agency_role` 列**（需 DDL；镜像列的存在本身就是债，但撤列要改
  `LoginUser` 构造链路并做全量鉴权回归，与 A 项「低风险不改 schema」的裁定不符）
- **补「改角色」端点**（功能缺口：现在只能 terminate + 重建；属新功能，需 BDD）
- **RBAC 增代理端角色 / 角色按租户定制**（再评估结论 C 项）
- **审核/监督域**（老丁已裁定取账无忧式记账员/审核员角色对，另立 SPEC-P116）
- **`UserSaveDTO.roleIds` 权限授予面**（字段注释已自记「待单独评估」）

---

## §2 方案选型

| 方案 | 评估 |
|---|---|
| **A 单一事实源 + 关分叉方 + 守卫（选中）** | ✅ 零 DDL、低风险、可反证；符合老丁「低风险不改 schema」裁定 |
| B 撤 `t_user.agency_role` 列，改由 `t_agency_user` join 读取 | ✅ 最彻底，但要动 `LoginUser`/`AuthController` 构造链路并全量鉴权回归；列为遗留 |
| C 只加文档说明 | ❌ 无护栏，分叉仍可发生 |

**为什么声明 `t_agency_user` 为源而不是反过来**：`agency_role` 的语义是「**在某代理公司内**的角色」，
必须挂在带 `agency_id` 的成员资格行上；`t_user` 是跨租户的**身份**行，
把租户内角色放在身份行上正是这次分叉的根因。竞品侧同一结论：用友（友户通用户ID + 企业账号ID分离）、
金蝶（一个手机号加入多个企业）、SAP B1（每租户单独标 Super User）——**身份永远不是租户**。

---

## §3 输入契约 / 输出契约 / 异常处理

### 输入契约
- `UserSaveDTO` **删除** `agencyRole` 字段及其 `@Size` 校验；`toEntity()` 不再 `setAgencyRole`
- `UserEntity.agency_role` 列保留，但**唯一写入方**为 `AgencyUserServiceImpl.create`

### 输出契约
- `POST/PUT /api/v1/system/users`（或本仓对应端点）**不再接受** `agencyRole` 参数
- 传入时：Jackson 默认忽略未知字段 ⇒ 接口仍 200 但**该字段被静默丢弃**
  ⚠️ 这本身是静默行为，需在 SPEC 明示并由守卫锁死（见 §5 AT-115-3）

### 异常处理
- `AgencyUserEnterpriseServiceImpl.assign` 的目标用户若无 `t_agency_user` 行 ⇒
  抛 `BusinessException` 并指明「该用户不是代理用户」（铁律 #14），
  而非静默按「角色不符」拒绝（否则报错指向错误原因）

---

## §4 验收标准（BDD）

| 编号 | Given | When | Then |
|---|---|---|---|
| AT-115-1 | 数据库中任一 `user_type='AGENCY'` 的 `t_user` | 查其 `t_agency_user` 行 | **必须存在且唯一**（`deleted=0`），否则转红 |
| AT-115-2 | 任一 `AGENCY` 用户 | 比对两表 `agency_role` | **必须相等**，否则转红 |
| AT-115-3 | `UserSaveDTO` 类 | 反射取其字段 | **不得包含** `agencyRole`，否则转红 |
| AT-115-4 | `AgencyUserServiceImpl.create` 执行后 | 查两表 | 同一事务内双写、值相等 |
| AT-115-5 | 反证：注入一条 `t_user.agency_role` 与 `t_agency_user` 不一致的数据 | 跑守卫 | **必须转红**（证明守卫非恒绿） |
| AT-115-6 | 反证：把 `agencyRole` 字段加回 `UserSaveDTO` | 跑守卫 | **必须转红** |

---

## §5 门禁与反证要求

1. **AT-115-5 是必做项**：守卫必须能在「有人注入分叉数据」时转红；
   只断言「当前全部相等」的守卫是恒绿（AGENTS §4.5 第 21 条）。
2. **AT-115-6** 证明字段级守卫真的在拦，不是装饰。
3. 结构性断言走**反射 + JDBC 真库**，两者都要（前者防代码回退，后者防数据漂移）。
4. L1/L2 全量回归（`UserSaveDTO`/`UserServiceImpl` 是共享路径，非定向）。

---

## §6 Plan（微循环，每步 Red→Green→反证）

| 步 | 内容 | 交付物 | 预计 |
|---|---|---|---|
| 1 | 写守卫测试（先红）：真库一致性 AT-115-1/2 + 反射 AT-115-3 | `AgencyRoleSingleSourceOfTruthTest` | 1 循环 |
| 2 | 反证 AT-115-5/6（确认守卫真能红） | 反证记录 | 1 循环 |
| 3 | Green：`UserSaveDTO` 删 `agencyRole`；`toEntity()` 不再 set | 改 DTO | 1 循环 |
| 4 | `assign` 对「有 `t_user` 无 `t_agency_user`」补 fail-loud | 改 Service | 1 循环 |
| 5 | 补「改角色」的**占位说明**（不实现，登记遗留） | SPEC §7 | — |
| 6 | 全量 L1/L2 + 5 个静态门禁 + 登记册/SPEC 回写 | 文档 | 1 循环 |

---

## §7 已知遗留（另立 SPEC）

1. **`t_user.agency_role` 列未撤** —— 镜像列本身就是债；撤列需改 `LoginUser` 构造链路
2. **无「改角色」端点** —— 角色建错只能 terminate + 重建
3. **`UserSaveDTO.roleIds` 权限授予面未评估**（字段注释自记）
4. **RBAC 无代理端角色、`t_role` 全局不可按租户定制**（再评估结论 C）
5. **审核/监督域缺失**（SPEC-P116，账无忧式记账员/审核员角色对）

---

## §8 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V0.1 | 2026-10-10 | 草案：取证（含两处自我更正）、方案选型、验收、Plan、遗留 |
