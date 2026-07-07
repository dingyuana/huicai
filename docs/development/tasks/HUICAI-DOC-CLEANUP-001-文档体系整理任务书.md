# 文档体系整理任务书

> **编号**：HUICAI-DOC-CLEANUP-001
> **版本**：V1.0 | **创建日期**：2026-07-07 | **创建人**：Hermes
> **状态**：待审核 | **优先级**：P0
> **关联文档**：DOCUMENT_REGISTRY.md, DESIGN.md, docs/design/*.md

---

## 一、任务背景

前期开发中产生了大量遗留文档散落在 docs/ 各层级。7/7 已完成初步分类归档（创建了 6 大分类目录、移入 archive/、建立编号体系），但仍有以下未完成工作：

---

## 二、当前完成状态

| 工作项 | 状态 |
|--------|------|
| 目录分类（design/architecture/specs/development/testing/reference/archive） | ✅ 已完成 |
| 旧目录移入 archive/ | ✅ 已完成 |
| DESIGN.md + 11 设计文档加编号头部 | ✅ 已完成 |
| DOCUMENT_REGISTRY.md 创建 | ✅ 已完成 |
| 路径同步（/root/data/huicai/ 与 /data/disk/huicai/） | ⚠️ 需验证 |

---

## 三、剩余待办

### 3.1 路径一致性验证（0.5h）

- [ ] 确认 `/root/data/huicai/docs/`（真实项目根）和 `/data/disk/huicai/docs/`（MCP 路径）是否同一文件系统
- [ ] 如不同，将 design/、development/、testing/、reference/、archive/ 同步到 `/root/data/huicai/docs/`
- [ ] 确认 DESIGN.md 和 DOCUMENT_REGISTRY.md 在两处一致

### 3.2 所有文档加编号头部（2h）

已完成：DESIGN.md + design/*.md（12 份）
未完成：

| 目录 | 文件数 | 需操作 |
|------|--------|--------|
| architecture/*.md | 5 | 加 HUICAI-ARC-001~005 编号头部 |
| development/plans/*.md | 4 | 加 HUICAI-DEV-001~004 编号头部 |
| development/workflows/*.md | 2 | 加 HUICAI-DEV-028~029 编号头部 |
| development/standards/*.md | 1 | 加 HUICAI-DEV-030 编号头部 |
| development/guides/*.md | 1 | 加 HUICAI-DEV-031 编号头部 |
| development/lessons/*.md | 1 | 加 HUICAI-DEV-032 编号头部 |
| testing/*.md + testing/**/*.md | 6 | 加 HUICAI-TST-001~006 编号头部 |
| reference/*.md | 4 | 加 HUICAI-REF-001~004 编号头部 |
| development/tasks/*.md（23份）+ requirements/*.md（7份） | 30 | **保留现状，注册表登记即可**（历史任务书不逐一加头部） |

**小计**：约 24 份文件需逐个加编号头部。

### 3.3 归档目录清理（0.5h）

- [ ] 检查 archive/ 下是否有冗余（新旧重复文件）
- [ ] 旧目录是否完全移入 archive/，无残留根目录散落文件
- [ ] 确认 backup/ 在 archive/开发计划/ 下的位置是否合理

### 3.4 规范固化（1h）

- [ ] 在 DEVELOPMENT_STANDARD.md（或现有 docs/development/standards/）中新增「文档管理规范」：
  - 新增文档必须分配 HUICAI 编号
  - 修改文档必须更新版本号 + 修改日期 + 修改内容
  - 必须在 DOCUMENT_REGISTRY.md 登记
- [ ] 将该规范同步到 Hermes 技能（添加检查机制）

### 3.5 后续维护规则

| 场景 | 操作 |
|------|------|
| 新增 SPEC | 沿用 P 系列编号，在 DOCUMENT_REGISTRY.md §四 追加 |
| 新增设计文档 | 分配 HUICAI-DES-NEXT，加头部，注册表登记 |
| 修改设计文档 | 更新版本号 + 修改日期 + 修改内容 |
| 废弃文档 | 移入 archive/，注册表标记「已归档」 |

---

## 四、实施顺序

```
第1步：路径一致性验证（确认操作目录正确）
第2步：24 份文档逐个加编号头部
第3步：归档目录清理
第4步：规范固化 + 技能同步
```

## 五、工作量估算

| 阶段 | 工时 | 说明 |
|------|------|------|
| 路径验证 | 0.5h | 确认文件系统 |
| 加编号头部 | 2h | 24 份文件，每个约 5 分钟 |
| 归档清理 | 0.5h | 检查冗余 |
| 规范固化 | 1h | 写规范文档 + 更新技能 |
| **合计** | **4h** | |

---

> **文档结束。审核通过后开始执行。**