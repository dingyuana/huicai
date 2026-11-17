# SPEC-P160/P161：撤审端点 + 参考库放行通道（V1.122 遗留收口）

> 2026-11-10。用户裁定「同意」，两项同批：①SPEC-P160 撤审端点（AUDITED→DRAFT，V1.121 登记遗留）；②SPEC-P161 参考库放行通道（`/voucher-templates/reference-list` 恒空 + `importFromReference` 静默失效同因收口，V1.103 遗留）。

## 一、SPEC-P160：撤审端点（AUDITED → DRAFT）

**设计裁定**：新权限码不建，复用 `voucher:audit`（零权限面扩张——撤审是审核动作的逆操作，能审的人才能撤）；不加 assertNotSelfReview（撤审回 DRAFT 后重提交仍需他人审核，分权由 audit 自有制审分离把关，与 unpost 家法同形）；不动余额快照（余额只随 post/unpost 动）；`assertPeriodOpen` 照 family 加。

**实现五件**：
- `VoucherStatus.isUnauditable`（仅 AUDITED）+ `VoucherStateMachineService.assertUnauditable`（「不可撤审, 需 AUDITED」）；
- `VoucherService.unaudit(Long,Long)` + `VoucherServiceImpl.unaudit`（getValidVoucher→assertUnauditable→assertPeriodOpen→batchUpdateStatus DRAFT 带乐观锁，仿 unpost L427）；
- `VoucherController` `POST /{id}/unaudit`（summary 注明仅纠错重审）；
- manifest 插条目 `POST /api/base/voucher/v1/vouchers/{id}/unaudit / voucher:audit / write:true`（棘轮 289→**290**，写 283→**284**，启动日志锚确认）。

**前端**：`voucher.ts` +`unauditVoucher`；列表页无行级操作列（只有批量工具栏），撤审按钮落 `VoucherDetail.vue`（AUDITED 行「记账」旁 +「撤审」，permission gate `voucher:audit`、confirm 文案「撤审后可修改重新提交」）。

**测试（L2 `VoucherMakerCheckerGuardTest` +2，💤7 例）**：撤审 AUDITED→DRAFT 成功 + 回环可重提交；非 AUDITED（SUBMITTED/DRAFT）拒绝且状态不变。
🔴 测试自错一处：fixture 初始形态即 SUBMITTED，首版按 DRAFT 断言白红——修测试非修实现。
**反证成立**：短路 `assertUnauditable` ⇒ 恰 `unauditRejectedForNonAudited` 红，还原即绿。

## 二、SPEC-P161：参考库放行通道

**根因（与 P153 A-2 同族）**：JdbcTemplate 手写 `WHERE enterprise_id=0` 只「绕过应用层过滤」，拦不住 DB 层 RLS 谓词——生产 `huicai_app` + GUC=租户时，谓词把 `enterprise_id=0` 叠成空集 ⇒ 参考库恒空、import 的 ref 扫描被滤成 0 行静默返回 0。**绕过 ≠ 放行。**

**修法定为「应用层上下文交换」**（P154 readiness probe 同款，零新迁移）：新增 `com.huicai.common.context.ReferenceContextSwap.withReferenceLibrary(dataSource, action)`——`EnterpriseContextHolder.set(0)` + `TenantGucSetter.apply`，finally 还原双向往返（GUC 去重按「最后一次 Applied(holder,ent)」比对，2→0→2 均真实重设）。GUC='0' 时标准 enterprise_policy 谓词天然命中 ent0 行，**不改 RLS 策略**。

