# P26 SPEC — 凭证模板引擎实现规格书

> 状态：待实现 | 优先级：P0（核心引擎）
> 依据：`docs/DESIGN.md §20 凭证模板系统`、`docs/需求分析书_银行流水导入分类_V1.0.md`
> 目标：将凭证分录的科目映射从硬编码剥离为配置驱动，实现 TemplateEngine 变量替换 + TemplateMatcher 多维匹配
> 工期：3 批（P0 核心引擎 2 天 → P1 业务接入 2 天 → P2 种子数据+结转 1 天）

---

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 | 批次 |
|---|------|------|------|------|
| 1 | 创建 `TemplateEngine`（变量替换+金额表达式） | `common/util/TemplateEngine.java` | ✅ 低 | P0 |
| 2 | 创建 `AmountExpressionResolver`（四则运算） | `common/util/AmountExpressionResolver.java` | ✅ 低 | P0 |
| 3 | 创建 `TemplateMatcher`（多维匹配引擎） | `finance/service/TemplateMatcher.java` | 🟡 中 | P0 |
| 4 | `VoucherTemplateEntity` 新增 source/businessType/direction/matchPriority | Entity 文件 | ✅ 低 | P0 |
| 5 | `VoucherTemplateLineEntity` 新增 assistType/assistRequired | Entity 文件 | ✅ 低 | P0 |
| 6 | V48 迁移: t_voucher_template/t_voucher_template_line 新增字段 | Flyway | 🟡 中 | P0 |
| 7 | `AutoGenerationService` 改用新 TemplateEngine | Service 文件 | 🟡 中 | P0 |
| 8 | V49 迁移: 新增辅助核算种子数据 | Flyway | ✅ 低 | P0 |
| 9 | `BusinessDocServiceImpl.generateVoucher()` 改为查模板 | Service 文件 | 🟡 中 | P1 |
| 10 | `TaxService.generateVoucherFromInvoice()` 改为查模板 | Service 文件 | 🟡 中 | P1 |
| 11 | 前端模板编辑页支持 source/businessType/assistType | Vue 文件 | ✅ 低 | P1 |
| 12 | 辅助核算写入（模板行生成时挂载 assistJson）| 各生成点 | 🟡 中 | P1 |
| 13 | 辅助核算强校验拦截 | 拦截器 | ✅ 低 | P1 |
| 14 | V50 迁移: 15+ 种子模板 | Flyway | ✅ 低 | P2 |
| 15 | 期末结账触发模板制证（损益/增值税/汇兑）| PeriodCloseService | 🟡 中 | P2 |
| 16 | 删除 AutoGenerationService / BusinessDocServiceImpl 残留硬编码 | 清理 | ✅ 低 | P2 |

---

## 1. TemplateEngine（核心引擎）

**路径**: `com.huicai.common.util.TemplateEngine`

### 1.1 变量替换

```java
/**
 * 凭证模板引擎 — 变量替换 + 金额表达式解析.
 *
 * <p>支持变量:
 * - {{amount}} / {{taxAmount}} / {{totalAmount}} — 金额取数
 * - {客户名称} / {供应商名称} / {银行名称} / {员工姓名} — 业务摘要变量
 * - {月份} / {期间} — 期间变量
 * - {摘要} — 摘要文本
 *
 * 金额表达式: "{{amount}} - {{taxAmount}}" / "{{amount}} * 0.5"
 */
public class TemplateEngine {

    /** 渲染摘要模板: "付{供应商名称}货款" → "付华为技术货款" */
    public static String renderSummary(String template, TemplateContext ctx) { ... }

    /** 渲染金额模板: "{{amount}}" → BigDecimal(1000); "{{amount}}-{{tax}}" → BigDecimal(870) */
    public static BigDecimal renderAmount(String template, TemplateContext ctx) { ... }

    /** 渲染摘要中的所有变量 */
    private static String replaceVars(String template, TemplateContext ctx) { ... }
}
```

