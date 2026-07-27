# 慧财财务 — 测试策略规范

> **编号**：HUICAI-TST-008
> **版本**：V1.0 | **修改日期**：2026-07-27 | **修改人**：Hermes | **修改内容**：创建完整测试策略规范
> **- 类型**: quality / test | **优先级**: high
> **- 依赖**: docs/testing/test-prevention-mechanism.md, docs/testing/test-methodology.md
> **- 执行工具**: JUnit5 / Vitest / Playwright / Maven / npm

---

## 1. 目标与原则

### 1.1 测试目标

| 目标 | 说明 | 衡量标准 |
|------|------|----------|
| **功能正确性** | 所有业务逻辑按需求实现 | 单元测试覆盖核心算法 100%，E2E 覆盖主流程 |
| **数据一致性** | 多表事务、状态机转换无脏数据 | 集成测试事务回滚验证，覆盖率 100% |
| **系统稳定性** | 高并发下不崩溃、响应在阈值内 | 压力测试 QPS ≥ 定义阈值，99th 延迟 < 500ms |
| **回归保护** | 每次代码变更自动验证旧功能 | CI 流水线每日全量跑测，失败率 = 0 |
| **缺陷预防** | 在编码阶段阻断已知错误模式 | 静态检查 + 测试前置（见 test-prevention-mechanism.md） |

### 1.2 核心原则

1. **金字塔优先** — 单元 > 集成 > API > E2E，比例 ~7:2:1，越往上层用例越少、价值越高
2. **左移门禁** — 单元测试不通过 → PR 合并阻塞；API 层集成测试 → nightly gate
3. **自动化为主** — 回归、冒烟、边界全部自动化；探索性、验收场景手工补充
4. **数据隔离** — 每个租户/企业独立数据库或 schema，测试前 clean/seed，测试后 rollback
5. **可观察断言** — 测试不仅检查结果，还要记录日志/截图/网络请求，便于调试

---

## 2. 测试分层体系

### 第 1 层：单元测试（Unit Test）— Java / Spring Boot

**适用对象：** Service / Repository / Enum / Utils / 状态机等最小逻辑单元

| 要素 | 要求 |
|------|------|
| **框架** | JUnit 5 + Mockito + AssertJ |
| **注解** | `@ExtendWith(MockitoExtension.class)`、`@MockBean`、`@InjectMocks` |
| **DB 依赖** | **严禁真实 DB**，所有 DAO/远程调用 Mock |
| **事务** | 无需回滚（无真实事务），每条测试自行清理状态 |
| **执行速度** | < 100ms / 条，1000 条应在 10s 内完成 |
| **覆盖对象** | 业务逻辑分支（if/else）、异常路径、边界条件 |
| **示例** | `OutputInvoiceStateMachineServiceImplTest.confirm_invalidState_throwsException()` |

**编写质量检查清单（单测提交必过）：**

```text
[ ] @Mock 所有外部依赖（Mapper/Service/Redis）
[ ] 测试方法名遵循 <method>_<scenario>_<expected>(xxx) 格式
[ ] 至少一条负向断言（抛出异常 / 返回值非预期）
[ ] 没有 Thread.sleep() / hard coded 等待
[ ] 不使用真实数据库连接
[ ] @Test 有 @DisplayName 描述（可选但推荐）
[ ] 测试类有 @Sliced(AmitestLevel.UNIT) 注释
```

---

### 第 2 层：集成测试（Integration Test）— Spring + Testcontainers

**适用对象：** DAO+Service 组合、跨 Service 调用、事务边界、SQL 查询、外部依赖交互

| 要素 | 要求 |
|------|------|
| **框架** | JUnit 5 + Spring Boot Test + Testcontainers |
| **数据库** | Testcontainers 启动临时 Postgres 容器（每条测试独立）或 H2 in-memory |
| **事务** | `@Transactional` + `@Rollback`，测试结束后自动回滚 |
| **执行速度** | 1~5s / 条（含容器启动），建议并行执行 |
| **覆盖对象** | SQL 查询正确性、事务隔离级别、级联删除、序列生成、Redis 交互 |
| **示例** | `VoucherNoServiceIntegrationTest.generateNextNo_noDuplicationUnderConcurrent()` |

