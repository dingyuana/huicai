# P26 模板引擎 — 实施任务书

> 日期：2026-06-23 | 基于：docs/specs/P26-voucher-template-engine.md
> 工期：3 批（P0 核心引擎 2 天 → P1 业务接入 2 天 → P2 种子数据+结转 1 天）

---

## 总览

| 批次 | 内容 | commit 数 | 预估 |
|:-----|:-----|:---------:|:----:|
| **P0** | TemplateEngine + TemplateMatcher + Entity 扩展 + V48 + AutoGen改造 | 3 | 2 天 |
| **P1** | BusinessDoc/TaxService 接入 + 前端支持 + 辅助核算 | 3 | 2 天 |
| **P2** | 种子模板 V50 + 期末结转 + 清理硬编码 | 2 | 1 天 |

---

## 第一批 P0 — 核心引擎（2 天，3 commits）

### P0-1: TemplateEngine + TemplateMatcher + Entity 扩展

| 项 | 内容 |
|:---|:------|
| **改动文件** | 新建 `common/util/TemplateEngine.java` |
| | 新建 `common/util/AmountExpressionResolver.java` |
| | 新建 `common/util/TemplateContext.java` |
| | 新建 `finance/service/TemplateMatcher.java` |
| | 修改 `finance/entity/VoucherTemplateEntity.java`（+source/businessType/direction/matchPriority）|
| | 修改 `finance/entity/VoucherTemplateLineEntity.java`（+assistType/assistRequired）|
| | 修改 `finance/mapper/VoucherTemplateMapper.java`（+matchByDimensions 查询）|
| | 新建 `db/migration/V48__add_voucher_template_dimensions.sql` |
| **验证** | `mvn test` 通过 > `TemplateEngine.renderSummary()` 变量替换正确 |
| | `TemplateEngine.renderAmount()` 四则运算正确 |
| | `TemplateMatcher.match()` 按优先级返回正确模板 |
| | V48 迁移不会破坏现有数据 |

### P0-2: AutoGenerationService 改造

| 项 | 内容 |
|:---|:------|
| **改动文件** | 修改 `finance/service/impl/AutoGenerationService.java` |
| | 改动点：`autoGenerate()` 内构建 `TemplateContext` → 调 `TemplateMatcher.match()` → `generateVoucherFromTemplate()` 使用 `TemplateEngine` |
| | `resolveAmount()` / `resolveSummary()` 改为调用 `TemplateEngine` |
| | `generateVoucherFromTemplate()` 补充辅助核算 `assistJson` 写入 |
| **验证** | 银行流水 A 类制证走模板路径 |
| | 无模板匹配时降级硬编码 |
| | `assistJson` 不为空 |

### P0-3: 测试覆盖

| 项 | 内容 |
|:---|:------|
| **改动文件** | 新建 `test/.../TemplateEngineTest.java` |
| | 新建 `test/.../AmountExpressionResolverTest.java` |
| | 新建 `test/.../TemplateMatcherTest.java` |
| | 新建 `test/.../AutoGenerationServiceTemplateTest.java` |
| **验证** | ≥12 @Test 全部通过 |

---

## 第二批 P1 — 业务接入（2 天，3 commits）

### P1-1: BusinessDocServiceImpl 接入

| 项 | 内容 |
|:---|:------|
| **改动文件** | 修改 `finance/service/impl/BusinessDocServiceImpl.java` |
| | 改动点：`generateVoucher()` 中构建 `TemplateContext` → `TemplateMatcher.match()` → 模板制证 |
| | 无匹配时降级现有 `DOC_VOUCHER_SUBJECTS` 硬编码 |
| **验证** | 7 种单据类型（RECEIPT/PAYMENT/EXPENSE/INVOICE_IN/INVOICE_OUT/OTHER_RECEIVABLE/OTHER_PAYABLE）全部走模板 |
| | 降级路径不受影响 |

### P1-2: TaxService 接入

| 项 | 内容 |
|:---|:------|
| **改动文件** | 修改 `tax/service/impl/TaxServiceImpl.java` |
| | 改动点：`generateVoucherFromInvoice()` 中构建 `TemplateContext` → `TemplateMatcher.match()` → 模板制证 |
| **验证** | 销售发票点击"生成凭证"走模板路径 |
| | 科目取模板中配置的 1122/5001/2221.01 而非硬编码 |

### P1-3: 前端模板编辑页支持新字段

| 项 | 内容 |
|:---|:------|
| **改动文件** | 修改 `frontend/.../VoucherTemplateView.vue` |
| | 修改 `frontend/.../voucherTemplate.ts` |
| | 修改 `finance/dto/VoucherTemplateVO.java` |
| | 改动点：模板编辑弹窗新增 source/businessType/direction 下拉框 + matchPriority 数字输入 |
| | 分录行新增 assistType 下拉框 + assistRequired 开关 |
| **验证** | 新增模板时可配置维度字段 |
| | 已存模板编辑后字段不丢失 |

