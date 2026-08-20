# PRD03：系统公共参数与平台 PRD

> **编号**：HUICAI-PRD-003
> **版本**：V1.0 | **日期**：2026-08-19
> **关联总 PRD**：`../CORE-需求分析.md`
> **关联设计**：DSN-基础数据管理.md（公共参数章节）
> **关联SPEC**：待建
> **对应包**：com.huicai.base.system / com.huicai.common
> **当前状态**：🟡 分散实现（编码规则在 VoucherNoService、参数在 application.yml、字典散落各 Service），待整合

---

## 1. 模块定位

系统公共参数模块是**全局基础设施层**，定义所有业务模块共享的配置规则和通用组件。它不承载任何业务逻辑，只负责：
- 统一编码生成规则（单据号/凭证号/发票号/其他业务编号）
- 全局可配置参数（小数位数、税号长度、金额精度等）
- 系统字典（币种、汇率、状态枚举、业务类型枚举）
- 导入导出框架、公共弹窗、打印基础框架

**核心原则**：所有业务模块**不得自行硬编码**编码规则或全局参数，必须通过公共参数模块提供的方法获取。

---

## 2. 功能清单

| 编号 | 功能点 | 优先级 | 当前状态 | 验收标准 |
|------|--------|--------|---------|---------|
| CFG-01 | 全局编码规则引擎 | P0 | ✅ VoucherNoService 已实现 | 所有单据号/凭证号通过统一接口生成，Redis INCR 原子自增 |
| CFG-02 | 系统参数配置 | P1 | ⚠️ application.yml + 硬编码混杂 | 参数写入 SysConfig 表，前端可配置，Service 读取 |
| CFG-03 | 系统字典管理 | P1 | ⚠️ 各 Service 散落实体类常量 | 统一字典表 + 前端配置中心 |
| CFG-04 | 导入导出公共组件 | P0 | ✅ EasyExcel 已集成 | 支持 Excel 导入/导出，模板可配置 |
| CFG-05 | 公共弹窗规范 | P2 | ✅ Element Plus Dialog | 所有弹窗遵循统一尺寸/标题/按钮规范 |
| CFG-06 | 打印基础框架 | P2 | ❌ 未实现 | 凭证打印/发票打印模板化，支持 PDF |
| CFG-07 | 系统字典 API | P1 | ⚠️ 部分实现（SysConfigService） | 提供统一 GET/POST/PUT 接口 |

---

## 3. 编码规则引擎（核心）

### 3.1 编码格式

| 单据类型 | 编号格式 | 示例 | 生成方式 |
|---------|---------|------|---------|
| 凭证 | `PZ + 年月(6位) + 序号(4位)` | `PZ2026070001` | Redis INCR |
| 业务单据 | `{类型前缀} + 年月(6位) + 序号(4位)` | `PAY2026070001` | Redis INCR |
| 销项发票 | `XS + 年月(6位) + 序号(6位)` | `XS202607000001` | 系统生成 + 允许手动录入 |
| 进项发票 | `JM + 年月(6位) + 序号(6位)` | `JM202607000001` | 系统生成 + 允许手动录入 |
| 应收单 | `YS + 年月(6位) + 序号(4位)` | `YS2026070001` | 自动从销项发票号生成 |
| 应付单 | `YF + 年月(6位) + 序号(4位)` | `YF2026070001` | 自动从进项发票号生成 |

### 3.2 编码生成约束

| 规则 | 说明 |
|------|------|
| 原子自增 | Redis INCR，支持并发，无重复号 |
| 按年分号 | 每年 1 月 1 日序号重置 |
| 跨账套隔离 | 同一编号在多个账套下可重复，通过 `enterprise_id` 隔离 |
| 红冲单据 | 红冲单据编号追加 `-H` 后缀（如 `PZ2026070001-H`） |
| 手动录入 | 发票允许手动录入编号，系统校验唯一性 |

---

## 4. 系统参数配置

### 4.1 参数表结构

