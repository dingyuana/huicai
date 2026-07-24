# S26 Sprint 6 任务书 — 前端角色适配

> 日期：2026-07-24 | 任务 ID：S26-S6 | 关联 SPEC：S-26 V2.0 | 前置：Sprint 5 完成

## 目标

完成代理内角色体系的前端适配：按 agencyRole 控制菜单可见性、企业切换范围、页面访问权限。实现会计管理页面和客户分配页面。

## 前置条件

- Sprint 5 完成：后端 agencyRole 体系就绪（t_agency_user / t_agency_user_enterprise 表 + API）
- 后端 /userinfo 接口已返回 agencyRole 字段
- 后端 /api/v1/agency/users 端点可用（AGENCY_ADMIN 专属）
- 后端 /api/v1/agency/assignments 端点可用（AGENCY_ADMIN 专属）
- 后端 EnterpriseController.switchEnterprise 已按 agencyRole 分流校验

## 任务清单（7 个微循环）

### MC6-01: auth.store 扩展 agencyRole

- 目标：store 加 agencyRole 状态 + 按角色过滤 enterpriseList + computed 属性
- 涉及文件：`frontend/src/stores/auth.store.ts`
- 新增状态：
  - `agencyRole: ref<string>('')`  // AGENCY_ADMIN / ACCOUNTANT / REVIEWER / ASSISTANT / ''
- 新增 computed：
  - `isAgencyAdmin: computed(() => agencyRole.value === 'AGENCY_ADMIN')`
  - `isAccountant: computed(() => agencyRole.value === 'ACCOUNTANT')`
  - `isReviewer: computed(() => agencyRole.value === 'REVIEWER')`
  - `isAssistant: computed(() => agencyRole.value === 'ASSISTANT')`
- fetchUserInfo 中从后端返回数据恢复 agencyRole
- login 中从登录返回数据设置 agencyRole
- **关键**：ACCOUNTANT/ASSISTANT 的 enterpriseList 由后端按分配关系过滤后返回，前端直接使用
- 契约：
  - ACCOUNTANT 登录后 `enterpriseList` 仅包含分配给自己的企业
  - AGENCY_ADMIN 登录后 `enterpriseList` 包含代理公司下全部企业
  - `isAgencyAdmin` 为 true 时显示管理菜单
- TDD：先写 auth.store.test.ts
  - `testAgencyRoleState` — agencyRole 正确存储和恢复
  - `testAccountantEnterpriseList` — ACCOUNTANT 的 enterpriseList 仅含分配企业
  - `testAgencyAdminEnterpriseList` — AGENCY_ADMIN 的 enterpriseList 含全部企业
  - `testComputedRoleFlags` — isAgencyAdmin/isAccountant/isReviewer/isAssistant 正确
- 验证：Vitest 单元测试通过

### MC6-02: 会计管理页面（AGENCY_ADMIN 可见）

- 目标：代理用户列表 + 创建/启停操作
- 涉及文件：
  - `frontend/src/views/agency/AccountantList.vue`（新建）
  - `frontend/src/api/modules/agency.ts`（扩展）
  - `frontend/src/router/routes/agency.ts`（加子路由）
- 页面功能：
  - 表格列：姓名 / 用户名 / 角色（AGENCY_ADMIN/ACCOUNTANT/REVIEWER/ASSISTANT）/ 状态（ACTIVE/SUSPENDED/TERMINATED）/ 负责客户数 / 操作
  - 操作列：
    - 暂停（ACTIVE → SUSPENDED）
    - 恢复（SUSPENDED → ACTIVE）
    - 终止（SUSPENDED → TERMINATED）
    - 分配客户（跳转到 AssignmentManage 页面）
  - 顶部按钮：新增会计（弹窗表单：用户名/密码/姓名/角色选择）
  - 角色选择下拉：会计(ACCOUNTANT) / 审核员(REVIEWER) / 助理(ASSISTANT)
  - 搜索栏：按姓名/用户名搜索
- 新增 API（agency.ts）：
  - `getAgencyUsers()` — GET /api/v1/agency/users
  - `createAgencyUser(data)` — POST /api/v1/agency/users
  - `suspendAgencyUser(id)` — POST /api/v1/agency/users/{id}/suspend
  - `reactivateAgencyUser(id)` — POST /api/v1/agency/users/{id}/reactivate
  - `terminateAgencyUser(id)` — POST /api/v1/agency/users/{id}/terminate
- 路由：`/agency/accountant-list` → AccountantList.vue
- 契约：
  - AGENCY_ADMIN 可见，ACCOUNTANT/ASSISTANT 不可见
  - 创建会计后自动出现在列表中
  - 暂停后状态变为 SUSPENDED，操作列显示"恢复"和"终止"