**关键测试场景：**

```java
@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=integration")
class VoucherNoServiceIT {

    @Autowired private VoucherNoService service;
    @Autowired private RedisTemplate<String,Object> redis;

    @Test
    @Repeat(10) // 重复多次增强发现概率
    void generateNextNo_isAtomic() throws InterruptedException {
        List<String> results = Executors.newFixedThreadPool(20)
            .invokeStream(IntStream.range(0,100).boxed(), i -> 
                CompletableFuture.completedFuture(service.generateNextNo("202607", 1L)))
            .collect(Collectors.toList());

        assertAll(
            () -> results.stream().distinct().count() == 100,      // 无重复
            () -> results.allMatch(r -> r.startsWith("JZ202607")), // 格式正确
            () -> new HashSet<>(results).size() == 100             // set去重后数量不变
        );
    }
}
```

---

### 第 3 层：API 功能测试（API Functional Test）— RESTful 接口级

**适用对象：** Controller 层的每个 HTTP 接口，含认证、权限、参数校验、返回体结构

| 要素 | 要求 |
|------|------|
| **框架** | RestAssured / Spring MVC Test (MockMvc) |
| **环境** | Spring Boot TestContext + @AutoConfigureTestDatabase(replace=NONE) 使用真实 DB（Testcontainers）或内存 DB |
| **认证** | 获取 test token（建 test 用户，固定密码）或在 filter 中 bypass |
| **覆盖** | 每个 CRUD 接口 + 动作接口（confirm/reject/void）的所有合法/非法输入 |
| **断言** | HTTP 码 + JSON Schema 校验 + DB 状态二次验证 |
| **示例** | `TaxControllerApiTest.confirm_success_createsBusinessDocAndVoucher()` |

**测试分类矩阵：**

| 维度 | 正向 | 负向 | 边界 |
|------|------|------|------|
| **参数** | 合法完整 payload | null/empty 字段 | 超长字符串、特殊字符 |
| **认证** | 有效 token | 过期/非法 token / 无 token | 不同角色（ADMIN vs ACCOUNTANT） |
| **资源ID** | 存在的 id | 不存在的 id | 负数/极大值 |
| **状态转换** | PENDING→CONFIRMED | CONFIRMED→PENDING（反向） | 已确认状态下重复确认 |
| **并发** | 单人顺序操作 | 同一发票多端并发确认 | 乐观锁版本号冲突 |

---

### 第 4 层：前端组件测试（Component Test）— Vue 3 + Vitest

**适用对象：** 高交互组件、含复杂 Vue 逻辑的状态管理组件、弹窗表单

| 要素 | 要求 |
|------|------|
| **框架** | Vitest + Vue Test Utils + Jest DOM |
| **渲染方式** | `shallowMount`（隔离子组件）或 `mount`（完整树） |
| **Mock 服务** | `vi.mock('@/api/modules/tax', () => ({ ... }))` |
| **断言** | 渲染输出 + 事件触发 + 状态变化 |
| **示例** | `InvoiceList.spec.ts.filterByCustomerShowsCorrectRows()` |

**关键测试项：**

- 组件 props 传入后是否正确渲染
- 按钮禁用态（form invalid / loading）是否正确
- 点击事件是否触发了正确的 method/handlers
- 是否正确调用了 API（mock 后验证调用参数）
- 插槽内容是否正确渲染
- 路由导航后组件是否正确重置状态

---

### 第 5 层：端到端测试（E2E）— Playwright

**适用对象：** 核心业务流程、页面加载完整性、浏览器层面的交互

当前覆盖范围：

| Spec | 描述 | 测试数 | 频率 |
|------|------|--------|------|
| `01-login.spec.ts` | 多角色登录验证 | 1 | PR smoke |
| `02-menu-navigation.spec.ts` | 菜单跳转 + 页面加载 | 5 | PR smoke |
| `03-output-invoice.spec.ts` | 开票列表、新增、编辑 | 3 | 每日 nightly |
| `04-page-smoke.spec.ts` | **39 个**业务页面加载 + 网络错误捕获 | **39** | 每日 nightly |
| *待扩展* | 完整开票-审核-核销-报表全流程 | - | Release candidate |

