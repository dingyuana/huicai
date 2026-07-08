# 004 - API契约断层：架构迁移后前端调用的接口后端不存在

## 问题描述

2026-07-07 上线后，用户反馈数据维护页面 (`/system/clear-data`) 加载异常，6个统计接口中有2个返回500错误：
- `GET /api/v1/receivables/page` → 500 (Internal Server Error)
- `GET /api/v1/payables/page` → 500 (Internal Server Error)

## 根因分析

### 直接原因
P34架构变更（应收应付合并到业务单据）删除了 `ReceivableController` 和 `PayableController`，但前端 `ClearDataView.vue` 仍在调用旧的 `/receivables/page` 和 `/payables/page` 接口，后端没有对应的映射处理。

### 根本原因（测试盲区）

| 测试层级 | 盲区描述 | 具体表现 |
|---------|---------|---------|
| **L1 - 接口存在性校验** | 无前端API路径与后端Controller映射的自动化校验 | 前端调用的6个接口中，2个在后端不存在，无测试告警 |
| **L2 - Controller测试** | 删除旧Controller测试后未补新测试 | `ReceivableControllerTest`/`PayableControllerTest` 被删除，`ArapController` 无对应测试 |
| **L3 - 部署冒烟测试** | Docker容器部署后无自动健康检查 | 宿主机测试通过，但Docker容器运行旧镜像，无验证机制 |
| **L4 - E2E流程测试** | 数据维护页面无端到端测试 | `ClearDataView.vue` 未纳入E2E测试范围 |

### 问题链条

```
P34架构变更 → 删除ReceivableController/PayableController
    ↓
前端ClearDataView.vue仍调用旧接口
    ↓
测试体系无API契约校验 → 未发现接口缺失
    ↓
Docker容器运行旧镜像 → 线上500错误
    ↓
用户反馈 → 人工排查修复
```

## 影响范围

| 维度 | 影响 |
|------|------|
| **用户体验** | 数据维护页面统计数据加载失败，影响业务操作 |
| **修复成本** | 紧急排查+重建容器，耗时约30分钟 |
| **测试可信度** | 测试通过 ≠ 功能可用，暴露测试假阳性问题 |

## 修复措施

### 短期修复（已完成）
1. 创建 `ArapController`，实现 `/receivables/page`、`/payables/page` 等接口
2. 重新编译打包后端代码
3. 重建Docker容器并验证

### 长期预防（待实施）
1. **增加API契约校验层**：遍历前端 `api/modules/*.ts` 提取所有HTTP请求路径，自动验证后端Controller映射存在性
2. **强制测试补全**：删除Controller时必须同步补写等价新测试
3. **部署冒烟测试**：容器启动后自动调用关键接口验证服务就绪
4. **前端API文档化**：为每个API模块生成接口清单，与后端Swagger对齐

## 预防机制

### 规则1：API契约双向追踪
- 前端API定义必须与后端Controller路径、方法完全对齐
- 删除/重命名Controller时，必须同步更新前端API调用

### 规则2：自动化校验脚本
- 编写脚本扫描前端所有API定义文件
- 提取路径和HTTP方法
- 验证后端是否存在对应映射

### 规则3：部署验证闭环
- Docker容器启动后执行冒烟测试
- 关键接口全部返回200后才标记服务就绪
- 测试失败时自动回滚或告警

## 关联文档

- [测试方法论](../standards/TESTING_STANDARD.md)
- [E2E测试模板](../testing/templates/E2EFlowTestTemplate.java)
- [AGENTS.md陷阱与经验库](../../AGENTS.md)

---

**记录日期**：2026-07-07  
**关联需求**：P34 - 应收应付架构重构  
**问题分类**：测试假阳性 / API契约断层