### 1.2 变量映射表

| 变量 | 取值来源 | 场景 |
|:-----|:---------|:-----|
| `{{amount}}` | ctx.amount | 所有模板 |
| `{{taxAmount}}` | ctx.taxAmount | 发票/含税场景 |
| `{{totalAmount}}` | ctx.amount + ctx.taxAmount | 价税合计 |
| `{客户名称}` | ctx.variables.get("customerName") | 收款/销售 |
| `{供应商名称}` | ctx.variables.get("vendorName") | 付款/采购 |
| `{银行名称}` | ctx.variables.get("counterpartyName") | 银行流水 |
| `{员工姓名}` | ctx.variables.get("employeeName") | 报销 |
| `{费用类型}` | ctx.variables.get("expenseType") | 报销 |
| `{月份}` | ctx.period | 期末结转 |
| `{年度}` | ctx.period.substring(0,4) | 期末结转 |
| `{摘要}` | ctx.summary | 所有模板 |
| `{单据号}` | ctx.variables.get("docNo") | 业务单据 |

### 1.3 金额表达式解析

支持的操作符：`+` `-` `*` `/` `(` `)`，以及 `{{amount}}` 等变量占位。

```java
// 示例
"{{amount}}"                     → ctx.amount
"{{amount}} - {{taxAmount}}"     → ctx.amount - ctx.taxAmount
"{{totalAmount}}"                → ctx.amount + ctx.taxAmount
"1000"                           → BigDecimal(1000)
"{{amount}} * 0.5"              → ctx.amount * 0.5
```

**解析规则**：
1. 先用变量值替换 `{{xxx}}` 占位
2. 用 ScriptEngine 或手工栈解析四则运算
3. 结果 `setScale(2, HALF_UP)`

---

## 2. AmountExpressionResolver

**路径**: `com.huicai.common.util.AmountExpressionResolver`

```java
/**
 * 金额表达式解析器.
 * 支持: + - * / ( ) 和 BigDecimal 操作数
 * 使用 Java ScriptEngine (Nashorn 替代) 或手工递归下降解析.
 */
public class AmountExpressionResolver {

    /** 解析表达式字符串, 返回 BigDecimal */
    public static BigDecimal evaluate(String expression, Map<String, BigDecimal> variables) { ... }
}
```

**实现选择**：使用 `javax.script.ScriptEngineManager` 或 `org.mariuszgromada.math.mxparser` 轻量库。
**安全**: 只允许数字 + 运算符 + 括号，禁止任意代码执行。输入 `expression` 先正则校验 `[0-9+\-*/().\s]+`。

---

## 3. TemplateContext

**路径**: `com.huicai.common.util.TemplateContext`（或内联在 TemplateEngine 中）

```java
@Data
@Accessors(chain = true)
public class TemplateContext {
    // ─── 匹配维度 ───
    private String source;          // BANK_STMT / BUSINESS_DOC / INVOICE / PERIOD_CLOSE
    private String businessType;    // RECEIPT / PAYMENT / EXPENSE / INVOICE_OUT / ...
    private String direction;       // in / out
    private String classification;  // bank_fee / interest_income / ...（兼容现有）

    // ─── 金额数据 ───
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;

    // ─── 业务变量 ───
    private String period;          // YYYYMM
    private String summary;
    private String counterpartyName;
    private String customerName;
    private String vendorName;
    private String employeeName;

    // ─── 辅助核算 ID ───
    private Long customerId;
    private Long vendorId;
    private Long deptId;
    private Long employeeId;
    private Long projectId;

    // ─── 扩展变量（key-value，用于摘要变量替换）───
    private Map<String, Object> variables = new HashMap<>();
}
```

---

## 4. TemplateMatcher（多维匹配引擎）

**路径**: `com.huicai.module.finance.service.TemplateMatcher`

### 4.1 匹配策略