**Smoke 测试（PR 快速失败）：** 仅跑 `01-login` + `02-menu-navigation` + `04-page-smoke` 子集（前 10 页），< 2min

**Nightly 全量：** 全量 E2E suite，每晚 2:00 Cron 触发，报告告发到飞书

**Full Flow（Release 前）：** 完整业务流（创建→审核→凭证→报表），≈ 15min

**Playwright 测试最佳实践：**

```typescript
// e2e/tests/04-page-smoke.spec.ts
import { test, expect, Page } from '@playwright/test';
import { createErrorTracker } from './helpers';

// 全局 beforeAll：统一设置 error tracker
test.beforeEach(async ({ page }) => {
    const tracker = createErrorTracker(page);
    page.on('response', tracker.trackResponse);
    page.onerror = tracker.logError;
});

// 参数化页面遍历
const PAGE_CONFIGS = [
    { path: '/tax/output-invoice', name: '销项发票' },
    { path: '/tax/input-invoice', name: '进项发票' },
    // ...全部 39 个路由
];

test.describe('Page Smoke Test', () => {
    PAGE_CONFIGS.forEach(({ path, name }) => {
        test(`should load ${name} without 500 errors`, async ({ page }) => {
            await page.goto(path);
            // 等待主内容（避免 polling 干扰）
            await page.waitForLoadState('load');
            await page.waitForTimeout(2000); // 缓冲期
            
            // 断言：error tracker 没收到任何 server errors
            expect(tracker.hasServerError()).toBe(false);
            
            // 断言：主区域有内容（避免空页面）
            await expect(page.locator('main')).toHave.textContent();
        });
    });
});
```

---

## 3. 专项测试计划

### 3.1 性能测试

| 指标 | 场景 | 阈值 | 工具 | 频率 |
|------|------|------|------|------|
| 平均响应时间 | 发票列表（10k 行分页） | < 800ms | k6 / JMeter | 大版本前 |
| 并发 TPS | 批量导入发票（100 条/次） | ≥ 20 QPS | k6 | 大版本前 |
| 内存峰值 | 长时间运行（24h） | OOM 不出现 | Prometheus/Grafana | 月度 |
| 数据库连接池 | 满负载下连接等待 | < 100ms | Gatling | 月度 |

**实施脚本示例（k6）：**

```javascript
// scripts/performance/invoices-import.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    vus: 50,
    duration: '5m',
};

export default function () {
    const res = http.post('http://backend:8080/api/sme/tax/v1/tax/output-invoices', {
        customerName: 'Test Corp',
        invoiceDate: '2026-07-01',
        amount: 1000,
        taxRate: 13,
    });

    check(res, { 'status is 201': (r) => r.status === 201 });
    sleep(0.1);
}
```

### 3.2 安全测试

| 类别 | 测试项 | 手段 | 频率 |
|------|--------|------|------|
| 认证授权 | 越权访问（A 看 B 的发票） | 用不同 token 遍历相同 ID | 每 PR 随机抽查 |
| SQL 注入 | 搜索框注入 `' OR 1=1 --` | 手动 payload + ZAP 扫描 | 每月 |
| XSS | 备注输入 `<script>alert()</script>` | 输出是否转义 + ZAP | 每月 |
| Token 泄露 | localStorage token 是否 HttpOnly Cookie | 审计前端存储 + 代码 review | 架构评审时 |
| 敏感数据 | 密码/银行卡是否加密存储 | 查 DB + 代码 review | 每次上线前 |

### 3.3 数据迁移回归测试

每条 Flyway migration 自动执行校验：

```sql
-- 在 migration 脚本内嵌校验段
-- CHECK: t_business_doc.enterprise_id NOT NULL 约束已存在
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name='t_business_doc' AND column_name='enterprise_id' AND is_nullable='NO'
    ) THEN
        RAISE NOTICE 'CHECK PASS: enterprise_id is NOT NULL';
    ELSE
        RAISE EXCEPTION 'CHECK FAIL: enterprise_id is nullable';
    END IF;
END $$;
```