- TDD：先写 AccountantList.test.ts
  - `testRenderList` — 列表渲染
  - `testCreateAccountant` — 创建会计弹窗
  - `testSuspendAccountant` — 暂停操作
  - `testRoleFilter` — 角色筛选
- 验证：组件测试通过

### MC6-03: 客户分配页面（AGENCY_ADMIN 可见）

- 目标：为会计分配/取消分配客户企业
- 涉及文件：
  - `frontend/src/views/agency/AssignmentManage.vue`（新建）
  - `frontend/src/api/modules/agency.ts`（扩展）
- 页面功能：
  - 左侧：会计列表（el-select 下拉选择目标会计）
  - 右侧：已分配企业列表 + 可分配企业列表
  - 已分配企业：表格展示（企业名称/税号/分配时间/操作-取消分配）
  - 可分配企业：表格展示（企业名称/税号/操作-分配）
  - 分配操作：点击"分配" → 确认弹窗 → 调用 API
  - 取消分配：点击"取消分配" → 确认弹窗 → 调用 API
- 新增 API（agency.ts）：
  - `getAssignments(agencyUserId)` — GET /api/v1/agency/assignments?agencyUserId=xxx
  - `assignEnterprise(agencyUserId, enterpriseId)` — POST /api/v1/agency/assignments
  - `unassignEnterprise(assignmentId)` — DELETE /api/v1/agency/assignments/{assignmentId}
- 路由：`/agency/assignment-manage` → AssignmentManage.vue
- 契约：
  - AGENCY_ADMIN 可见，ACCOUNTANT/ASSISTANT 不可见
  - 分配后会计登录可见该企业
  - 取消分配后会计登录不再可见该企业
- TDD：先写 AssignmentManage.test.ts
  - `testSelectAccountant` — 选择会计
  - `testAssignEnterprise` — 分配企业
  - `testUnassignEnterprise` — 取消分配
  - `testAssignedList` — 已分配列表渲染
- 验证：组件测试通过

### MC6-04: EnterpriseSwitcher 按 agencyRole 过滤

- 目标：ACCOUNTANT 切换器仅显示分配的企业；AGENCY_ADMIN 显示全部
- 涉及文件：`frontend/src/layouts/components/EnterpriseSwitcher.vue`
- 改造逻辑：
  - 当前逻辑：`v-if="authStore.isAgency"` 显示切换器，选项 = `authStore.enterpriseList`
  - 新逻辑：
    - `v-if="authStore.isAgency"` 保持不变
    - 选项 = `authStore.enterpriseList`（后端已按 agencyRole 过滤，前端直接使用）
    - 显示当前角色标签（el-tag）：
      - AGENCY_ADMIN → 蓝色 "经理"
      - ACCOUNTANT → 绿色 "会计"
      - REVIEWER → 橙色 "审核员"
      - ASSISTANT → 灰色 "助理"
- 契约：
  - ACCOUNTANT 切换器选项 = 分配给自己的企业
  - AGENCY_ADMIN 切换器选项 = 代理公司下全部企业
  - 角色标签正确显示
- TDD：先写 EnterpriseSwitcher.test.ts（扩展）
  - `testRoleFilter` — 按角色过滤企业列表
  - `testRoleTagDisplay` — 角色标签显示
- 验证：组件测试通过

### MC6-05: 路由守卫按 agencyRole 控制

- 目标：按 agencyRole 控制页面访问权限
- 涉及文件：`frontend/src/router/index.ts`
- 改造逻辑：
  - 新增 agency 子路由守卫：
    - `/agency/accountant-list` — 仅 AGENCY_ADMIN 可访问
    - `/agency/assignment-manage` — 仅 AGENCY_ADMIN 可访问
    - 非 AGENCY_ADMIN 访问上述路由 → 跳转 `/403`
  - 业务路由守卫扩展：
    - ASSISTANT 访问审核相关路由 → 跳转 `/403`
    - REVIEWER 访问写操作路由 → 跳转 `/403`（前端兜底，后端为主）
- 路由配置（agency.ts）：
  ```typescript
  {
    path: '/agency/accountant-list',
    name: 'AccountantList',
    component: () => import('@/views/agency/AccountantList.vue'),
    meta: { title: '会计管理', role: 'AGENCY_ADMIN' }
  },
  {
    path: '/agency/assignment-manage',
    name: 'AssignmentManage',
    component: () => import('@/views/agency/AssignmentManage.vue'),
    meta: { title: '客户分配', role: 'AGENCY_ADMIN' }
  }
  ```
- 契约：
  - ACCOUNTANT 访问 `/agency/accountant-list` → 跳转 403
  - AGENCY_ADMIN 正常访问
- TDD：先写 router.test.ts（扩展）
  - `testAgencyRoleGuard` — 角色路由守卫
  - `testAccountantBlockedFromAdminRoute` — 会计被拦截
  - `testAssistantBlockedFromAuditRoute` — 助理被拦截
