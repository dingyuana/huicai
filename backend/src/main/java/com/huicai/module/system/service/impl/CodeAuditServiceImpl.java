package com.huicai.module.system.service.impl;

import com.huicai.module.system.service.CodeAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 项目代码审核服务实现
 */
@Slf4j
@Service
public class CodeAuditServiceImpl implements CodeAuditService {

    @Value("${huicai.code-audit.project-root:${user.dir}}")
    private String projectRoot;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public String performAudit() {
        log.info("[CodeAudit] 开始执行每日代码审核...");
        StringBuilder report = new StringBuilder();
        String auditTime = LocalDateTime.now().format(DATE_FMT);

        report.append("# 慧财财务 · 每日代码审核报告\n");
        report.append("生成时间: ").append(auditTime).append("\n\n");

        report.append(sectionGit());
        report.append(sectionBackend());
        report.append(sectionFrontend());
        report.append(sectionSql());
        report.append(sectionProgress());
        report.append(sectionSuggestions());

        report.append("\n---\n");
        report.append("报告结束 · 自动生成于 ").append(auditTime).append("\n");

        saveReport(report.toString(), LocalDateTime.now().format(FILE_FMT));

        log.info("[CodeAudit] 每日代码审核完成");
        return report.toString();
    }

    /* ============== 报告章节 ============== */

    private String sectionGit() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 一、Git 提交与工作区状态\n\n");

        // 最近 5 条提交
        sb.append("### 最近 5 条提交\n");
        List<String> logLines = exec("git", "log", "--oneline", "-5", "--format=%h | %ai | %s");
        if (logLines.isEmpty()) {
            sb.append("_暂无提交记录_\n");
        } else {
            for (String l : logLines) sb.append("- ").append(l).append("\n");
        }
        sb.append("\n");

