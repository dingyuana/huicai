# SPEC-P158：企业所得税小微三条件精确判定（SPEC-P156 遗留收口）

> 2026-11-09。用户裁定开工（数据源已探明），取数口径委托实现方裁定。

## 判定口径（用户裁定授权）

| 条件 | 数据源 | 取数形态 |
|------|--------|----------|
| 累计利润 ≤ 300 万 | 利润表 `cumulativeProfit`（既有） | 期末累计 |
| 从业人数 ≤ 300 人 | `t_employee`（deleted=0 AND is_active=true）selectCount | **期末值** |
| 资产总额 ≤ 5000 万 | 资产负债表 `balanceSheet.totalAssets` | **期末值** |

三条件 AND → 小型微利 5%（2.5%×2 政策延续口径）；任一超限 → 25% 标准档。

**口径定性**：从业/资产均为期末值近似，非税法官方「季度平均值」口径（系统未保留期末快照历史，
均值不可回算）。响应 note 尾缀显式注明口径。仍是「测算」非「申报定值」（八类红线不变）。

## 实现

- `TaxServiceImpl` 新注 `EmployeeMapper`；`calculateCit` 三条件逐条判定，超限原因收集进
  `exceeded` 列表，note **逐条点名**（多条件超限全列，对企业可诊断哪条挡路）。
- 响应新增 `smallMicro`（boolean）/ `headcount` / `totalAssets` 三字段（判定依据透出）。
- 无新表、无迁移、无权限面改动（既有 GET `/cit/estimate` 复用）。

## 测试与反证

- L1 `TaxServiceImplTest` 56→59（+3）：从业 301 超限 25%+note 点名 / 资产 5000 万+0.01 挡小微 /
  三条件齐（含恰 300 人、恰 5000 万边界仍计小微）；既有 citEstimate/citBoundary 补 `balanceSheet`/`selectCount` 双桩。
- 🔴 首跑一处测试自错：note 金额断言写成千分位「5,000,000」（实现是 `stripTrailingZeros().toPlainString()`
  无分隔符）——修测试非修实现。
- **反证成立**：临时短路从业/资产两条件（回纯利润近似）⇒ 恰超限 2 例红，还原即绿。

## 生产实机（三条件分支首次真库点亮）

- 旧数据全部累计利润 ≤0（202407 = -93、202611 = 0）恒走免预缴分支，三条件从未实机到达。
- ent2 造探针凭证 ZZ2026100001（借 1122 应收 50000 / 贷 6001 收入 50000，period 202610）：
  DRAFT → submit → **分权拦截「制单人不能自审」**（admin 制单）→ 换 qingdao_a（ent2 ADMIN）audit+post。
- `GET /cit/estimate?period=202610` 实测：**smallMicro=true / taxRate=0.05 / estimatedTax=2500 /
  headcount=1 / totalAssets=50000 / note 口径文案完整**。
- 清理定性：unpost 对称回滚快照（`updateBalanceOnUnpost` 实证——unpost 后 1122/6001 快照归 0，
  与 AUDITED 终态完全一致，零污染）；🔴 状态机无 AUDITED→DRAFT 回退端点（reject 仅收 SUBMITTED、
  delete 仅认 DRAFT）⇒ 凭证 168 按「演练数据留存预期语义」留存（对齐 ent2 演练家法）。

## 验证

L1 **1910**/0/0/5（+3）全绿；实机三探针值全对。

## 遗留

- 季度平均值口径：需期末快照历史（做期末快照表后可升级为官方口径精确判定）。
- AUDITED 无 DRAFT 回退端点：纠错语义走红冲（reverse），登记观察。
