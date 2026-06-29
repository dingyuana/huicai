package com.huicai.module.finance.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 夜间项目健康检查定时任务.
 * <p>
 * 每天凌晨 4:00 执行, 包含:
 * 1. Maven 编译检查 (test-compile)
 * 2. 单元测试快速集 (ci-fast, 仅 Controller/Service 层, 跳过 Mapper/E2E)
 * 3. 代码覆盖率快照
 * 4. 输出汇总报告
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NightlyHealthCheckJob {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PROJECT_DIR = "/root/data/disk/huicai/backend";
    private static final int TIMEOUT_MINUTES = 25;

    /**
     * 阶段 1: Maven test-compile 检查.
     */
    private Map<String, Object> runCompileCheck() {
        log.info("[1/3] 执行编译检查...");
        Map<String, Object> result = new HashMap<>();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "mvn", "test-compile", "-q",
                "-DskipTests",
                "-Dmaven.test.skip=false"
            );
            pb.directory(new File(PROJECT_DIR));
            pb.redirectErrorStream(true);
            Process p = pb.start();

            List<String> output = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }

            boolean completed = p.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            int exitCode = completed ? p.exitValue() : -1;

            result.put("compileSuccess", completed && exitCode == 0);
            result.put("compileExitCode", exitCode);
            result.put("compileDuration", completed ? "completed" : "TIMEOUT");
            if (!output.isEmpty()) {
                result.put("compileOutput", String.join("\n", output));
            }
        } catch (Exception e) {
            log.error("编译检查异常: {}", e.getMessage(), e);
            result.put("compileSuccess", false);
            result.put("compileError", e.getMessage());
        }

        log.info("[1/3] 编译检查: {}", Boolean.TRUE.equals(result.get("compileSuccess")) ? "通过" : "失败");
        return result;
    }

    /**
     * 阶段 2: 快速测试集 (Controller + Service 层, 跳过需要 Testcontainers 的 Mapper/E2E).
     */
    private Map<String, Object> runQuickTests() {
        log.info("[2/3] 执行快速测试集...");
        Map<String, Object> result = new HashMap<>();

        try {
            // 构建测试过滤器: 排除 Mapper 和 E2E 测试 (需要 Docker/Testcontainers)
            String[] excludePatterns = {
                "**/mapper/**",
                "**/e2e/**",
                "**/*IntegrationTest*",
                "**/ConcurrencyLoadTest*",
                "**/SimpleConcurrencyLoadTest*"
            };

            StringBuilder testArg = new StringBuilder("-Dtest=");
            boolean first = true;
            
            // 收集所有 Controller 和 Service impl 测试
            ProcessBuilder pb = new ProcessBuilder(
                "find", PROJECT_DIR + "/src/test/java",
                "-name", "*Test.java",
                "-path", "*/controller/*",
                "-o", "-path", "*/service/impl/*Test.java"
            );
            pb.redirectErrorStream(true);
            Process findProc = pb.start();
            List<String> testClasses = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(findProc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Convert file path to dotted class name
                    String className = convertToClassName(line);
                    if (className != null && !className.isEmpty()) {
                        testClasses.add(className);
                    }
                }
            }
            findProc.waitFor(5, TimeUnit.MINUTES);

            if (testClasses.isEmpty()) {
                log.warn("[2/3] 未找到 Controller/Service 测试类");
                result.put("testsPassed", false);
                result.put("testCount", 0);
                result.put("testDetail", "未找到测试类");
                return result;
            }

            // 取前 50 个测试类避免命令过长
            int maxTests = Math.min(testClasses.size(), 50);
            for (int i = 0; i < maxTests; i++) {
                if (!first) testArg.append(",");
                testArg.append(testClasses.get(i));
                first = false;
            }

            log.info("[2/3] 运行 {} 个测试类...", maxTests);

            ProcessBuilder mvn = new ProcessBuilder(
                "mvn", "test",
                testArg.toString(),
                "-Djacoco.skip=true",
                "-Dsurefire.rerunFailingTestsCount=0"
            );
            mvn.directory(new File(PROJECT_DIR));
            mvn.redirectErrorStream(true);
            Process p = mvn.start();

            List<String> output = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }

            boolean completed = p.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            int exitCode = completed ? p.exitValue() : -1;

            // 解析测试结果
            int totalTests = 0;
            int failures = 0;
            int errors = 0;
            for (String line : output) {
                if (line.contains("Tests run:")) {
                    try {
                        String[] parts = line.split(",");
                        for (String part : parts) {
                            part = part.trim();
                            if (part.startsWith("Tests run:")) {
                                totalTests = Integer.parseInt(part.split(":")[1].trim());
                            } else if (part.startsWith("Failures:")) {
                                failures = Integer.parseInt(part.split(":")[1].trim());
                            } else if (part.startsWith("Errors:")) {
                                errors = Integer.parseInt(part.split(":")[1].trim());
                            }
                        }
                    } catch (Exception e) {
                        // ignore parse errors
                    }
                }
            }

            result.put("testsPassed", completed && exitCode == 0);
            result.put("testCount", totalTests);
            result.put("failures", failures);
            result.put("errors", errors);
            result.put("testExitCode", exitCode);
            result.put("testDuration", completed ? "completed" : "TIMEOUT");

            // 提取失败的测试名
            if (failures > 0 || errors > 0) {
                List<String> failedTests = new ArrayList<>();
                for (String line : output) {
                    if (line.contains("<<< FAILURE!") || line.contains("<<< ERROR!")) {
                        failedTests.add(line.trim());
                    }
                }
                result.put("failedTests", failedTests.subList(0, Math.min(failedTests.size(), 10)));
            }

        } catch (Exception e) {
            log.error("快速测试集异常: {}", e.getMessage(), e);
            result.put("testsPassed", false);
            result.put("testError", e.getMessage());
        }

        log.info("[2/3] 快速测试: {} (共 {} 个, 失败 {} 个, 错误 {} 个)",
            Boolean.TRUE.equals(result.get("testsPassed")) ? "通过" : "有失败",
            result.get("testCount"), result.get("failures"), result.get("errors"));
        return result;
    }

    /**
     * 阶段 3: 生成汇总报告.
     */
    private void logReport(Map<String, Object> report) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("=".repeat(50)).append("\n");
        sb.append("  夜间项目健康检查报告\n");
        sb.append("=".repeat(50)).append("\n");
        sb.append(String.format("  执行时间: %s ~ %s\n", report.get("startTime"), report.get("endTime")));
        sb.append(String.format("  总体状态: %s\n", report.get("overallStatus")));
        sb.append("\n  编译检查: ");
        sb.append(Boolean.TRUE.equals(report.get("compileSuccess")) ? "✅ 通过" : "❌ 失败");
        sb.append("\n  快速测试: ");
        sb.append(Boolean.TRUE.equals(report.get("testsPassed")) ? "✅ 通过" : "❌ 有失败");
        sb.append(String.format(" (%d 个用例, %d 失败, %d 错误)",
            report.getOrDefault("testCount", 0),
            report.getOrDefault("failures", 0),
            report.getOrDefault("errors", 0)));
        
        if (report.containsKey("failedTests")) {
            sb.append("\n  失败详情:\n");
            @SuppressWarnings("unchecked")
            List<String> failures = (List<String>) report.get("failedTests");
            for (String f : failures) {
                sb.append("    ").append(f).append("\n");
            }
        }
        sb.append("=".repeat(50)).append("\n");

        log.info(sb.toString());
    }

    /**
     * 将文件路径转换为 Java 类名.
     * e.g., "/path/to/Backend/src/test/java/com/huicai/module/x/ControllerTest.java"
     * → "com.huicai.module.x.ControllerTest"
     */
    private String convertToClassName(String filePath) {
        if (filePath == null || !filePath.endsWith("Test.java")) {
            return null;
        }
        // Remove project dir prefix
        String relative = filePath.replace(PROJECT_DIR + "/src/test/java/", "");
        // Replace path separators with dots, remove .java
        return relative.replace('/', '.').replace('\\', '.').replace(".java", "");
    }
}