```
匹配优先级:
  1. source + businessType + direction  (精确匹配)
  2. source + businessType              (业务类型匹配)
  3. classification                      (银行流水分类，兼容现有)
  4. 兜底: 返回 null（调用方自行降级）
```

### 4.2 接口

```java
@Service
public class TemplateMatcher {

    private final VoucherTemplateService templateService;

    /**
     * 根据上下文匹配合适的激活模板.
     * @return 匹配到的模板（含分录行），无匹配返回 null
     */
    public VoucherTemplateEntity match(TemplateContext ctx) {
        // 1. 精确匹配: source + businessType + direction
        VoucherTemplateEntity t = find(ctx.getSource(), ctx.getBusinessType(), ctx.getDirection());
        if (t != null) return t;

        // 2. 业务类型匹配: source + businessType（忽略 direction）
        t = find(ctx.getSource(), ctx.getBusinessType(), null);
        if (t != null) return t;

        // 3. 分类匹配: classification（兼容现有）
        if (StrUtil.isNotBlank(ctx.getClassification())) {
            t = templateService.matchByClassification(ctx.getClassification());
            if (t != null) return t;
        }

        return null;
    }

    private VoucherTemplateEntity find(String source, String businessType, String direction) {
        // 查 t_voucher_template WHERE source=? AND business_type=? AND [direction=?] AND is_active=true
        // 按 match_priority ASC, LIMIT 1
    }
}
```

### 4.3 新增 Mapper 查询

```java
// VoucherTemplateMapper.java
@Select("""
    SELECT * FROM t_voucher_template
    WHERE is_active = true
      AND source = #{source}
      AND business_type = #{businessType}
      AND (direction IS NULL OR direction = '' OR direction = #{direction})
    ORDER BY match_priority ASC
    LIMIT 1
""")
VoucherTemplateEntity matchByDimensions(@Param("source") String source,
                                         @Param("businessType") String businessType,
                                         @Param("direction") String direction);
```

---

## 5. Entity 变更

### 5.1 VoucherTemplateEntity

```java
// 新增字段
/** 来源: BANK_STMT / BUSINESS_DOC / INVOICE / PERIOD_CLOSE */
private String source;

/** 业务类型: RECEIPT / PAYMENT / EXPENSE / INVOICE_OUT / ... */
private String businessType;

/** 方向: in(收/入) / out(付/出) / 空(双向) */
private String direction;

/** 匹配优先级（越小越优先，默认 0） */
private Integer matchPriority;
```

### 5.2 VoucherTemplateLineEntity

```java
// 新增字段
/** 辅助核算类型: CUSTOMER / VENDOR / DEPT / EMPLOYEE / PROJECT / 空(无) */
private String assistType;

/** 是否必填辅助核算（强校验）*/
private Boolean assistRequired;
```

### 5.3 V48 迁移 SQL

```sql
-- V48__add_voucher_template_dimensions.sql

-- 1. t_voucher_template 新增维度字段
ALTER TABLE t_voucher_template
    ADD COLUMN IF NOT EXISTS source VARCHAR(30),
    ADD COLUMN IF NOT EXISTS business_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS direction VARCHAR(10),
    ADD COLUMN IF NOT EXISTS match_priority INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN t_voucher_template.source IS '来源: BANK_STMT/BUSINESS_DOC/INVOICE/PERIOD_CLOSE';
COMMENT ON COLUMN t_voucher_template.business_type IS '业务类型: RECEIPT/PAYMENT/EXPENSE/INVOICE_OUT/...';
COMMENT ON COLUMN t_voucher_template.direction IS '方向: in/out/空(双向)';
COMMENT ON COLUMN t_voucher_template.match_priority IS '匹配优先级, 越小越优先';

CREATE INDEX IF NOT EXISTS idx_vt_dimensions
    ON t_voucher_template(source, business_type, is_active, match_priority);

-- 2. t_voucher_template_line 新增辅助核算字段
ALTER TABLE t_voucher_template_line
    ADD COLUMN IF NOT EXISTS assist_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS assist_required BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN t_voucher_template_line.assist_type IS '辅助核算类型: CUSTOMER/VENDOR/DEPT/EMPLOYEE/PROJECT';
COMMENT ON COLUMN t_voucher_template_line.assist_required IS '是否必填辅助核算(强校验)';
```