- 验证：路由测试通过

### MC6-06: 侧边栏按 agencyRole 控制菜单

- 目标：AppSidebar 菜单按 agencyRole 显示/隐藏
- 涉及文件：`frontend/src/layouts/AppSidebar.vue`
- 改造逻辑：
  - 代理公司菜单分组：
    ```vue
    <el-sub-menu v-if="authStore.isAgency" index="agency">
      <template #title>代理公司</template>
      <el-menu-item index="/agency/enterprise-list">客户列表</el-menu-item>
      <el-menu-item index="/agency/batch-operation">批量操作</el-menu-item>
      <el-menu-item v-if="authStore.isAgencyAdmin" index="/agency/accountant-list">会计管理</el-menu-item>
      <el-menu-item v-if="authStore.isAgencyAdmin" index="/agency/assignment-manage">客户分配</el-menu-item>
    </el-sub-menu>
    ```
  - 菜单可见性矩阵：

    | 菜单项 | AGENCY_ADMIN | ACCOUNTANT | REVIEWER | ASSISTANT |
    |--------|:-----------:|:----------:|:--------:|:---------:|
    | 客户列表 | 是 | 是 | 是 | 是 |
    | 批量操作 | 是 | 是 | — | — |
    | 会计管理 | 是 | — | — | — |
    | 客户分配 | 是 | — | — | — |

- 契约：
  - ACCOUNTANT 侧边栏无「会计管理」「客户分配」菜单
  - REVIEWER 侧边栏无「批量操作」「会计管理」「客户分配」菜单
  - ASSISTANT 侧边栏无「批量操作」「会计管理」「客户分配」菜单
- TDD：先写 AppSidebar.test.ts（扩展）
  - `testAgencyMenuForAdmin` — AGENCY_ADMIN 菜单完整
  - `testAgencyMenuForAccountant` — ACCOUNTANT 菜单精简
  - `testAgencyMenuForReviewer` — REVIEWER 菜单精简
  - `testAgencyMenuForAssistant` — ASSISTANT 菜单精简
- 验证：组件测试通过

### MC6-07: Sprint 6 全量回归

- 目标：npm test 全部通过 + 前端构建成功 + 完整角色流程验证
- 验证清单：
  - [ ] `npm test` Failures=0
  - [ ] `npm run build` 无 TypeScript 错误
  - [ ] AGENCY_ADMIN 登录 → 侧边栏含「会计管理」「客户分配」
  - [ ] AGENCY_ADMIN → 会计管理 → 创建会计 → 分配客户
  - [ ] ACCOUNTANT 登录 → 侧边栏无「会计管理」「客户分配」
  - [ ] ACCOUNTANT → 企业切换器仅显示分配的企业
  - [ ] ACCOUNTANT → 访问 /agency/accountant-list → 跳转 403
  - [ ] REVIEWER 登录 → 侧边栏无「批量操作」
  - [ ] ASSISTANT 登录 → 侧边栏无「批量操作」「会计管理」「客户分配」
  - [ ] 角色标签在 EnterpriseSwitcher 中正确显示
- 验证：npm test + npm run build

---

## 验收标准

- `npm test` Failures=0
- `npm run build` 无 TypeScript 错误
- 4 种角色登录后菜单可见性正确
- 会计管理页面 CRUD + 启停功能可用
- 客户分配页面分配/取消分配功能可用
- EnterpriseSwitcher 按角色过滤企业列表 + 显示角色标签
- 路由守卫按 agencyRole 正确拦截
- 侧边栏按 agencyRole 正确显示/隐藏菜单

## 风险

| 风险 | 缓解 |
|------|------|
| auth.store 状态膨胀 | 使用 computed 属性封装角色判断逻辑，保持 store 简洁 |
| 前端角色判断与后端不一致 | 前端仅做 UI 控制（菜单/路由），数据权限以后端为准 |
| TypeScript 类型新增 agencyRole | 在 UserInfo 接口中新增 agencyRole: string |
| 组件测试 Mock 复杂 | 复用现有 auth.store Mock 模式，仅扩展 agencyRole 字段 |

## 与 Sprint 4 的关系

Sprint 4 完成了 V1.0 前端适配（客户列表 + 批量操作 + 企业切换器 + 路由守卫按 userType 分发）。Sprint 6 在 Sprint 4 基础上：

- **扩展** auth.store（加 agencyRole + computed）
- **扩展** EnterpriseSwitcher（加角色标签 + 角色过滤）
- **扩展** 路由守卫（加 agencyRole 校验）
- **扩展** 侧边栏（加角色菜单控制）
- **新增** 会计管理页面
- **新增** 客户分配页面
