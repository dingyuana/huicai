# 编码规范（慧财财务）

> **编号**：HUICAI-DEV-010
> **版本**：V2.0 | **修改日期**：2026-07-20 | **修改人**：Hermes
> **适用范围**：慧财财务系统后端 Java、前端 Vue、AI 服务 Python
> **关联文档**：[技术方案](../技术方案.md) | [分层架构规范](../architecture/分层架构规范.md) | [测试标准](../testing/standards/TESTING_STANDARD.md)

---

## 一、通用规范

### 1.1 命名约定

| 语言 | 类型 | 规范 | 示例 |
|------|------|------|------|
| Java | 类名 | 大驼峰 | `BankStatementService` |
| Java | 方法名 | 小驼峰 | `importBankStatement` |
| Java | 常量 | 全大写下划线 | `MAX_RETRY_COUNT` |
| Java | 枚举 | 大驼峰，常量全大写 | `InvoiceStatus.PENDING_CONFIRM` |
| Java | 包名 | 全小写，点分隔 | `com.huicai.sme.tax` |
| Python | 模块 | 蛇形 | `classification_agent.py` |
| Python | 类名 | 大驼峰 | `ClassificationAgent` |
| TypeScript | 变量/函数 | 小驼峰 | `confirmInvoice` |
| TypeScript | 组件 | 大驼峰 | `BankStatementView.vue` |

### 1.2 注释约定

- 公共 API 必须写 Javadoc / Docstring
- 复杂业务逻辑必须写行注释说明意图
- TODO 必须包含日期和负责人（`// TODO 2026-07-20 老丁：需要补充异常处理`）
- 禁止在注释中保留被删除的代码片段

### 1.3 错误处理

- 使用统一的 `BusinessException`（`com.huicai.common.exception.BusinessException`）
- 严禁吞掉异常（`catch` 后必须处理或 rethrow）
- 错误信息必须清晰，包含业务上下文
- 禁止使用 `e.printStackTrace()` 或 `System.out.println()`

### 1.4 日志规范

- 使用 Lombok `@Slf4j` 注解获取 Logger
- 关键业务操作必须记录日志（`log.info("xxx: 单据 {} 已审核通过", id)`）
- 异常日志必须记录完整堆栈（`log.error("xxx 失败", e)`）
- 禁止记录敏感信息（密码/身份证/银行卡号）

---

## 二、Java 规范（适用于 OpenCode 代码生成）

### 2.1 包结构规则

当前架构使用 `base` / `sme` / `agency` 三层包结构，`com.huicai` 下：

```
com.huicai.base           ← 底座模块（所有企业通用）
  ├── subject/            ← 科目体系
  ├── period/             ← 会计期间
  ├── voucher/            ← 凭证引擎
  ├── balance/            ← 科目余额
  ├── report/             ← 报表引擎
  ├── auth/               ← RBAC 权限
  ├── masterdata/         ← 基础数据（客商/部门/员工）
  ├── audit/              ← 审计日志
  └── config/             ← 系统配置

com.huicai.sme            ← SME 分支（中小微企业）
  ├── tax/                ← 发票税务（进销项）
  ├── arap/               ← 应收应付/核销/费用报销
  ├── cash/               ← 资金管理/银行流水/对账
  ├── asset/              ← 固定资产
  ├── budget/             ← 预算管理
  └── salary/             ← 工资薪酬（待建）

com.huicai.agency         ← Agency 分支（代账）
  ├── tenant/             ← 多客户账套（待建）
  ├── batch/              ← 批量操作（待建）
  ├── client/             ← 客户CRM（待建）
  └── mobile/             ← 移动端审批（待建）

com.huicai.common         ← 公共组件
  ├── exception/           ← BusinessException + 错误码
  ├── entity/              ← 基础实体基类
  ├── util/                ← 工具类
  └── config/              ← 自动配置
```

**新增模块时必须放入正确的包**，严禁放入 `com.huicai.module` 下层（旧结构正在迁移）。

### 2.2 分层依赖规则（强制）

见 [分层架构规范](../architecture/分层架构规范.md)，核心规则：