---

## 6. AutoGenerationService 改造

### 6.1 现状

```java
// 当前: 先查模板 → 有模板用 generateVoucherFromTemplate → 无模板降级硬编码 switch
VoucherTemplateEntity template = voucherTemplateService.matchByClassification(stmt.getClassification());
if (template != null) { generateVoucherFromTemplate(template, lines, stmt, period, amount, userId); return; }
// 降级: switch(classification) { case "bank_fee": ... }
```

### 6.2 目标

```java
// 改为: 构建 TemplateContext → TemplateMatcher.match → TemplateEngine 渲染 → 制证
TemplateContext ctx = new TemplateContext()
    .setSource("BANK_STMT")
    .setClassification(stmt.getClassification())
    .setDirection(stmt.getDirection())
    .setAmount(amount)
    .setSummary(stmt.getSummary())
    .setCounterpartyName(stmt.getCounterAccount())
    .setPeriod(period)
    .setVariables(Map.of(
        "customerName", customerName,
        "vendorName", vendorName
    ));

VoucherTemplateEntity template = templateMatcher.match(ctx);
if (template != null) {
    generateVoucherFromTemplate(template, templateService.getLines(template.getId()), ctx, stmt, userId);
} else {
    // 降级: 硬编码（保留但标 deprecated）
}
```

### 6.3 `generateVoucherFromTemplate` 改造

```java
private void generateVoucherFromTemplate(VoucherTemplateEntity template,
                                          List<VoucherTemplateLineEntity> lines,
                                          TemplateContext ctx,
                                          BankStatementEntity stmt,
                                          Long userId) {
    VoucherEntity voucher = createVoucher(stmt, ctx.getPeriod(), ctx.getAmount(), userId);
    voucher.setTemplateId(template.getId());
    voucherMapper.updateById(voucher);

    for (VoucherTemplateLineEntity line : lines) {
        BigDecimal dr = TemplateEngine.renderAmount(line.getDrAmountTemplate(), ctx);
        BigDecimal cr = TemplateEngine.renderAmount(line.getCrAmountTemplate(), ctx);
        // 方向约束 + 跳过 0 金额行（同现有逻辑）
        String summary = TemplateEngine.renderSummary(line.getSummaryTemplate(), ctx);

        VoucherEntryEntity entry = new VoucherEntryEntity();
        entry.setVoucherId(voucher.getId());
        entry.setSubjectId(line.getSubjectId());
        entry.setDebit(dr); entry.setCredit(cr);
        entry.setSummary(summary);
        // 辅助核算: 按 assistType 设置 assistJson
        if (StrUtil.isNotBlank(line.getAssistType())) {
            entry.setAssistJson(buildAssistJson(line, ctx));
        }
        entry.setSortOrder(sort++);
        voucherEntryMapper.insert(entry);
    }
}

private String buildAssistJson(VoucherTemplateLineEntity line, TemplateContext ctx) {
    // 根据 line.assistType 取出对应的 ID，构建 {"customerId": 123} 格式
}
```

---

## 7. BusinessDocServiceImpl 改造

### 7.1 现状

```java
// BusinessDocServiceImpl 用硬编码 DOC_VOUCHER_SUBJECTS 映射
private static final Map<String, List<String[]>> DOC_VOUCHER_SUBJECTS = Map.ofEntries(
    Map.entry("RECEIPT",            pair("1002", "1122")),
    Map.entry("PAYMENT",            pair("2202", "1002")),
    // ...
);
```

### 7.2 目标