CI 中执行 `mvn flyway:migrate -Dflyway.checkStatements=true`，失败的 migration 直接阻断构建。

---

## 4. CI/CD 流水线集成

```
┌─────────────┐       ┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│   Git Push  │──────▶│  Lint & Style│──────▶│ Unit Test    │──────▶│  Build       │
│ (PR branch) │       │  (pre-commit)│       │ (JUnit/Vitest)│       │ (Docker img) │
└──────┬──────┘       └──────┬───────┘       └──────┬───────┘       └──────┬───────┘
         │                    │                      │                      │
         ▼                    ▼                      ▼                      ▼
   ┌────────────┐       ┌─────────────┐       ┌─────────────┐       ┌──────────────┐
   │  Integration│──────▶│   API Test  │──────▶│   E2E Smoke │──────▶│ Deploy to   │
   │  Test       │       │ (RestAssured)│       │ (Playwright) │       │   Staging   │
   └──────┬──────┘       └──────┬──────┘       └──────┬──────┘       └──────┬───────┘
          │                     │                      │                      │
          ▼                     ▼                      ▼                      ▼
┌────────────┐           ┌──────────────┐        ┌─────────────┐     ┌─────────────────┐
│ Merge to    │◀─────────┘             │◀───────┘ Nightly    │     │  Full Release   │
│ develop     │                       QA Gate                  │     │ Candidate Test  │
└─────────────┘                         └─────────────────────┘     └─────────────────┘
```

**关键 Gate 规则：**

| Gate | 条件 | 动作 |
|------|------|------|
| **Pre-commit** | ESLint / Checkstyle / Prettier 报错 | 阻断提交 |
| **PR build** | Unit test 失败 / 覆盖率下降 > 5% | PR 评论 failure，禁止 merge |
| **Staging deploy** | E2E smoke 测试失败（>2 页面 500） | 自动回滚部署 |
| **Nightly run** | API 集成测试失败 > 5% / E2E 失败 > 2 用例 | Slack 告发 Owner |
| **Release** | 没有 open 的 P0/P1 Bug + 性能基线达标 | 发布审批通过 |

---

## 5. 覆盖率目标与度量

| 层级 | 工具 | 最低目标 | 统计方式 | 报告位置 |
|------|------|----------|----------|----------|
| Java 单元/集成 | JaCoCo + Maven | **≥ 80%**（整体），关键模块 ≥ 90% | `mvn jacoco:report` | PR 附带覆盖率 diff |
| Vue 组件 | Vitest + c8 | **≥ 70%** 分支覆盖 | `npm run test:ci -- --coverage` | Frontend build log |
| API 接口 | Swagger + custom script | **100%** API 路径至少一个用例 | OpenAPI spec 映射 table | API 文档附录 |
| E2E 业务流程 | Playwright reporter | 核心链路（登录→开票→核销→报表）**100%** 覆盖 | Daily Nightly report | Slack 频道 |

---

## 6. 缺陷分级与管理

| 级别 | 定义 | 处理时限 | 示例 |
|------|------|----------|------|
| **P0（致命）** | 数据丢失/损坏、核心流程不可用（如 confirm 持续 500）、安全风险 | 立即修复，阻塞 Release | 销项发票确认接口全体 500 |
| **P1（严重）** | 主要功能不可用、结果明显错误（金额偏差 > 0.01）、影响财务准确性 | 24h 内修复 | 报表计算金额偏差 > 0.01 |
| **P2（一般）** | 次要功能异常、UI 错位、边缘情况报错 | 下一个 Sprint 修复 | Excel 导出日期格式不对 |
| **P3（优化）** | 体验问题、文案笔误、轻微性能影响 | 排入 backlog | 加载动画稍慢 |

**流程：** GitHub Issue 打标签 `severity:P0/P1/P2/P3` → Weekly triage → 状态跟踪直到 Close。

---

## 7. 测试数据管理

### 7.1 租户隔离策略

| 环境 | enterprise_id | 备注 |
|------|--------------|------|
| Dev / Test | 9999 | 专用测试租户，每次测试前 full reset |
| Staging | 1 (seeded) | 接近生产的预灌数据 |
| Production | N/A | 严禁测试数据写入 |

