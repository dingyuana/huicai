# SPEC-P157：R4 员工自助查询（REQ-2026-160 后置项）

> 2026-11-09。用户裁定批准权限面方案（V201：EMPLOYEE 角色 + salary:self:view + t_employee.user_id）。

## 权限面（用户裁定批准）

| 项 | 落地 |
|----|------|
| 绑定模型 | `t_employee.user_id`（一员工↔一账号，uq_employee_user 部分唯一索引；NULL=未绑定） |
| 角色 | `EMPLOYEE`（role_type=NORMAL——chk_role_type 约束无 ENTERPRISE 值，实机抓出后修正） |
| 权限码 | `salary:self:view`（挂 menu-salary 下 BUTTON 行，仅 EMPLOYEE 持有） |
| V201 自证 | DO 三不变量：列在位 / 授权行数≥1（防空转，V196 教训）/ 权限行存在 |

## 实现

- **后端**：`GET /salary-bills/my`（按 user_id 反查绑定员工 → 名下 PAID 单；未绑定返空表不越权）+ `GET /salary-bills/my/{id}/items`（本人 PAID 单明细；他人单/未发放 fail-loud）+ `PUT /v1/employees/{id}/bind-user`（绑定/解绑；账号存在 + **同企业校验**（跨企业绑定破坏 RLS 隔离即拒）+ 一账号一员工友好报错）。
- **manifest**：+3 GET 条目（`/my`、`/my/{id}/items` → salary:self:view；bind-user PUT → party:list 粗粒度同族），写 283。
- **前端**：`/salary/my` 只读页（MySalaryView：列表 + 明细对话框，无任何管理按钮）+ 路由（permission salary:self:view）+ 侧边栏「工资薪酬」组内「我的工资单」项（canAccess 过滤，无 salary:list 者不见工资单管理项）+ EmployeeList 编辑框「自助账号」下拉（绑定单独走 bind-user 端点，语义分离）。

## 🔴 实机抓出真缺陷：manifest 通配吞并字面量路径

- **现象**：GET `/salary-bills/my` 403 要求 `salary:list`——`{id}` 段正则 `[^/]+` 把字面量 `my` 当 id 吞掉，命中 `GET /{id}` 条目。
- **根因**：`PermissionManifest.find` 首中即返回，条目顺序决定语义。生产清单 `/my` 恰好排在 `{id}` 前（侥幸对），但**顺序无关的正确性**才是匹配语义的本义。
- **修法**：`find` 两轮匹配——先字面量路径（精确胜出），再通配条目（首个兜底）。
- **守卫**：`literalPathWinsOverWildcard`（真清单断言）+ `literalWinsRegardlessOfOrder`（**合成条目**：`{id}` 故意排前，旧首中逻辑必红——首版守卫只测真清单被侥幸顺序骗过假绿，第二轮才抓到真反证）。
- **反证成立**：旧 `find` 注入 ⇒ 合成例红（真清单例因顺序侥幸仍绿——这正是合成测试的价值）。

## 验证

- L1 **1907**/0/0/5（+2：myBills 过滤/myItems 三态守卫 ×2 类；manifest 守卫 6→7 例）
- L2 `SalaryBillRealDBTest` 4/4（+1 selfServiceMyBills 真库全链：绑定→只见本人 PAID→DRAFT 不可见→他人单 400）
- **反证双成立**：myItems 越权校验短路恰该例红；manifest 合成条目旧逻辑必红
- 前端 372 全绿 + vue-tsc 过 + Playwright 真浏览器（临时 spec 用后即删）：emp-self-01 登录 → 侧边栏只见「我的工资单」 → 演练单 202611/net=27355 渲染 → 明细可见 → 管理按钮零
- 生产实机：绑定（跨企业绑定被拒=保护正确）→ 员工登录（权限码恰 salary:self:view）→ /my 200 → /my/1/items 200 → 越权面 403×2 + 不存在单 fail-loud
- 探针账号/绑定/角色关联已清理（员工 2 user_id 回 NULL；t_user 逻辑删 + t_user_role 物理删）

## 遗留

- 绑定管理走 EmployeeList 编辑框下拉（分页取 100 账号），超 100 账号企业需搜索式选择器（当前企业规模不需要）。
- 员工账号创建/EMPLOYEE 角色分配走既有 system/user 管理页（未做一键「开通自助」向导）。
