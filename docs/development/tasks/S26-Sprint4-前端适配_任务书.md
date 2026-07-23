# S26 Sprint 4 任务书 — 前端适配

> 日期：2026-07-23 | 任务 ID：S26-S4 | 关联 SPEC：S-26 | 前置：Sprint 1 + Sprint 2 完成（Sprint 3 可并行）

## 目标
完成代理工作台前端、客户切换组件、批量操作 UI、路由守卫按 userType 分发。

## 前置条件
- Sprint 1 完成：多租户基础设施
- Sprint 2 完成：JWT 扩展（返回 userType/enterpriseId/agencyId/enterpriseList）、企业切换接口
- 现有前端 auth.store.ts 仅有 token/userInfo/permissions
- 现有前端 agency.ts 路由为空占位
- 现有 request.ts 无 X-Enterprise-Id header

## 任务清单（7 个微循环）

### MC4-01: auth.store 扩展
- 目标：加 currentEnterpriseId/userType/agencyId/enterpriseList 状态 + switchEnterprise 方法
- 涉及文件：frontend/src/stores/auth.store.ts
- 新增状态：
  - currentEnterpriseId: ref<number | null>(null)
  - userType: ref<string>('')  // SUPER_ADMIN / AGENCY / ENTERPRISE
  - agencyId: ref<number | null>(null)
  - enterpriseList: ref<EnterpriseSimple[]>([])
- 新增方法：
  - switchEnterprise(enterpriseId: number): 调用 POST /api/v1/enterprise/switch → 更新 currentEnterpriseId → localStorage 存储
  - isAgency: computed(() => userType.value === 'AGENCY')
  - isSuperAdmin: computed(() => userType.value === 'SUPER_ADMIN')（替换原 roles.includes(1) 硬编码）
- 登录时从返回数据填充 userType/agencyId/enterpriseList
- localStorage 新增 key: huicai_current_enterprise_id
- 契约：AGENCY 登录后 store.enterpriseList.length > 0
- TDD：先写 auth.store.test.ts（testAgencyLoginStore, testSwitchEnterprise, testIsAgencyComputed）
- 验证：Vitest 单元测试通过

### MC4-02: request.ts 拦截器
- 目标：请求头自动携带 X-Enterprise-Id
- 涉及文件：frontend/src/api/request.ts
- 在 request 拦截器中：
  - 从 localStorage 取 huicai_current_enterprise_id
  - 如果存在，设置 config.headers['X-Enterprise-Id'] = enterpriseId
- 在 response 拦截器中：
  - 如果返回 20003（跨租户拦截），提示并跳转到客户列表
  - 如果返回 20005（企业暂停），提示并退出
- 契约：所有 API 请求 header 含 X-Enterprise-Id（AGENCY 用户切换后）
- TDD：先写 request.test.ts（testEnterpriseHeader, testCrossEnterpriseError, testSuspendedEnterpriseError）
- 验证：API 单元测试通过

### MC4-03: 路由守卫 — 按 userType 分发
- 目标：登录后按 userType 跳转不同首页
- 涉及文件：frontend/src/router/index.ts
- 路由守卫改造：
  - 已登录访问 /login 时：
    - SUPER_ADMIN → /admin/dashboard
    - AGENCY → /agency/enterprise-list（如果未选企业）
    - AGENCY → /dashboard（如果已选企业）
    - ENTERPRISE → /dashboard
  - AGENCY 用户访问业务路由时，检查 currentEnterpriseId 是否已设置
  - 未设置则跳 /agency/enterprise-list
- 契约：AGENCY 用户登录后跳转 /agency/enterprise-list
- TDD：先写 router.test.ts（testUserTypeDispatch, testAgencyRedirectToEnterpriseList, testAgencyAccessBusinessRoute）
- 验证：路由测试通过

### MC4-04: 代理工作台页面 — 客户列表
- 目标：客户企业列表 + 切换 + 批量操作入口
- 涉及文件：
  - frontend/src/views/agency/EnterpriseList.vue
  - frontend/src/api/modules/agency.ts
  - frontend/src/router/routes/agency.ts（填充子路由）
  - frontend/src/types/models.ts（加 EnterpriseVO 类型）
