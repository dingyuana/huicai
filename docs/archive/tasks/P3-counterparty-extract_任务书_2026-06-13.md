# P3 任务书：对手方名正则识别 — 移植 Go 版 `extractCounterpartyName`

> 日期：2026-06-13 | 任务 ID：P3-COUNTERPARTY-EXTRACT
> 上游：Go 版 `internal/service/bank_transaction_service.go:645-669`（已读）
> 老丁原话："（隐含）用户调整 + 列名智能映射 + 对手方名识别"

## 目标

移植 Go 版 4 级正则对手方名识别算法到 Java 慧财版，让 B 类业务单据（business_receipt / business_payment）能自动填 `customer_id` / `supplier_id`。

## Go 版 4 级正则（已读，645-669 行）

```go
// 1. 税务局
counterpartyTaxBureauRe = `(?:国家税务总局\p{Han}{0,15}税务局|\p{Han}{2,20}税务局)`
// 2. 政府部门
counterpartyGovRe = `\p{Han}{2,20}(?:社保局|公积金中心|社保中心|海关)`
// 3. 有限公司类
counterpartyCompanyRe = `\p{Han}{2,30}(?:有限公司|股份有限公司|集团|有限责任公司|股份公司|总公司|分公司|子公司|集团公司)`
// 4. 短组织名
counterpartyShortOrgRe = `\p{Han}{4,20}(?:公司|厂|店|商行|银行|事务所|医院|学校|中心)`

func extractCounterpartyName(description string) string {
    // 优先匹配最长
    if m := counterpartyTaxBureauRe.FindString(desc); m != "" { return m }
    if m := counterpartyGovRe.FindString(desc); m != "" { return m }
    if m := counterpartyCompanyRe.FindString(desc); m != "" { return m }
    if m := counterpartyShortOrgRe.FindString(desc); m != "" { return m }
    return ""
}

// 避免"10086"被当对手方
func looksLikeBankCode(s string) bool {
    return bankCodeOnlyRe.MatchString(s)  // `^\d{4,20}$`
}
```

## 实施步骤

### Step 1：新增 `CounterpartyExtractor` 工具类

**位置**：`com.huicai.module.finance.util.CounterpartyExtractor.java`

```java
public class CounterpartyExtractor {

    // 4 级正则（用 Java Pattern）
    private static final Pattern TAX_BUREAU = Pattern.compile("(?:国家税务总局[\\u4e00-\\u9fa5]{0,15}税务局|[\\u4e00-\\u9fa5]{2,20}税务局)");
    private static final Pattern GOV_DEPT   = Pattern.compile("[\\u4e00-\\u9fa5]{2,20}(?:社保局|公积金中心|社保中心|海关)");
    private static final Pattern COMPANY    = Pattern.compile("[\\u4e00-\\u9fa5]{2,30}(?:有限公司|股份有限公司|集团|有限责任公司|股份公司|总公司|分公司|子公司|集团公司)");
    private static final Pattern SHORT_ORG  = Pattern.compile("[\\u4e00-\\u9fa5]{4,20}(?:公司|厂|店|商行|银行|事务所|医院|学校|中心)");
    private static final Pattern BANK_CODE  = Pattern.compile("^\\d{4,20}$");

    public static String extract(String description) {
        if (description == null || description.isBlank()) return "";
        if (BANK_CODE.matcher(description.trim()).matches()) return "";
        String m = match(TAX_BUREAU, description); if (!m.isEmpty()) return m;
        m = match(GOV_DEPT, description);  if (!m.isEmpty()) return m;
        m = match(COMPANY, description);    if (!m.isEmpty()) return m;
        m = match(SHORT_ORG, description);  if (!m.isEmpty()) return m;
        return "";
    }

    private static String match(Pattern p, String s) {
        var m = p.matcher(s);
        return m.find() ? m.group().trim() : "";
    }
}
```

### Step 2：替换 `AutoGenerationService.guessPartyId` 占位符

**位置**：`AutoGenerationService.java:311-315`

```java
// ❌ 当前（永远返回 null）
private Long guessPartyId(String counterAccount, boolean isSupplier) {
    if (StrUtil.isBlank(counterAccount)) return null;
    return null;
}

// ✅ 改为：先正则提取，再查 party 表
private Long guessPartyId(String counterAccount, boolean isSupplier) {
    if (StrUtil.isBlank(counterAccount)) return null;
    String name = CounterpartyExtractor.extract(counterAccount);
    if (name.isEmpty()) return null;
    // TODO: 按 name 查 t_customer / t_vendor 表返回 ID（M3 任务，本期 stub）
    log.debug("正则提取对手方名={} (待 P3 后续查表)", name);
    return null;
}
```

**注**：P3 后续任务补 t_customer / t_vendor 表查表逻辑。本期只做正则提取 + 日志。

### Step 3：单测

新增 `CounterpartyExtractorTest.java`：

| Test | 输入 | 期望输出 |
|---|---|---|
| `testExtract_税务局` | "向国家税务总局山东税务局缴税" | "国家税务总局山东税务局" |
| `testExtract_社保局` | "支付济南市社保局5月社保" | "济南市社保局" |
| `testExtract_有限公司` | "收到山东恺拓蔚兰医疗科技有限公司货款" | "山东恺拓蔚兰医疗科技有限公司" |
| `testExtract_股份公司` | "中国建筑股份有限公司付款" | "中国建筑股份有限公司" |
| `testExtract_短公司` | "向万达公司付款" | "万达公司" |
| `testExtract_银行` | "工商银行手续费" | "工商银行" |
| `testRejects_BankCode` | "10086" | "" |
| `testEmpty_NullInput` | null | "" |
| `testPriority_税务局_优先于公司` | "国家税务总局北京市税务局" | "国家税务总局北京市税务局" |
| `testPriority_长匹配优先` | "中国石油化工集团有限公司" | "中国石油化工集团有限公司" |

## 验收标准

1. 10 单测全绿
2. `CounterpartyExtractor.extract("10086") == ""`（防止假对手方）
3. `AutoGenerationService.guessPartyId` 调新方法不抛错

## 不做的事

- ❌ 不实现 t_customer / t_vendor 表查表（stub）
- ❌ 不改 Go 版代码
- ❌ 不做模糊匹配 / 同义词

## 风险

| 风险 | 应对 |
|---|---|
| 正则贪婪匹配出错 | 10 单测覆盖（含 2 priority 测试） |
| Unicode 字符 `\p{Han}` Java 写法 | 用 `[\\u4e00-\\u9fa5]` 替代 |
| 公司名带数字（如"中石化3号加油站"） | 短组织 regex 兼容 4-20 字符含数字 |
