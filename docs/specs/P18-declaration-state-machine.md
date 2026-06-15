# P18 SPEC — 申报状态机扩展 (SUBMITTED → APPROVED/REJECTED)

> 状态：补 2 方法中（P13 已实现 submitDeclaration, 缺 approve/reject）
> 目标：完整 5 态状态机 (DRAFT/SUBMITTED/APPROVED/REJECTED)
> 工期：1 批

---

## 1. 现状摸底 (2026-06-15)

| 文件 | 状态 |
|---|---|
| `t_tax_declaration` 实体 | ✅ (P3) |
| `TaxServiceImpl` 申报方法 | **2/4** (createDeclaration, submitDeclaration) |
| 缺 | `approveDeclaration / rejectDeclaration` |

**当前状态机**:
```
DRAFT ──→ submitDeclaration ──→ SUBMITTED ──→ (卡死, 缺审批)
```

**目标完整状态机**:
```
DRAFT ──→ submitDeclaration ──→ SUBMITTED
                                     ├─→ approveDeclaration ──→ APPROVED
                                     └─→ rejectDeclaration  ──→ REJECTED
```

---

## 2. P18-1 任务 (本批)

### 2.1 接口 + Impl

```java
// TaxService.java
TaxDeclarationEntity approveDeclaration(Long id, String approver);
TaxDeclarationEntity rejectDeclaration(Long id, String approver, String reason);
```

行为:
- 校验 status=SUBMITTED
- approveDeclaration → 状态=APPROVED, 记录 approver/approvedAt
- rejectDeclaration → 状态=REJECTED, 记录 approver/reason/rejectedAt
- 业务单据关联 (voucherId 联动, 当前用 placeholder)

### 2.2 Controller 端点

```java
POST /api/v1/tax/declarations/{id}/approve
POST /api/v1/tax/declarations/{id}/reject?reason=...
```

### 2.3 单测 (2 个)

| # | 测试 | 覆盖 |
|---|---|---|
| 1 | `approveDeclaration_SUBMITTED_变APPROVED_记录approver` | approveDeclaration |
| 2 | `rejectDeclaration_SUBMITTED_变REJECTED_理由必填` | rejectDeclaration |

---

## 3. 不在 P18 范围

- 申报撤销/作废
- 与电子税务局对接 (P14 候选)
- 自动生成申报凭证

---

## 4. 测试验收

**目标**: 305 → 307 (+2 测试)