### 7.2 种子数据生成

- 使用 Faker 库生成标准测试数据（客户、供应商、科目、期初余额）
- SQL seed 脚本置于 `src/test/resources/db/migration/V*_seed_test_data.sql`
- E2E 测试开始前自动调用 `/api/test/seeder` 端点重置数据

### 7.3 敏感数据脱敏

- 测试客户名 → `Test Customer N`（N=序号）
- 测试电话 → `138****XXXX`
- 测试邮箱 → `test{n}@example.com`
- 绝不使用真实生产数据进入测试环境

---

## 8. 相关责任方

| 角色 | 职责 |
|------|------|
| **Developer** | 编写自己负责模块的单元/组件测试；保证 own code 测试覆盖；修复 P0/P1 bug |
| **Tech Lead / Architect** | 审查测试策略；批准性能基线值；协调资源解决跨模块测试依赖 |
| **QA / Test Engineer**（若有） | 维护 API/E2E 套件；设计性能测试用例；Bug 跟踪与闭环 |
| **Product Owner** | 提供业务验收场景（Given/When/Example）；参与 P0/P1 验收测试 |

---

## 9. 相关文件清单

| 文件 | 作用 | 更新责任人 |
|------|------|-----------|
| `docs/testing/test-methodology.md` | 具体测试方法与检查清单 | Dev / Tech Lead |
| `docs/testing/test-prevention-mechanism.md` | 测试前置阻断机制（编译期检查等） | Dev |
| `docs/testing/test-coverage-matrix.md` | 实时更新的覆盖率仪表盘 | QA / Automation |
| `backend/src/test/java/...` | Java 单元测试与集成测试代码 | Dev |
| `frontend/src/__tests__/...` | 前端组件/API 测试 | Dev |
| `e2e/tests/...` | Playwright E2E 测试 | Dev / QA |
| `.github/workflows/ci.yml` | CI 流水线配置 | Tech Lead |

---

## 10. BDD 验收标准

### 场景 1：单元测试按时执行
**Given** 开发者在 PR 中提交了测试代码  
**When** GitHub Actions 触发 CI 流水线  
**Then** `mvn test` 在 10min 内完成并通过

### 场景 2：E2E smoke 测试快速反馈
**Given** 开发者推送 PR 到 feature branch  
**When** Playwright smoke 测试跑完  
**Then** 39 个页面加载测试中 ≤ 1 个失败，且失败页面在 30min 内有 Owner 响应

### 场景 3：缺陷阻断 release
**Given** 系统中存在未关闭的 P0 Bug  
**When** 尝试创建 Release tag  
Then CI 阻断发布流程，直到所有 P0 关闭

### 场景 4：覆盖率门禁生效
**Given** 新代码未写单元测试导致整体覆盖率下降 > 5%  
**When** PR 提交检查  
Then GitHub Action 标记为 failure，禁止 merge

---

## 附录：常用命令速查

```bash
# ==================== 后端 ====================
cd backend
mvn test                      # 运行全部单元测试
mvn test -Dtest=*Tax*Test     # 只跑 Tax 相关测试
mvn verify                    # 包含集成测试的全 build
mvn jacoco:report           # 查看 HTML 覆盖率报告

# ==================== 前端 ====================
cd frontend
npm test                      # Vitest 组件/单元测试
npm run test:ci -- --coverage # CI 模式下跑带 coverage
npx playwright test           # 运行全部 E2E
npx playwright test --grep-smoke # 只跑 smoke 标签

# ==================== E2E ====================
npx playwright test         # 全部 spec
npx playwright test 04-page-smoke  # 指定文件
npx playwright headed       # 打开浏览器看可视化过程
npx playwright show-trace   # 查看失败 trace

# ==================== Docker Compose =====================
docker compose up -d        # 启动所有服务（PG/Redis/MQ等）
docker compose logs -f backend # 实时看后端日志
docker compose restart backend # 重启后端容器
```

---

**文档最后修订：** 2026-07-27 | **下次复审：** 2026-10-27（或 major 版本发布前）