```java
// generateVoucher() 中改为：
TemplateContext ctx = new TemplateContext()
    .setSource("BUSINESS_DOC")
    .setBusinessType(entity.getDocType())
    .setAmount(entity.getAmount())
    .setPeriod(entity.getPeriod())
    .setVariables(Map.of(
        "customerName", getCustomerName(entity),
        "vendorName", getVendorName(entity),
        "docNo", entity.getDocNo()
    ));

VoucherTemplateEntity template = templateMatcher.match(ctx);
if (template != null) {
    // 用模板生成分录（多行，每行对应一条模板行）
    generateFromTemplate(template, lines, ctx, entity, userId);
} else {
    // 降级: 现有硬编码逻辑
}
```

---

## 8. TaxService.generateVoucherFromInvoice 改造

### 8.1 现状

```java
// 硬编码: 借 1122 / 贷 5001 + 2221.01
Subject subjectAr = findSubject("1122");
Subject subjectRevenue = findSubject("5001");
Subject subjectOutputTax = findSubject("2221.01");
```

### 8.2 目标

```java
TemplateContext ctx = new TemplateContext()
    .setSource("INVOICE")
    .setBusinessType("INVOICE_OUT")
    .setAmount(inv.getAmount())
    .setTaxAmount(inv.getTaxAmount())
    .setTotalAmount(inv.getTotalAmount())
    .setPeriod(inv.getPeriod())
    .setVariables(Map.of("customerName", inv.getCustomerName()));

VoucherTemplateEntity template = templateMatcher.match(ctx);
if (template != null) {
    generateFromTemplate(template, lines, ctx, inv, userId);
} else {
    throw BusinessException.badRequest("缺少销售发票凭证模板, 请先配置");
}
```

---

## 9. 辅助核算写入与强校验

### 9.1 辅助核算写入

在 `generateVoucherFromTemplate` 中，对于每一条模板行：

```java
if (StrUtil.isNotBlank(line.getAssistType())) {
    // 强校验: 如果 assistRequired=true 但对应 ID 为空，拦截
    if (Boolean.TRUE.equals(line.getAssistRequired())) {
        Long id = extractAssistId(line.getAssistType(), ctx);
        if (id == null) {
            throw new BusinessException("模板行缺少必填辅助核算: " + line.getAssistType());
        }
    }
    // 写入 assistJson
    entry.setAssistJson(buildAssistJson(line, ctx));
}
```

### 9.2 `extractAssistId` 映射

| assistType | 取数字段 | 备注 |
|:-----------|:---------|:-----|
| CUSTOMER | ctx.customerId | 客户辅助核算 |
| VENDOR | ctx.vendorId | 供应商辅助核算 |
| DEPT | ctx.deptId | 部门辅助核算 |
| EMPLOYEE | ctx.employeeId | 员工辅助核算 |
| PROJECT | ctx.projectId | 项目辅助核算 |

---

## 10. 前端模板编辑页变更

### 10.1 表头新增列

| 列 | 类型 | 说明 |
|:---|:-----|:------|
| 来源 | 下拉框 | BANK_STMT / BUSINESS_DOC / INVOICE / PERIOD_CLOSE |
| 业务类型 | 下拉框 | RECEIPT / PAYMENT / EXPENSE / INVOICE_OUT / ... |
| 方向 | 下拉框 | in / out / 双向 |
| 优先级 | 数字 | 匹配优先级 |

### 10.2 分录行新增列

| 列 | 类型 | 说明 |
|:---|:-----|:------|
| 辅助核算 | 下拉框 | 客户/供应商/部门/员工/项目/无 |
| 强校验 | 开关 | 是否必填辅助核算 |

---

## 11. 种子模板（V50 迁移）

> 以下模板在 V50 迁移中以 SQL 形式写入，分类覆盖 5 大类。

### 11.1 资金与出纳类（source=BANK_STMT）

