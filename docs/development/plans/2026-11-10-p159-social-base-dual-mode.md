# SPEC-P159：单位社保缴费基数双模式（V1.118 遗留收口）

> 2026-11-10。用户批准两项：①V202 ADMIN 补授 voucher:update（权限面）；②缴费基数双模式。

## 一、基数口径裁定（用户委托实现方设计）

现状缺陷：基数恒 = 当期应发合计 clamp 上下限，与社保局**按人逐年核定**的合规申报基数可能不符。

| 模式 | 代码 | 基数来源 | 语义 |
|------|------|----------|------|
| 当期应发（默认） | `GROSS` | 该员工当期 EARN 合计（历史口径） | 存量行为零漂移 |
| 社保核定基数 | `EMP_DECL` | `t_employee.social_base_declared`（社保局年度核定值） | 员工未核定 fail-loud |

- 上下限 clamp [baseFloor, baseCap] 对两种口径**通用**（EMP_DECL 基数同样过限）。
- 基数口径按**项目级**配置（一险种一模式），同单可混合（养老按核定、医疗按应发）。
- EMP_DECL 员工未核定 → **fail-loud 400 点名员工**（静默回落应发=错值，宁可拒绝）。

## 二、实现

- **V203**：`t_social_insurance_rate` +`base_mode VARCHAR(16) NOT NULL DEFAULT 'GROSS'` + chk
  (GROSS|EMP_DECL)；`t_employee` +`social_base_declared NUMERIC(12,2)`（NULL=未核定）；DO 自证
  三不变量（列在位 ×2 + 存量行恒 GROSS 零漂移）。
- `recomputeCompanySocial`：EMP_DECL 项目涉员工取核定值，未核定 400 点名；分叉按项目 rate.baseMode。
- `SocialInsuranceRateController`：RateVO/RateSaveDTO +baseMode（@Pattern GROSS|EMP_DECL，
  save 归一 null→GROSS）。
- 🔴 **实机抓出读-改-写静默丢字段**：`EmployeeServiceImpl#update` 逐字段搬运，新字段只在
  DTO/toEntity 侧补、update 侧漏 ⇒ PUT 200 但落库空（§4.4 第 11 条形态）；修=补一行搬运；
  L1 守卫 +1（反证：撤搬运恰该例红，首版断言 NPE 改 expect 等值后干净 Failure）。
- `MasterDataVoContractTest` 当场抓 EmployeeVO 前端契约缺失 ⇒ `employee.ts` 补类型（守卫生效实录）。

## 三、前端

- SalaryList 社保配置：比例表加「基数口径」列；表单加口径下拉（当期应发/社保核定基数）；
  hint 说明双口径与未核定拒绝语义。
- EmployeeList：编辑框加「社保核定基数」数字输入 + openEdit 回填。
- `salary.ts`：SocialRate/saveSocialRate +baseMode。

## 四、测试与反证

- L1 `SalaryBillServiceTest` +3：EMP_DECL 基数 8000×16%=1280（非应发 1600）/ 未核定 fail-loud
  且 bill/item 零写入 / 混合双项目同单（GROSS 1600 + EMP_DECL 核定 2000 clamp 下限 3000×8%=240）。
- 🔴 测试自错两处：误删 `itemRowForVo` helper（编译错，恢复）；混合例 selectCount 桩 2<3 个
  不同 itemId 被 validateItems 拒（改 3）。
- **反证三成立**：EMP_DECL 分支误回落 GROSS ⇒ 恰 2 例红；撤 fail-loud ⇒ 恰该例红；
  EmployeeServiceImpl 撤搬运 ⇒ 恰该例红。
- L1 **1914**/0/0/5（+4）；前端 372 全绿 + vue-tsc 过。

## 五、生产实机（rebuild 后）

- V202：admin PUT 凭证 → 400「分录不能为空」（业务层，此前 403）；DB 持有面 ADMIN/ACCOUNTANT/
  FINANCE_MGR 三角色。
- V203：员工 2 核定 8000 落库；项目 5 切 EMP_DECL → save 202612 → **item5=1280.00 + item8=1600.00
  同单混合点亮**；未核定员工 save → 400 点名；探针即清（rate 回 GROSS、员工软删、bill 3 DRAFT 演练留存）。
- Flyway V202/V203 success，DO 自证双 NOTICE。

## 六、遗留

- 基数细则：奖金/补贴是否计入基数按地区差异大——EMP_DECL 人工核定已是逃生门，GROSS 细项
  拆分口径登记待需求。
- `t_employee` emp_code 软删行仍占唯一索引（重建同 code 报引用错误形态）——登记观察。
