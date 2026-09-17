---
标题: P66 费用报销前端打磨（REQ-2026-016 前端收口）
编号: P66
版本: v1.0 (2026-09-13)
关联PRD: PRD05（应收应付/费用报销域）
状态: 📝 草案待审核
关联SPEC: P11-employee-expense-reimbursement.md（后端闭环 ✅）、P54-code-restructure-base-sme-agency.md
test_ref: 待实施（ExpenseReimbursementServiceImplTest 扩展；前端以 build+手工验证清单）
预估工时: 8h（前端 6h + 后端微调 1h + 测试 1h）

## 背景

REQ-2026-016 状态"⚠️ 后端完整，前端基础"。后端 10 个端点齐全（CRUD/submit/approve/reject/auto-voucher/generate-voucher），VO 字段丰富（reimbNo/rejectReason/voucherId/approvedBy/attachmentIds/submittedAt/approvedAt），但前端仅 2 个薄页面（ExpenseList 156 行 / ExpenseEdit 97 行），存在以下缺口：

1. **驳回死端（状态机缺口）**：`updateDraft`/`submit` 仅允许 DRAFT，REJECTED 单据无法修改重报，业务闭环断裂
2. **编辑页费用类型缺 2 种**：下拉仅 4 种（TRAVEL/OFFICE/ENTERTAINMENT/OTHER），列表映射却有 6 种（缺 TRANSPORT/MEAL）——数据不一致缺陷
3. **信息展示缺失**：列表显示裸 id 而非 reimbNo；驳回原因/审批人/提交时间/凭证入口均未展示；无详情视图
4. **附件能力空转**：VO 有 attachmentIds 字段、attachment.ts 上传 API 齐备、MinIO 存储已上线，UI 无上传/查看入口
5. **筛选能力弱**：page 仅支持 employeeId+status，无类型/关键字/日期筛选
6. **审计缺口**：approve 不传 approver 参数，审批人未记录
7. **越权保存**：非 DRAFT 单据进入编辑页仍显示"保存草稿"，靠后端 400 兜底

## 输入契约

### 后端微调（2 处守卫扩展 + 1 处查询扩展）

1. `updateDraft`：守卫从 `仅 DRAFT` 放宽为 `DRAFT 或 REJECTED`；REJECTED 修改后状态保持 REJECTED（等待重新提交）
2. `submit`：守卫从 `仅 DRAFT` 扩展为 `DRAFT 或 REJECTED`；REJECTED → SUBMITTED 时清空 rejectReason
3. `pageQuery` 增加 3 个可选参数：`expenseType`（String）、`keyword`（匹配 reimbNo/summary）、`beginDate`/`endDate`（createdAt 范围）

### 前端页面（无新路由，沿用 /arap/expense 与 /arap/expense/edit）

- 列表/编辑沿用现有 `expense.ts` API 模块，类型定义补全 VO 全部字段
- 附件：`uploadFile(file, 'EXPENSE', reimbId)` + `listAttachments('EXPENSE', reimbId)` + `deleteAttachment`

## 输出契约

### 列表页（ExpenseList.vue）

- 编号列显示 reimbNo（替换裸 id）
- 筛选区新增：费用类型下拉、关键字输入、日期范围（对接扩展后的 page 参数）
- 新增列：提交时间（submittedAt）、驳回原因（rejectReason，tooltip 展示）
- 操作列 REJECTED 行新增"重新编辑"（跳编辑页）；VOUCHERED 行新增"查看凭证"（跳凭证详情）
- 行点击打开**只读详情抽屉**：全部 VO 字段 + 审批人/时间线（提交→审批）+ 附件列表（可下载）
- approve 调用传入当前登录人姓名（auth store）作为 approver

### 编辑页（ExpenseEdit.vue）

- 费用类型补全 6 种（新增 TRANSPORT 交通费 / MEAL 餐饮费）
- 附件上传区：DRAFT 与 REJECTED 状态可上传/删除，其他状态只读列表
- REJECTED 进入时顶部显示驳回原因警示横幅（el-alert）
- 非 DRAFT/REJECTED 状态进入 → 全表单禁用（只读模式），隐藏保存按钮

### 后端状态机（变更后）

```
DRAFT → SUBMITTED → APPROVED → VOUCHERED
         ↑ 下的 reject：
SUBMITTED → REJECTED →（updateDraft 修改后）submit → SUBMITTED（清 rejectReason）
```

## 状态流转

铁律 #4：REJECTED → SUBMITTED 为本次**显式新增**的合法转换，配正反双向测试（REJECTED 可提交；VOUCHERED/APPROVED 提交仍拒绝）。

## 异常处理

| 场景 | 异常 | 消息 |
|------|------|------|
| updateDraft 单据状态为 APPROVED/VOUCHERED/SUBMITTED | BusinessException | 仅 DRAFT 或 REJECTED 状态可修改 |
| submit 单据状态为 APPROVED/VOUCHERED | BusinessException | 仅 DRAFT 或 REJECTED 可提交: 当前=xxx |
| 附件上传非图片/PDF/常见办公格式 | BusinessException | 不支持的附件格式 |
| 编辑页保存失败（后端 400） | el-message 展示后端 msg | 透传 |
| 关键字/日期筛选非法 | 参数忽略（宽松处理） | — |

## BDD

### 场景 1: 驳回后修改重新提交（核心闭环）
- Given 存在 REJECTED 报销单（rejectReason="发票缺失"）
- When updateDraft 修改金额为 300，再 submit
- Then 状态 SUBMITTED，rejectReason 为空
- And 审计日志记录变更前后快照

### 场景 2: 非 DRAFT/REJECTED 拒绝修改
- Given VOUCHERED 报销单
- When updateDraft
- Then 抛 BusinessException("仅 DRAFT 或 REJECTED 状态可修改")

### 场景 3: 非 DRAFT/REJECTED 拒绝提交（负向）
- Given APPROVED 报销单
- When submit
- Then 抛 BusinessException("仅 DRAFT 或 REJECTED 可提交: 当前=APPROVED")

### 场景 4: 筛选扩展
- Given 存在 TRAVEL 类型、摘要含"差旅"的报销单 2 张
- When page(expenseType=TRAVEL, keyword=差旅)
- Then 仅返回这 2 张

### 场景 5: 审批人记录
- Given SUBMITTED 报销单，登录人为"张会计"
- When approve(id, approver=张会计)
- Then VO 的 approvedBy = 张会计，approvedAt 非空

### 场景 6: 附件上传与列表（前端手工验证项）
- Given DRAFT 报销单
- When 上传 PDF 附件并保存
- Then 详情抽屉附件列表可见且可下载

## 非目标

- 前端单测框架搭建（路线图另列"前端测试覆盖率提升"项）
- 固定资产测试补充（路线图另列）
- 借款/提前借款审批（老丁 2026-07-11 范围确认排除）
- 多级审批流（当前单级 approve 满足 SME 场景）

## 版本历史

- v1.0 (2026-09-13): 草案创建，待老丁审核