**接入三处**：
- `VoucherTemplateServiceImpl.listReferenceTemplates`：查询包 swap（顺带补齐 templateCode/voucherTypeCode/summary/remark 四个此前漏读字段）；
- `VoucherTemplateController.listReferenceTemplates`：**整个方法体**（含 getLines/enrichLines 的 MyBatis 面——分录行 subject 挂 ent0 科目，租户上下文下必被应用层过滤杀成 null）包 swap；
- `importFromReference` 重写**两阶段**——Phase A（ctx=0）JdbcTemplate 读 ent0 模板+分录+科目码进内存；Phase B（还原 ctx）逐模板：按 (template_code,target) 查重 → 显式列 INSERT（`enterprise_id=target` 显式给值过 WITH CHECK，`entries` 列 jsonb 需 `?::jsonb`）→ 行级 subject remap（`SELECT id FROM t_subject WHERE code=? AND enterprise_id=target`，缺则降级 ref subject_id，原 COALESCE 语义保留）→ 幂等二次导入 = 0。tplRows=0 跳过分录（原行为）。

🔴 **两处实现缺陷被测试当场抓出（反证家法的价值）**：
1. 行级查重首版按 `(template_id, line_order)`——生产 ent0 实测 **19 组「重复」全是借贷配对行**（同一 line_order 挂借 1002/贷 6001 两行，非脏数据），单键查重把每对的贷方全杀掉（25→13）；
2. 改按完整内容查重（order+direction+subject+款额）时 subject 键必须用 **remap 后的 target id**——部分重导入场景 ref id 与库里 target id 永不相等，必重复。

**测试（L2 `VoucherReferenceLibraryRealDBTest` 2 例💤）**：
- 造运行时企业夹具（全新库只有迁移种子企业已自带模板，import 会全跳过——V183「运行时注册租户」同款）；
- reference-list：ctx=租户下 9 模板全可见 + 分录行非空 + subjectCode 富集；
- import：全数导入 + 台账行数相等 + remap 夹具科目命中 + 幂等 0。
🔴 **L2 诚实标注**：测试容器 test 用户是超级用户，RLS 永不参战（P154 家法）——本类只能照**应用层过滤**（ctx=租户时 app filter 对 MyBatis 面注入 enterprise_id=租户）与数据正确性两面；DB 层由生产实机验证（下节）。service 侧 swap 在 L2 无反证可证（超管绕 RLS 后撤 swap 也绿）——反证锚在 controller swap。
**反证成立**：撤 controller swap ⇒ 恰 `referenceListVisibleWithLinesAndSubjects` 红（分录行被 app filter 杀空），还原即绿。

## 三、验证与生产实机

- L1 **1914**/0/0/5 基线不变（新守卫全在 L2）；L2 两类 7/7 绿；前端 372 全绿 + vue-tsc 过。
- rebuild（mvn package → compose build → up）Flyway 零新迁移；启动锚「**共 290 条（写端点 284 条）enforcing**」。
- **实机全链 7 步**（qingdao_a/ent2，ADMIN 持 voucher:list+audit）：
  1. GET reference-list → **9 行**、25 分录行、**subjectCode/subjectName 25/25 全富集**（此前恒 null）；
  2. POST import-from-reference → **200 data=9**（此前静默 0）；
  3. DB ent2 模板 9 行 + 分录 25 行；remap **25/25 全挂 ent2** 科目（enterprise_id=2 GROUP BY 单一）；
  4. 二次 import → **data=0** 幂等；
  5. POST /{168}/unaudit → **200**、DB status=DRAFT、version 5→6；
  6. 再撤审 → **body code 400**「凭证当前状态 DRAFT 不可撤审, 需 AUDITED」（拒绝面）；
  7. submit→audit 还原链 **200**，终态 AUDITED（created_by=1≠audit 人 7，不触发制审分离）。
- 探针即清：ent2 导入 9 模板 + 25 分录 DELETE 清零；凭证 168 演练留存语义（AUDITED）复原。

## 四、遗留

- P161 service 侧 swap 的 DB 层反证只能靠生产实机（L2 超管语义）——本轮实机已照，后续返回测试若动 import 反证勿信 L2 绿。
- 参考库科目富集对前端**跨租户展示 ent0 科目名**——参考库本身是设计级共享数据（V131 由 ent1 复制、且 UI 语义即「标准模板」），非泄露；若未来需定制参考库细目另议。
- 撤审无批量端点（单条按钮即可，需求出现再立项）。