```sql
CREATE TABLE t_sys_config (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    config_key      VARCHAR(64) NOT NULL UNIQUE,
    config_value    TEXT NOT NULL,
    config_type     VARCHAR(32) NOT NULL,   -- system/business/accounting
    description     VARCHAR(256),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 4.2 参数列表

| config_key | 默认值 | 类型 | 说明 |
|-----------|--------|------|------|
| `voucher.amount_scale` | `2` | accounting | 凭证金额小数位数 |
| `voucher.max_entries` | `100` | accounting | 单张凭证最大分录数 |
| `invoice.tax_number_length` | `20` | business | 纳税人识别号最大长度 |
| `period.format` | `yyyyMM` | system | 会计期间格式 |
| `doc.no_prefix_mapping` | `{}` | system | 单据类型前缀映射（JSON） |
| `currency.default` | `CNY` | business | 默认币种 |
| `exchange.rate_update` | `manual` | business | 汇率更新方式（manual/auto） |
| `import.max_rows` | `10000` | system | 单次导入最大行数 |
| `export.max_rows` | `100000` | system | 单次导出最大行数 |

---

## 5. 系统字典管理

### 5.1 字典分类

| 字典名称 | 值 | 说明 |
|---------|-----|------|
| 币种 CURRENCY | CNY/USD/EUR/GBP/JPY 等 | 支持手动增删 |
| 汇率类型 EX_RATE_TYPE | SPOT/MID | 中间价/即期汇率 |
| 单据状态 DOC_STATUS | DRAFT/SUBMITTED/AUDITED/CLOSED | 全局状态枚举 |
| 凭证方向 VOUCHER_DIR | DEBIT/CREDIT | 借/贷 |
| 余额方向 BAL_DIR | DEBIT/CREDIT | 借方/贷方 |

---

## 6. 公共组件

| 组件 | 说明 | 状态 |
|------|------|------|
| EasyExcel 导入导出 | 通用导入导出工具类，支持模板配置 | ✅ 已集成 |
| 统一弹窗（Dialog） | Element Plus Dialog，固定宽度/标题/按钮 | ✅ 已约定 |
| 全局异常处理 | `@RestControllerAdvice` 统一捕获，返回 `BusinessException` | ✅ 已实现 |
| 全局审计 AOP | `@AuditLog` 注解记录操作日志 | ✅ 已实现 |
| 数据权限拦截器 | MyBatis 拦截器自动注入 `enterprise_id` 条件 | ✅ 已实现 |
| 打印框架 | 凭证/发票打印模板化，支持 PDF 输出 | ❌ 待开发 |

---

## 7. 不做的事

| 不做 | 理由 |
|------|------|
| 工作流引擎 | 慧财审核为手动操作，非工作流驱动 |
| 多租户独立配置 | 全局参数共享，按账套隔离 |
| 第三方系统参数对接 | 非 MVP 范围 |
| 国际化 | 当前仅中文 |

---

## 8. API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/system/configs` | 获取全部参数 |
| GET | `/api/v1/system/configs?key={key}` | 获取单个参数 |
| PUT | `/api/v1/system/configs` | 更新参数 |
| GET | `/api/v1/system/dicts?dictName={name}` | 获取字典列表 |
| POST | `/api/v1/system/dicts` | 新增字典项 |
| PUT | `/api/v1/system/dicts/{id}` | 更新字典项 |

---

## 9. 验收标准

| ID | BDD 场景 |
|----|---------|
| CFG-AT-01 | Given 凭证号生成 When 并发 100 次调用 Then 所有编号唯一，无重复 |
| CFG-AT-02 | Given 新年度 1 月 1 日 When 生成凭证号 Then 序号从 0001 开始 |
| CFG-AT-03 | Given 参数配置表有配置 When Service 读取参数 Then 返回最新值，非硬编码 |
| CFG-AT-04 | Given 手动录入发票编号 When 编号已存在 Then 返回重复错误 |
| CFG-AT-05 | Given 导入 Excel 超过最大行数 When 调用导入接口 Then 返回超限错误 |

---

> **文档结束。**