- 页面功能：
  - 表格展示客户企业列表（名称/纳税人识别号/状态/合同到期/上次操作时间）
  - 搜索栏（企业名称/税号）
  - 操作列：进入企业空间 / 激活企业 / 暂停企业
  - 顶部统计卡片：总客户数/活跃客户/本月到期/待审核凭证
  - 批量操作按钮：批量导入/批量审核/批量结账
- 路由：
  - /agency/enterprise-list → EnterpriseList.vue
  - /agency/batch-operation → BatchOperation.vue（MC4-06）
- 契约：页面展示客户列表，点击"进入"跳转到 /dashboard（设置 currentEnterpriseId）
- TDD：先写 EnterpriseList.test.ts（testRenderList, testSearchEnterprise, testEnterEnterprise, testActivateEnterprise）
- 验证：组件测试通过

### MC4-05: 客户切换组件
- 目标：顶部导航栏客户切换下拉
- 涉及文件：frontend/src/layouts/components/EnterpriseSwitcher.vue
- 组件功能：
  - 显示当前客户名称（从 store.enterpriseList 中找 currentEnterpriseId 对应的 name）
  - 下拉列表显示所有绑定的客户
  - 点击切换：调用 store.switchEnterprise → 刷新当前页面数据
  - 切换后显示 Toast 提示
  - 样式参考多租户架构设计.md §6.2
- 在 AppHeader.vue 中引入 EnterpriseSwitcher（仅 AGENCY 用户显示）
- 契约：切换后所有页面数据更新为新企业
- TDD：先写 EnterpriseSwitcher.test.ts（testRenderCurrentEnterprise, testSwitchEnterprise, testOnlyVisibleForAgency）
- 验证：组件测试通过

### MC4-06: 批量操作页面
- 目标：批量导入/审核/结账 UI
- 涉及文件：frontend/src/views/agency/BatchOperation.vue
- 页面功能：
  - Tab 切换：批量导入 / 批量审核 / 批量结账
  - 批量导入 Tab：
    - 文件上传区（el-upload 拖拽上传）
    - 导入类型选择（销项发票/进项发票）
    - 导入结果表格（文件名/成功数/失败数/失败详情）
  - 批量审核 Tab：
    - 凭证列表（多选）
    - 批量审核按钮
    - 审核结果表格
  - 批量结账 Tab：
    - 企业列表（多选）+ 期间选择
    - 批量结账按钮
    - 结账结果表格
- 契约：批量操作页面可执行批量任务
- TDD：先写 BatchOperation.test.ts（testBatchImportUI, testBatchAuditUI, testBatchCloseUI, testTabSwitch）
- 验证：组件测试通过

### MC4-07: Sprint 4 全量回归 + E2E
- 目标：npm test 通过 + E2E 全流程测试
- 新增 E2E 测试：frontend/src/__tests__/e2e/agency-flow.spec.ts
- E2E 场景：
  1. AGENCY 用户登录 → 验证跳转客户列表
  2. 选择客户企业 → 验证进入业务空间
  3. 切换客户 → 验证数据更新
  4. 批量导入发票 → 验证导入结果
  5. 批量审核凭证 → 验证审核结果
  6. 批量结账 → 验证结账结果
- 契约：E2E 全流程通过
- 验证：npm test + npx playwright test 通过

## 验收标准
- npm test Failures=0
- npm run build 无 TypeScript 错误
- npx playwright test agency-flow.spec.ts 通过
- AGENCY 用户登录 → 客户列表 → 切换客户 → 业务操作 全流程可用
- 请求头自动携带 X-Enterprise-Id
- 路由守卫按 userType 正确分发
- 客户切换组件正常工作
- 批量操作页面可用

## 风险
- 前端 TypeScript 类型不匹配 → 参考现有类型定义，保持一致
- Playwright E2E 环境依赖后端服务 → 确保 Docker 环境启动
- 路由守卫逻辑复杂 → 充分测试各种 userType 组合