| name | classification | 借方科目 | 贷方科目 |
|:-----|:---------------|:---------|:---------|
| 客户收款 | business_receipt | 1002 银行存款 | 1122 应收账款 |
| 支付供应商 | business_payment | 2202 应付账款 | 1002 银行存款 |
| 银行手续费 | bank_fee | 6602.01 财务费用-手续费 | 1002 银行存款 |
| 利息收入 | interest_income | 1002 银行存款 | 6602.02 财务费用-利息收入 |
| 缴纳税金 | tax_payment | 2221 应交税费 | 1002 银行存款 |
| 社保缴费 | social_security | 2211 应付职工薪酬-社保 | 1002 银行存款 |
| 保险费用 | insurance_fee | 6602.06 管理费用-保险费 | 1002 银行存款 |
| 内部调拨 | internal_transfer | 1002-目标户 | 1002-源户 |
| 工资发放 | salary_payment | 2211 应付职工薪酬-工资 | 1002 银行存款 |

### 11.2 往来与结算类（source=BUSINESS_DOC）

| name | businessType | 借方科目 | 贷方科目 |
|:-----|:-------------|:---------|:---------|
| 收款单 | RECEIPT | 1002 银行存款 | 1122 应收账款 |
| 付款单 | PAYMENT | 2202 应付账款 | 1002 银行存款 |
| 费用报销 | EXPENSE | 6602 管理费用 | 1002 银行存款 |
| 采购发票 | INVOICE_IN | 1403 库存商品 | 2202 应付账款 |
| 销售发票 | INVOICE_OUT | 1122 应收账款 | 6001 主营业务收入 |

### 11.3 期末结转类（source=PERIOD_CLOSE）

| name | businessType | 逻辑 |
|:-----|:-------------|:-----|
| 损益结转 | PROFIT_LOSS_CLOSE | 自动取科目余额反向结转 |
| 增值税结转 | VAT_CLOSE | 销项-进项-已交，正数转未交 |
| 汇兑损益 | FX_CLOSE | 期末汇率重估外币余额 |

---

## 12. 不做事项

- ❌ 不实现自定义报表公式引擎（仅支持四则运算）
- ❌ 不做模板版本管理（单版本覆盖更新）
- ❌ 不做模板审批流
- ❌ 不做模板导入导出

---

## 13. 测试要点

| 测试场景 | 方法 | 期望 |
|:---------|:-----|:------|
| `TemplateEngine.renderSummary` 基础变量 | `testRenderSummary_basicVars()` | 变量被正确替换 |
| `TemplateEngine.renderSummary` 无变量文本 | `testRenderSummary_noVars()` | 原样返回 |
| `TemplateEngine.renderAmount` 表达式 | `testRenderAmount_expression()` | 计算结果正确 |
| `AmountExpressionResolver.evaluate` 四则运算 | `testEvaluate_basicArith()` | `1+2*3`=7 |
| `TemplateMatcher.match` 精确匹配 | `testMatch_exact()` | 返回最匹配模板 |
| `TemplateMatcher.match` 降级匹配 | `testMatch_fallback()` | 按优先级降级 |
| `generateVoucherFromTemplate` 辅助核算写入 | `testAssistJson_written()` | assistJson 不为空 |
| 强校验拦截 | `testAssistRequired_blocked()` | 抛 BusinessException |
| V48 迁移回退 | `testMigration_rollback()` | 不破坏现有数据 |

---

## 14. API 变更

| 方法 | 路径 | 说明 | 批次 |
|:-----|:-----|:-----|:----:|
| GET | `/api/v1/voucher-templates?source=&businessType=` | 按维度查模板 | P0 |
| POST | `/api/v1/voucher-templates` | 创建模板（含新字段）| P0 |

> 现有 CRUD 端点不变，请求体扩展 source/businessType/direction 等字段即可。

---

## 15. 后续依赖

- **依赖 P21/P22**：凭证状态机 + 发票状态机已落地，本 SPEC 不涉及状态变更
- **依赖 V23/V40/V42**：已有模板表和种子数据需要 V48 迁移扩展字段
- **被 PeriodCloseService 依赖**：P2 期末结转需要本 SPEC 的 TemplateMatcher + TemplateEngine