| 层 | 包路径 | 允许导入 | 禁止导入 |
|----|--------|---------|---------|
| L0 Entity | `*.entity/` | 本层类 | 任何 Service/Controller |
| L0 DTO | `*.dto/` | 本层类 | 任何 Service/Controller |
| L0 VO | `*.vo/` | 本层类 | 任何 Service/Controller |
| L1 Mapper | `*.mapper/` | L0 | L2/L3 |
| L2 Service | `*.service/` | L0, L1 | L3 (Controller) |
| L3 Controller | `*.controller/` | L0, L2 | L1 (Mapper) |

### 2.3 Entity 规范

```java
/**
 * 实体类命名规范：XxxEntity（对应表 t_xxx）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_business_doc")
public class BusinessDocEntity extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("doc_no")
    private String docNo;

    // 金额字段：NUMERIC(18,2) 映射到 BigDecimal
    private BigDecimal amount;

    // 状态字段：VARCHAR(32) 映射到 String
    private String status;

    // 枚举映射：使用 String 类型，通过转换器或手动处理
    // 不强制使用 MyBatis TypeHandler，保持简单
}
```

**规则：**
- 所有实体继承 `BaseEntity`（含 `id`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`）
- `@TableName` 必须显式指定表名（`t_xxx`）
- 主键统一 `@TableId(type = IdType.AUTO)`，BIGINT 自增
- 金额字段：`private BigDecimal xxx`，数据库 `NUMERIC(18,2)`
- 税率字段：`private BigDecimal taxRate`，数据库 `NUMERIC(5,2)`，存储百分比整数（13=13%）
- 状态字段：`private String status`，数据库 `VARCHAR(32)`
- 不为空字段加 `javax.validation.constraints.NotBlank` / `@NotNull`
- 字段注释加 `@Schema(description = "xxx")`（Swagger）

### 2.4 Mapper 规范

```java
@Mapper
public interface BusinessDocMapper extends BaseMapper<BusinessDocEntity> {
    // 简单 CRUD 继承 BaseMapper 即可，无需额外方法
    // 复杂查询使用 @Select / @Update 注解或 XML
}
```

**规则：**
- 继承 `BaseMapper<Entity>` 获得 CRUD
- 复杂连表 SQL 拆分为多次查询，Java 层组装（禁止写复杂 join）
- 必须使用逻辑删除（`deleted=0`），MyBatis-Plus 自动处理
- 分页使用 `Page<Entity>` + `selectPage`
- XML 文件在 `src/main/resources/mapper/` 下，命名 `XxxMapper.xml`

### 2.5 Service 规范

```java
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class XxxServiceImpl implements XxxService {
    private final XxxMapper xxxMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<XxxVO> create(XxxCreateDTO dto) {
        // 1. 参数校验
        // 2. DTO → Entity 转换
        // 3. 业务逻辑
        // 4. 保存
        // 5. Entity → VO 转换
        // 6. 返回
    }
}
```

**规则：**
- 使用构造器注入（`@RequiredArgsConstructor` + `private final`），禁止 `@Autowired` 字段注入
- Service 类必须有 `@Transactional(rollbackFor = Exception.class)` 类级别注解
- 只读方法加 `@Transactional(readOnly = true)`
- 方法名规范：`createXxx`, `updateXxx`, `deleteXxx`, `getXxx`, `pageQueryXxx`
- DTO 入参校验：使用 `@Validated` + Bean Validation 注解
- 状态机方法：`submitXxx`, `approveXxx`, `rejectXxx`, `reverseXxx`, `voidXxx`

### 2.6 Controller 规范

```java
@RestController
@RequestMapping("/api/v1/xxx")
@RequiredArgsConstructor
@Slf4j
public class XxxController {
    private final XxxService xxxService;

