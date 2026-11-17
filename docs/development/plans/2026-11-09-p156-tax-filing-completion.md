# SPEC-P156：P1-1 税务申报收尾（REQ-2026-162）

> 2026-11-09。批次来源：P1 路线图收尾专项。勘察结论——P1-2 费用报销已全套落地（P11-4，roadmap「现状：无报销流程」过时已勘误）；P1-1 核心链已在（VAT 计算+附加税+附表一/二+税负率分析 CURR/YOY+申报 CRUD），本批次补齐三项缺口。

## 裁定依据（roadmap 预置决策项，无需新裁定）

| 决策项 | 采纳 |
|--------|------|
| D-1 首期仅申报表导出，不强制对接 API | ✅ 本批次实现导出 |
| D-2 按现行税率自动计算 | ✅ 复用既有 calculateVat（13/9/6/0 由发票税率聚合） |
| D-3 税负率预警阈值可配置（默认 3%） | ✅ 本批次实现 |
| D-4 电子税务局直连 | 后置（需申请接口权限，登记遗留） |

## 实施（V1.119 / V200）

### ① 增值税申报表导出（D-1）
- `GET /api/sme/tax/v1/tax/vat/export?period=YYYYMM`（GET 免 manifest 登记）
- 单 xlsx 三段：汇总（销项/进项/应纳/附加税 12%/合计 + 留抵提示）+ 附表一（按客户+税率，含合计行）+ 附表二（按供应商+税率，含合计与可抵扣税额）
- hutool ExcelWriter，与 salary 导出同族写法

### ② 企业所得税预缴测算（D-1 补充）
- `GET /api/sme/tax/v1/tax/cit/estimate?period=YYYYMM`
- 取利润表**累计利润总额**（复用 ReportService.incomeStatement，不建平行体系）；≤0 免预缴；>0 按档测算：≤300 万 → 小型微利 5%，否则 25%
- 🔴 **八类红线「税务规则」**：纯「测算」不落库不生成凭证；小微档位按利润额近似判定（从业≤300 人/资产≤5000 万条件由企业自查，note 字段提示）——**登记遗留**，精确档位判定待用户裁定细化

### ③ 税负预警阈值（D-3）
- V200：`t_tax_burden_config`（enterprise_id + tax_type 唯一部分索引 + threshold_rate NUMERIC(8,6)）+ RLS 三件套同迁移（V1.108 P0 家法）+ DO 双不变量自证
- `GET /burden-config`（未配置返回系统默认 3% 虚拟 VO，不落库）/ `POST /burden-config`（upsert，manifest 登记 permission=tax:output:list 与申报 CRUD 同码，零权限面扩张）
- 校验：阈值必须 0~100%
- 前端 TaxVatView 税负率分析 tab：阈值表单（比例% + 启用开关）+ 预警 tag（税负率 < 阈值 → 红色警示）

## 验证

- L1 **1903**/0/0/5（+4：默认值/配置回读、upsert+非法值守卫、CIT 三档、300 万档位边界）
- L2 `TaxBurdenConfigRealDBTest` 1/1（真库全链：默认 3% → save 落库 → 回读 → upsert 不新增行）
- **反证双成立**：DEFAULT_BURDEN_THRESHOLD 注入 0.99 ⇒ 恰默认值例红；档位 `<=` 改 `<` ⇒ 恰边界例红
- OutParamEntityStructureTest 零容忍守卫**当场抓出** Entity 直出×2 ⇒ 补 TaxBurdenConfigVO 修复（守卫生效实录）
- 前端 372 全绿 + vue-tsc 过
- 生产实机（rebuild 后）：阈值默认 3% → 保存 2.5%（DB 落行 enterprise_id=2 租户归属正确）→ 回读 → CIT 测算 200 → 申报表导出 xlsx 魔数 200；探针行用后即清
- V200 生产迁移 success；表 RLS true/true

## 遗留

- 企业所得税小微档位精确判定（从业人数/资产总额两条件未接入，按利润额近似）
- 电子税务局 API 直连（D-4，需申请接口权限）
- 申报表导出为「测算汇总」形态，与税务局官方申报表样式逐格对齐待需求确认
