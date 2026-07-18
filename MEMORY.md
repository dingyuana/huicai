---
§
项目路径：`/root/data/huicai/`（老丁确认）。设计文档在 `docs/design/`，SPEC 在 `docs/specs/`。
§
当前分支：`feature/architecture-base-branch`（基于 main，最新 commit fce647d — 代码清理+修复）。最新 migration：V1__baseline.sql + V55.1/V84-V90。
§
技术栈：Spring Boot 3.x + MyBatis-Plus + Flyway / Vue 3 + Element Plus + TypeScript / PostgreSQL 16
§
开发流程：三步闭环（SPEC→Plan→审核→执行），Contract-First 微循环，每次提交前 mvn test 必须 0 fail。
§
测试基线：Mapper 62.7% (37/59), Controller 52% (26/50), Service 27文件, 前端6文件/48测试。基线889+9测试，2个预先存在的失败。
§
核心铁律：所有审核人工完成；AI只做辅助不替换Java模块；不引入新框架/微服务/K8s；修bug全量同类扫描；代码变更同步文档。
§
已知问题：Hindsight Memory 已配置但需充值才能使用（402 Insufficient Credits）。