---

## 第三批 P2 — 种子数据 + 结转（1 天，2 commits）

### P2-1: V50 种子模板

| 项 | 内容 |
|:---|:------|
| **改动文件** | 新建 `db/migration/V50__seed_voucher_templates.sql` |
| | 写入 15+ 条模板数据（5 大类全覆盖）|
| **验证** | `SELECT COUNT(*) FROM t_voucher_template WHERE is_active=true` ≥ 15 |

### P2-2: 期末结转触发模板制证

| 项 | 内容 |
|:---|:------|
| **改动文件** | 修改 `finance/service/impl/PeriodCloseService.java` |
| | 改动点：损益结转步骤 → `TemplateMatcher.match(source=PERIOD_CLOSE, businessType=PROFIT_LOSS_CLOSE)` |
| | 增值税结转步骤 → `TemplateMatcher.match(source=PERIOD_CLOSE, businessType=VAT_CLOSE)` |
| **验证** | 期末结账触发损益结转制证 |
| | 增值税结转制证 |

### P2-3: 清理硬编码

| 项 | 内容 |
|:---|:------|
| **改动文件** | 删除 `AutoGenerationService` 中 switch-case 硬编码（确认所有分类都有模板后）|
| | 删除 `BusinessDocServiceImpl` 中 `DOC_VOUCHER_SUBJECTS`（确认后）|
| **验证** | 无分类无模板时抛明确错误，不静默失败 |

---

## 文件变更总表

| 文件 | P0 | P1 | P2 |
|:-----|:--:|:--:|:--:|
| `common/util/TemplateEngine.java` | ✅ 新建 | — | — |
| `common/util/AmountExpressionResolver.java` | ✅ 新建 | — | — |
| `common/util/TemplateContext.java` | ✅ 新建 | — | — |
| `finance/service/TemplateMatcher.java` | ✅ 新建 | — | — |
| `finance/entity/VoucherTemplateEntity.java` | ✅ 修改 | — | — |
| `finance/entity/VoucherTemplateLineEntity.java` | ✅ 修改 | — | — |
| `finance/mapper/VoucherTemplateMapper.java` | ✅ 修改 | — | — |
| `finance/service/impl/AutoGenerationService.java` | ✅ 修改 | — | — |
| `db/migration/V48__add_voucher_template_dimensions.sql` | ✅ 新建 | — | — |
| `finance/service/impl/BusinessDocServiceImpl.java` | — | ✅ 修改 | — |
| `tax/service/impl/TaxServiceImpl.java` | — | ✅ 修改 | — |
| `frontend/.../VoucherTemplateView.vue` | — | ✅ 修改 | — |
| `frontend/.../voucherTemplate.ts` | — | ✅ 修改 | — |
| `finance/dto/VoucherTemplateVO.java` | — | ✅ 修改 | — |
| `db/migration/V50__seed_voucher_templates.sql` | — | — | ✅ 新建 |
| `finance/service/impl/PeriodCloseService.java` | — | — | ✅ 修改 |
| 测试文件（4 个） | ✅ 新建 | — | — |

---

## 验证清单

### 每批完成后检查

- [ ] `mvn test` 全部通过
- [ ] `lsp_diagnostics` 无错误
- [ ] 无硬编码科目残留（除了降级路径）
- [ ] 模板 CRUD 页面正常工作

### 最终验收

- [ ] 银行流水 A 类制证走模板（bank_fee/interest_income/tax_payment/...）
- [ ] 业务单据 7 种类型制证走模板
- [ ] 发票生成凭证走模板
- [ ] 辅助核算正确写入 assistJson
- [ ] 强校验拦截缺失辅助核算
- [ ] 无分类无模板时抛错而非静默失败
- [ ] 期末损益/增值税结转自动制证

---

## 风险与缓解

| 风险 | 影响 | 缓解 |
|:-----|:-----|:------|
| TemplateEngine 四则运算用 ScriptEngine 有安全风险 | 代码注入 | 严格限制输入字符集 `[0-9+\-*/().\s]` |
| BusinessDocServiceImpl 7 种单据映射与种子模板不一致 | 科目配错 | P1 阶段先并行（模板优先+降级硬编码），确认后再删 |
| 已有数据中 `DOC_VOUCHER_SUBJECTS` 调用方依赖 | 回归 | 加单测覆盖 7 种单据的制证结果 |
| 前端模板编辑页字段增多 | UI 拥挤 | 使用 el-collapse 分组：基本信息/维度配置/分录行 |