    @PostMapping
    public ApiResult<XxxVO> create(@Valid @RequestBody XxxCreateDTO dto) {
        return ApiResult.success(xxxService.create(dto));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<XxxVO>> pageQuery(
            @Valid XxxPageQueryDTO dto) {
        return ApiResult.success(xxxService.pageQuery(dto));
    }
}
```

**规则：**
- Controller 只做参数转换和路由，不包含业务逻辑
- 响应统一使用 `ApiResult<T>`（`{ code, message, data, traceId }`）
- 分页响应使用 `PageResult<T>`（`{ list, total, page, size }`）
- 路径：`/api/v1/{模块}/{资源}`，kebab-case 复数名词
- 基础路径 `/api/v1/` 已在 `application.yml` 配置，无需在 `@RequestMapping` 重复
- 所有入参使用 `@Valid` / `@Validated` 校验

### 2.7 DTO / VO 转换规范

**禁止在 Entity 中暴露业务方法。** 转换统一在 Service 层完成：

```java
// 方式一：手动转换（推荐，显式且可控）
public XxxVO toVO(XxxEntity entity) {
    XxxVO vo = new XxxVO();
    BeanUtils.copyProperties(entity, vo, "sensitiveField");
    // 手动设置需要转换的字段
    return vo;
}

// 方式二：使用 MapStruct（如果项目已引入）
@Mapper(componentModel = "spring")
public interface XxxConverter {
    XxxVO toVO(XxxEntity entity);
}
```

**规则：**
- 禁止在 Controller 层直接返回 Entity
- 禁止在 Entity 中加 `@JsonIgnore` 等序列化注解（VO 负责控制序列化）
- 敏感字段（密码、token）在转换时排除

### 2.8 异常体系

统一使用 `BusinessException`：

```java
// 抛出
throw new BusinessException(ErrorCode.PARAM_ERROR, "单据号不能为空");
throw new BusinessException(20101, "借贷不平衡");

// 在 ControllerAdvice 中统一处理
@ExceptionHandler(BusinessException.class)
public ApiResult<Void> handleBusinessException(BusinessException e) {
    return ApiResult.error(e.getCode(), e.getMessage());
}
```

**错误码段分配：**

| 段 | 业务域 | 示例 |
|----|--------|------|
| 20000-20099 | 系统级 | 20001=参数校验失败, 20002=未授权 |
| 20100-20199 | 业财底座 | 20101=借贷不平衡, 20103=期间已锁定 |
| 20200-20299 | 税务发票 | 20201=发票验真失败 |
| 20300-20399 | 资金核销 | 20301=核销金额超出余额 |
| 20400-20499 | AI 层 | 20401=置信度过低 |
| 20500-20599 | 工资薪酬 | 20501=员工已有工资表 |

**新增错误码规则：** 在对应段内取下一个可用数值，添加到 `ErrorCode` 常量类并注释用途。

### 2.9 事务规范

| 场景 | 注解位置 | 说明 |
|------|---------|------|
| 单表操作 | Service 类级别 | 默认 `@Transactional(rollbackFor = Exception.class)` |
| 只读查询 | 方法级别 | `@Transactional(readOnly = true)` |
| 跨模块调用 | Service 方法 | 事务传播到调用方，确保跨模块一致性 |
| 异步操作 | 不可用 | 异步方法不能有 `@Transactional`，需独立事务 |

**禁止：** Controller 层加 `@Transactional`，Mapper 层直接加 `@Transactional`。

### 2.10 状态机编码规范

所有状态机类必须实现 `BaseStateMachine<Entity, Status>` 接口（或遵循相同模式）：

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class XxxStateMachineServiceImpl implements XxxStateMachineService {
    private final XxxMapper xxxMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        XxxEntity entity = xxxMapper.selectById(id);
        // 1. 前置校验
        Assert.state(Status.PENDING_REVIEW.equals(entity.getStatus()),
            "当前状态不允许审核通过");

        // 2. 状态转换
        entity.setStatus(Status.CONFIRMED.name());
        entity.setAuditedBy(SecurityUtils.getUserId());

        // 3. 副作用（创建下游单据等）
        // ...

        // 4. 保存
        xxxMapper.updateById(entity);

        // 5. 审计日志
        auditLogService.log("CONFIRM", entity);
    }
}
```

**强制规则：**
- 每个状态转换方法必须有独立测试
- 每个测试至少有 1 条负向断言（不该做的没做）
- 所有状态转换必须记录审计日志
- 终态（VOIDED, REVERSED, FULLY_RECONCILED）不可再转换

---

## 三、前端规范（Vue 3 + Element Plus）

### 3.1 目录结构

```
frontend/src/
├── api/              ← API 封装（L1 数据访问）
├── composables/      ← 组合式函数（L2 业务逻辑）
├── views/            ← 页面组件（L3 视图层）
├── router/           ← 路由定义
├── store/            ← Pinia 状态管理
└── utils/            ← 工具函数
```

### 3.2 组件规范

- 使用 Composition API（`<script setup>`）
- 组件必须有 Props 类型定义（`defineProps<{...}>()`）
- API 调用封装在 `api/` 目录下，组件不直接调用 axios
- 使用 Pinia 做状态管理，组件间共享数据
- 使用 Element Plus 组件库，不自行封装基础组件

### 3.3 路由规范

```typescript
// 路由路径映射包结构
base/*     → 底座功能
sme/*      → SME 功能（当前主要）
agency/*   → Agency 功能（待建）
```

---

## 四、测试规范（详见测试文档）

### 4.1 强制要求

| 修改类型 | 必须补充的测试 |
|---------|--------------|
| 新增业务模块 | Service 测试 + Controller 测试 + Mapper 真实 DB 测试 |
| 修改状态机 | 状态流转测试 + 负向断言 |
| 新增/修改 DB 字段 | Mapper 真实 DB 测试 |
| 修改 Controller 参数 | Controller 参数绑定测试 |
| 跨模块流程变更 | E2E 流程测试 |

### 4.2 测试框架

- Java：JUnit 5 + Mockito + Testcontainers（真实 DB）+ MockMvc（Controller）
- 前端：Vitest + Vue Test Utils + Playwright（E2E）
- Python：pytest + httpx

详细规范见 [TESTING_STANDARD.md](../testing/standards/TESTING_STANDARD.md) 和 [test-methodology.md](../testing/test-methodology.md)。

---

## 五、SPEC 编写规范（面向 OpenCode）

### 5.1 强制格式

每个 SPEC 必须包含以下四段（SDD 模板）：

```
## 1. 输入契约     — 参数/类型/约束/前置条件
## 2. 输出契约     — 响应结构/成功/失败
## 3. 状态流转     — 状态机图/合法/非法转换
## 4. 异常处理     — 异常场景/错误码/降级策略
```

### 5.2 BDD 验收标准

验收标准必须使用 Given-When-Then 格式：

```
### 场景：审核通过
**Given** 状态为 PENDING_REVIEW
**When** 调用 confirm()
**Then** 状态变为 CONFIRMED
**And** 不自动创建凭证
```

详见 [SPEC-CONTRACT-SCHEMA.md](../specs/SPEC-CONTRACT-SCHEMA.md) v2.0。

---

## 六、规范进化记录

### V2.0 (2026-07-20)
- 新增包结构规则（base/sme/agency 三层）
- 新增 Entity/Mapper/Service/Controller 代码模板
- 新增 DTO/VO 转换规范
- 新增异常体系 + 错误码段分配
- 新增状态机编码规范
- 新增事务规范
- 新增 SPEC 编写规范

### V1.0 (2026-06-xx)
- 初始创建：命名/注释/错误处理/日志基础规范

### 坑点记录

| 坑点 | 避免方案 |
|------|---------|
| Spring 循环依赖 | 使用 `@Lazy` + 构造器注入 |
| Flyway 迁移版本冲突 | 每次迁移前检查 `flyway_schema_history` 最新版本 |
| CORS 配置遗漏 | 开发环境明确允许 localhost 端口 |
| @Transactional 在 Controller 层 | 强制禁止，只放 Service 层 |
| Entity 直接返回给前端 | 使用 VO 转换，禁止序列化 Entity |
| 金额精度丢失 | 必须使用 `BigDecimal`，禁止 `double`/`float` |
| taxRate 忘记除以 100 | 存储百分比整数，计算时 `amount * taxRate / 100` |