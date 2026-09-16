# 前端查询条件区风格统一 — 设计规范

> **日期**：2026-09-16
> **作者**：Sisyphus
> **状态**：⏳ 待老丁审核
> **范围**：前端所有列表页（`src/views/` ~89 个 `.vue`）的上半部分（页头 + 统计区 + 分类标签 + 查询区 + 操作工具栏）
> **目标**：消除同一项目内查询条件区风格碎片，形成一份可复用的统一规范，并落地为共享组件

---

## 1. 背景与问题

### 1.1 现状

前端 `src/views/` 共 **89 个页面**（`ls views/*/*.vue views/*/*/*.vue`），其中约 **34 个列表页** 使用 `<el-form inline class="filter-form">` 作为查询条件区。无共享 FilterBar/PageHeader/Toolbar 组件，所有样式内联在各自 `<style scoped>` 中。

### 1.2 实证不一致点（抽查证据）

| # | 不一致项 | 表现 | 证据 |
|---|---|---|---|
| 1 | **页头字号** | OutputInvoice `18px`，其余全部 `16px` | `views/tax/output-invoice/OutputInvoiceList.vue:688` vs `views/finance/business-doc/BusinessDocList.vue:252` 等 |
| 2 | **filter-form 间距** | OutputInvoice/Prepayment `margin-bottom: 16px`，主流（BusinessDoc/BankStatement/BankJournal/PendingPool/BankAccount/Vendor 等）`12px` | grep 52 处 `filter-form` 命中 |
| 3 | **全局样式死代码** | `styles/index.scss` 定义了 `.search-form`（background/padding/margin 16px/20px）和 `.table-toolbar`，但**零页面引用**；另 agency 模块用 `.search-bar` | `styles/index.scss:16-21` + `views/agency/AccountantList.vue:6` |
| 4 | **无共享组件** | `src/components/` 仅有 `batch/`（BatchActionBar/BatchResultDialog），无 FilterBar/SearchForm/PageHeader/Toolbar | `ls src/components/` |
| 5 | **统计卡片各自私有** | 仅 OutputInvoice/ClearData/Voucher/Dashboard/AgencyDashboard 5 页有 stat-card，样式全部不同（OutputInvoice 有 gradient icon + hover 动画，其余未知） | grep `stat-card` |
| 6 | **分类标签位置不一** | OutputInvoice 独立 `<el-radio-group>` 在 stat 区下方；System 模块（Menu/Role/User）在 filter-form 内作为 `<el-form-item>` | `views/tax/output-invoice/OutputInvoiceList.vue:102` vs `views/system/user/UserList.vue:92` |
| 7 | **操作按钮容器不一** | BusinessDoc 用裸 `<div>`，OutputInvoice 用 `<el-space wrap>` | `BusinessDocList.vue:6-9` vs `OutputInvoiceList.vue:6-16` |
| 8 | **无全局设计令牌** | `styles/index.scss` 仅 29 行，无 CSS 变量；`main.ts` 仅 `import 'element-plus/dist/index.css'`，无主题定制；`vite.config.ts` 未注入全局 scss 变量 | `src/styles/index.scss` + `src/main.ts` |

### 1.3 影响

- 页面间视觉不一致，用户体验碎片化
- 每新增一页需重新决定字号/间距/容器，增加沟通成本
- 样式维护成本高（同一概念多套实现）

---

## 2. 设计目标

1. **统一查询条件区视觉**：字号、间距、容器、控件尺寸全项目一致
2. **消除死代码**：清理 `.search-form`、`.table-toolbar` 等未引用样式
3. **建立共享组件**：产出 `FilterBar` / `PageHeader` 共享组件，避免重复代码
4. **建立设计令牌**：在 `styles/index.scss` 中定义 CSS 变量（颜色/字号/间距）
5. **规范页面骨架**：定义列表页上半部分的固定层序

---

## 3. 统一设计令牌

在 `styles/index.scss` 中替换现有内容，定义全局 CSS 变量：

```scss
// ===== 设计令牌（Design Tokens）=====
:root {
  // 字号
  --font-title: 16px;          // 页面标题统一 16px（对齐主流 80% 页面）
  --font-weight-title: 600;
  --font-color-title: #303133;
  --font-size-base: 14px;

  // 间距
  --space-xs: 4px;
  --space-sm: 8px;
  --space-md: 12px;           // filter-form margin-bottom 统一 12px
  --space-lg: 16px;           // 页头 margin-bottom / pagination margin-top
  --space-xl: 20px;           // .page-container padding

  // 容器
  --card-shadow: none;        // 列表页统一 shadow="never"
  --card-radius: 4px;         // 搜索表单区域圆角

  // 颜色（沿用 Element Plus 默认语义色，不覆盖主题）
  --color-primary: #409eff;
  --color-text-primary: #303133;
  --color-text-secondary: #909399;
  --color-bg: #fff;
}
```

### 3.1 清理