        // 工作区状态
        sb.append("### 工作区状态\n");
        List<String> statusLines = exec("git", "status", "--short");
        if (statusLines.isEmpty()) {
            sb.append("- 工作区 **干净**, 无未提交改动\n");
        } else {
            sb.append("- ⚠️ 工作区有 ").append(statusLines.size()).append(" 处未提交改动:\n");
            for (String l : statusLines) sb.append("  - ").append(l).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String sectionBackend() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 二、后端代码统计 (Java)\n\n");
        File backendSrc = new File(resolveProjectDir(), "backend/src/main/java/com/huicai");
        if (!backendSrc.exists()) {
            sb.append("_后端源码目录不存在_\n\n");
            return sb.toString();
        }

        // 按一级子目录统计
        Map<String, Integer> moduleFiles = new LinkedHashMap<>();
        File[] modules = backendSrc.listFiles(File::isDirectory);
        if (modules != null) {
            for (File m : modules) {
                moduleFiles.put(m.getName(), countFilesBySuffix(m, ".java"));
            }
        }

        // 按类型统计总数
        int entityCount = countFilesBySuffix(backendSrc, "Entity.java");
        int mapperCount = countFilesBySuffix(backendSrc, "Mapper.java");
        int serviceCount = countFilesBySuffix(backendSrc, "Service.java");
        int controllerCount = countFilesBySuffix(backendSrc, "Controller.java");
        int totalJava = countFilesBySuffix(backendSrc, ".java");

        sb.append("| 指标 | 数量 |\n");
        sb.append("|------|------|\n");
        sb.append("| 总 Java 文件 | ").append(totalJava).append(" |\n");
        sb.append("| Entity 实体类 | ").append(entityCount).append(" |\n");
        sb.append("| Mapper 接口 | ").append(mapperCount).append(" |\n");
        sb.append("| Service 服务 | ").append(serviceCount).append(" |\n");
        sb.append("| Controller 控制器 | ").append(controllerCount).append(" |\n\n");

        sb.append("| 模块 | Java 文件数 |\n");
        sb.append("|------|------------|\n");
        for (Map.Entry<String, Integer> e : moduleFiles.entrySet()) {
            sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String sectionFrontend() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 三、前端代码统计\n\n");
        File frontendSrc = new File(resolveProjectDir(), "frontend/src");
        if (!frontendSrc.exists()) {
            sb.append("_前端源码目录不存在_\n\n");
            return sb.toString();
        }

        int vueFiles = countFilesBySuffix(frontendSrc, ".vue");
        int tsFiles = countFilesBySuffix(frontendSrc, ".ts");
        int scssFiles = countFilesBySuffix(frontendSrc, ".scss");

        sb.append("| 类型 | 数量 |\n");
        sb.append("|------|------|\n");
        sb.append("| .vue 组件 | ").append(vueFiles).append(" |\n");
        sb.append("| .ts 脚本 | ").append(tsFiles).append(" |\n");
        sb.append("| .scss 样式 | ").append(scssFiles).append(" |\n");
        sb.append("| **合计** | ").append(vueFiles + tsFiles + scssFiles).append(" |\n\n");
        return sb.toString();
    }

    private String sectionSql() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 四、数据库迁移脚本\n\n");
        File migrationDir = new File(resolveProjectDir(), "backend/src/main/resources/db/migration");
        if (!migrationDir.exists()) {
            sb.append("_迁移脚本目录不存在_\n\n");
            return sb.toString();
        }
        File[] sqlFiles = migrationDir.listFiles((d, n) -> n.endsWith(".sql"));
        int count = sqlFiles == null ? 0 : sqlFiles.length;
        sb.append("- 总迁移脚本数: **").append(count).append("** 个\n\n");
        if (sqlFiles != null) {
            for (File f : sqlFiles) {
                sb.append("  - ").append(f.getName()).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private String sectionProgress() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 五、开发进度对照 (基于 Phase 计划)\n\n");
        File backendSrc = new File(resolveProjectDir(), "backend/src/main/java/com/huicai");
        boolean hasAsync = new File(backendSrc, "config/AsyncConfig.java").exists();
        boolean hasSecurity = new File(backendSrc, "config/security").exists();
        boolean hasSystemModule = new File(backendSrc, "module/system").exists();
        boolean hasFinanceModule = new File(backendSrc, "module/finance").exists();

        sb.append("| Phase | 状态 | 依据 |\n");
        sb.append("|-------|------|------|\n");
        sb.append("| Phase 0 · 项目骨架 | ").append(hasAsync ? "✅ 已完成" : "❌ 未开始").append(" | AsyncConfig / Docker Compose |\n");
        sb.append("| Phase 1 · 基础数据 | ").append(hasSystemModule ? "✅ 已完成" : "❌ 未开始").append(" | module/system 模块 |\n");
        sb.append("| Phase 2 · RBAC 权限 | ").append(hasSecurity ? "✅ 已完成" : "❌ 未开始").append(" | config/security + JWT |\n");
        sb.append("| Phase 3 · 财务核心 | ").append(hasFinanceModule ? "🟨 进行中" : "❌ 未开始").append(" | module/finance 模块 (凭证/余额/结账待完善) |\n");
        sb.append("| Phase 4+ · 业务单据/出纳/... | ❌ 未开始 | 尚未搭建模块 |\n\n");
        return sb.toString();
    }

    private String sectionSuggestions() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 六、下一步建议\n\n");

        File backendSrc = new File(resolveProjectDir(), "backend/src/main/java/com/huicai");
        File financeDir = new File(backendSrc, "module/finance");
        int controllerCount = countFilesBySuffix(financeDir, "Controller.java");
        int serviceImplCount = countFilesBySuffix(financeDir, "ServiceImpl.java");

        sb.append("1. **优先完成 Phase 3 财务核心闭环**: 凭证校验 → 审核 → 记账 → 科目余额更新 → 结账\n");
        sb.append("2. **凭证核心状态机**: 草稿 → 提交 → 审核 → 记账 → 红冲 的完整流程\n");
        sb.append("3. **借贷平衡硬约束**: 保存凭证时强制校验 `SUM(借) = SUM(贷)`\n");
        sb.append("4. **科目余额实时更新**: 记账操作同步更新 `t_subject_balance`\n");
        sb.append("5. **前端联动**: 补齐凭证审核/记账按钮, 打通操作闭环\n");

        sb.append("\n_当前后端 finance 模块: ").append(controllerCount)
          .append(" 个 Controller, ").append(serviceImplCount)
          .append(" 个 ServiceImpl_\n\n");
        return sb.toString();
    }

    /* ============== 工具方法 ============== */

    /**
     * 执行外部命令并返回所有输出行
     */
    private List<String> exec(String... cmd) {
        List<String> lines = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(resolveProjectDir());
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            p.waitFor();
        } catch (Exception e) {
            log.warn("[CodeAudit] 命令执行失败 {}: {}", String.join(" ", cmd), e.getMessage());
        }
        return lines;
    }

    /**
     * 递归统计目录下指定后缀的文件数
     */
    private int countFilesBySuffix(File dir, String suffix) {
        if (dir == null || !dir.exists()) return 0;
        int[] counter = {0};
        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().endsWith(suffix))
                 .forEach(p -> counter[0]++);
        } catch (Exception e) {
            log.warn("[CodeAudit] 统计文件失败 {}: {}", dir, e.getMessage());
        }
        return counter[0];
    }

    /**
     * 解析项目根目录
     */
    private File resolveProjectDir() {
        String root = projectRoot;
        // Spring 解析 ${user.dir} 失败时回退到当前工作目录
        if (root == null || root.contains("${")) {
            root = System.getProperty("user.dir");
        }
        File dir = new File(root);
        // 优先检测 .git 目录, 向上查找
        while (dir != null && dir.exists()) {
            if (new File(dir, ".git").exists()) return dir;
            dir = dir.getParentFile();
        }
        return new File(System.getProperty("user.dir"));
    }

    /**
     * 保存报告到 reports 目录
     */
    private void saveReport(String content, String fileTime) {
        try {
            File reportsDir = new File(resolveProjectDir(), "docs/reports");
            if (!reportsDir.exists()) reportsDir.mkdirs();
            File reportFile = new File(reportsDir, "code-audit_" + fileTime + ".md");
            try (FileWriter fw = new FileWriter(reportFile)) {
                fw.write(content);
            }
            log.info("[CodeAudit] 报告已保存: {}", reportFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("[CodeAudit] 保存报告失败: {}", e.getMessage());
        }
    }
}