- 删除 `styles/index.scss` 中的 `.search-form`、`.table-toolbar` 死代码（零引用）
- 保留 `.page-container`、`.el-table` 基础规则

---

## 4. 页面骨架规范

列表页上半部分固定为 **6 层**（自上而下）：

```
┌─────────────────────────────────────────────┐
│ ①  PageHeader（页头：标题 + 操作按钮）          │
├─────────────────────────────────────────────┤
│ ②  StatCards（可选，6 个统计数字卡片；无则跳过）    │
├─────────────────────────────────────────────┤
│ ③  ClassificationTabs（可选 el-radio-group 分类标签）│
├─────────────────────────────────────────────┤
│ ④  FilterBar（查询条件表单）                    │
├─────────────────────────────────────────────┤
│ ⑤  Table（el-table）                          │
├─────────────────────────────────────────────┤
│ ⑥  Pagination（分页，flex-end）                │
└─────────────────────────────────────────────┘
```

### 4.1 层序规则

- ② StatCards 和 ③ ClassificationTabs 为**可选层**：无统计数字则跳过 StatCards；无分类维度则跳过 ClassificationTabs
- ③ ClassificationTabs 始终在 ④ FilterBar **上方**（先选分类维度，再填具体条件）
- 当 ②③ 同时存在时：StatCards → ClassificationTabs → FilterBar
- ① PageHeader 始终在最顶

### 4.2 基准页对照

当前两页对照（目标规范 vs 现状）：

| 层 | BusinessDocList（现状） | OutputInvoiceList（现状） | **统一目标** |
|---|---|---|---|
| ① PageHeader title | 16px ✓ | 18px ✗ → 改 16px | 16px, font-weight 600, #303133 |
| ② StatCards | 无 | 6 个（gradient 动画） | 标准化 stat-card（见 §6） |
| ③ ClassificationTabs | filter 后 | stat 后、filter 前 | 统一在 FilterBar 上方 |
| ④ FilterBar margin | 12px ✓ | 16px ✗ → 改 12px | margin-bottom 12px |
| ⑤ Table | el-card shadow="never" | el-card shadow="never" | 保持 ✓ |
| ⑥ Pagination | flex-end, margin-top 16px | flex-end, margin-top 16px | 保持 ✓ |

---

## 5. 共享组件规范

### 5.1 新增 `src/components/page/PageHeader.vue`

```vue
<template>
  <div class="page-header">
    <span class="page-title"><slot name="title">{{ title }}</slot></span>
    <el-space wrap>
      <slot name="actions" />
    </el-space>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title?: string }>()
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-lg);
}
.page-title {
  font-size: var(--font-title);
  font-weight: var(--font-weight-title);
  color: var(--font-color-title);
}
</style>
```

### 5.2 新增 `src/components/page/FilterBar.vue`

```vue
<template>
  <el-form :model="model" inline class="filter-form">
    <slot />
  </el-form>
</template>

<script setup lang="ts">
defineProps<{ model: Record<string, any> }>()
</script>

<style scoped>
.filter-form {
  margin-bottom: var(--space-md);
}
</style>
```

**用法**：
```vue
<FilterBar :model="query">
  <el-form-item label="状态">
    <el-select v-model="query.status" ... />
  </el-form-item>
  <el-form-item>
    <el-button type="primary" @click="onSearch">查询</el-button>
    <el-button @click="onReset">重置</el-button>
  </el-form-item>
</FilterBar>
```

### 5.3 新增 `src/components/page/StatCard.vue`

```vue
<template>
  <el-col :span="span">
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-info">
          <span class="stat-label"><slot name="label" /></span>
          <span class="stat-value"><slot /></span>
        </div>
        <div class="stat-icon" :class="iconClass">
          <el-icon><slot name="icon" /></el-icon>
        </div>
      </div>
    </el-card>
  </el-col>
</template>

<script setup lang="ts">
defineProps<{ span?: number; iconClass?: string }>()
</script>

<style scoped>
/* 统一 stat-card 样式，详见 §6 */
</style>
```

### 5.4 组件存放结构

```
src/components/
├── batch/          （已有）
└── page/           （新增）
    ├── PageHeader.vue
    ├── FilterBar.vue
    └── StatCard.vue
```

---

## 6. 统计卡片区规范

仅当页面有多个汇总数字时显示。使用 `<el-row :gutter="16">` + `<StatCard>` 组件。

### 6.1 布局

- 固定 6 列网格（`el-col :span="4"`），不足 6 个时按实际 span 均分
- 使用 `:gutter="16"`，`margin-bottom` 由外层控制

### 6.2 StatCard 样式（统一）

```scss
.stat-card {
  margin-bottom: 0;
  border-radius: 8px;
  transition: all 0.3s ease;
  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1) !important;
  }
}
.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 500;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
}
.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-icon .el-icon { font-size: 24px; color: #fff; }
```

**注意**：移除 OutputInvoice 现有的 `animation: statCardIn` 入场动画（性能/复杂度不必要）。保留 hover 微动效。

### 6.3 图标色板（统一）

| 用途 | 渐变 | class |
|---|---|---|
| 总计/总数 | `#4facfe → #00f2fe` | `icon-total` |
| 金额（正向） | `#43e97b → #38f9d7` | `icon-blue` |
| 红字/负向 | `#fa709a → #fee140` | `icon-red` |
| 警告 | `#ff9a44 → #fc6076` | `icon-warn` |
| 异常/作废 | `#8e9eab → #5a6a7e` | `icon-void` |

---

## 7. 分类标签规范

使用 `<el-radio-group v-model="..." class="classification-tabs" @change="handler">`。

- 放置位置：FilterBar **上方**（③层）
- 间距：`margin-bottom: var(--space-md)`（12px）
- 控件样式：`el-radio-button` 内边距 `8px 14px`（参考 BankStatement 现有 `.classification-tabs`）
- 无分类维度时**整层省略**，不得留空 div

---

## 8. 操作工具栏规范

页头右侧操作按钮统一用 `<el-space wrap>` 包裹，**不使用裸 `<div>`**。

```vue
<PageHeader title="页面名">
  <template #actions>
    <el-button @click="...">刷新</el-button>
    <el-button type="primary" @click="...">新增</el-button>
    <!-- 批量操作组件等放这里 -->
  </template>
</PageHeader>
```

按钮顺序约定（左→右）：刷新 → 导入 → 新增 → 批量操作 → 导出

---

## 9. 迁移清单

按优先级排定需调整页面（按影响面）：

### P0 — 样式修复（一行级，无组件依赖）

| 页面 | 文件 | 修复内容 |
|---|---|---|
| OutputInvoiceList | `views/tax/output-invoice/OutputInvoiceList.vue` | `.page-title` 18px→16px；`.filter-form` 16px→12px |
| PrepaymentView | `views/finance/prepayment/PrepaymentView.vue` | `.filter-form` 16px→12px |

### P1 — 清理死代码 + 定义令牌

| 文件 | 操作 |
|---|---|
| `src/styles/index.scss` | 替换为 §3 令牌定义；删除 `.search-form`、`.table-toolbar` 死代码 |

### P2 — 引入共享组件（逐页改造）

| 页面 | 文件 | 改造内容 |
|---|---|---|
| BusinessDocList | `views/finance/business-doc/BusinessDocList.vue` | 引入 `PageHeader`、`FilterBar`；按钮容器 `<div>`→`<el-space>` |
| BankStatementView | `views/finance/bank-statement/BankStatementView.vue` | 引入 `PageHeader`、`FilterBar` |
| VoucherList | `views/finance/voucher/VoucherList.vue` | 引入 `PageHeader`、`FilterBar`、`StatCard`（有 stat 区） |
| 待办其余列表页 | `views/finance/`, `views/arap/`, `views/tax/` 等 | 引入 `PageHeader`、`FilterBar` |

### P3 — 标准化统计卡片

| 页面 | 文件 | 操作 |
|---|---|---|
| OutputInvoiceList | `views/tax/output-invoice/OutputInvoiceList.vue` | 6 个 stat-card 改为 `<StatCard>` 组件；移除自定义 keyframe 动画 |
| VoucherList | `views/finance/voucher/VoucherList.vue` | stat-card 改为 `<StatCard>` |
| ClearDataView / AgencyDashboard | ... | 同上 |

### P4 — 分类标签位置统一

| 页面 | 文件 | 操作 |
|---|---|---|
| System 模块（Menu/Role/User） | `views/system/.../*.vue` | 将内联在 `<el-form-item>` 中的 el-radio-group 抽出为独立 ③层 |

---

## 10. 验收标准

1. **视觉一致性**：所有列表页页头字号均为 16px；filter-form margin-bottom 均为 12px
2. **无死代码**：`grep -rn 'search-form\|table-toolbar' views/` 零命中（排除注释）
3. **共享组件可复用**：新增列表页使用 `PageHeader`/`FilterBar`/`StatCard`，无内联重复
4. **设计令牌生效**：`styles/index.scss` 含 `:root` CSS 变量，所有页面样式引用 `var(--...)`
5. **全量测试通过**：`npx vitest run` + `npx vue-tsc --noEmit` 零错误
6. **构建无回归**：`npm run build` 成功

---

## 11. 非目标范围

- Element Plus 主题色定制（不覆盖默认主题）
- 表格列定义、分页逻辑、API 调用层改造
- 响应式断点适配（本次仅统一桌面端上半部分）
- 暗黑模式

---

## 附录 A：参考基准页

- **正面基准**：`views/tax/output-invoice/OutputInvoiceList.vue`（层序完整、统计卡片丰富，但字号/间距需修正）
- **简洁基准**：`views/finance/business-doc/BusinessDocList.vue`（紧凑、层级清晰，但缺统计区、按钮容器需改进）
- **本次改造后目标**：两者的优点合并，缺点消除
