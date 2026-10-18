package com.huicai.agency.batch.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P120：`BatchOperationController` 的角色守卫。
 *
 * <h2>为什么需要它</h2>
 * 4 个端点当前<b>全是 501 未实现桩</b>（P107 D3 为避免假成功而显式禁用），
 * 所以<b>今天并无越权漏洞</b> —— 会计调这个端点只会拿到 501。
 *
 * <p>但本 SPEC 在修复 `EnterpriseController` 时<b>漏扫了同功能域的本控制器</b>，
 * 一度把它记成「会计仍可跨客户批量结账/审核」。实为过度断言，
 * 结论已据实更正。教训沉淀为：<b>判定「有漏洞」前先看端点到底是实现还是桩</b>，
 * 别因为「没有守卫」就自动推导「可被打」。
 *
 * <h2>本守卫的价值</h2>
 * 这些端点<b>终将实现</b>（REQ-2026-134 / P107 D3 只是暂禁）。届时授权是
 * 最容易漏的一环 —— 本守卫把「端点必须有角色校验」钉成机械不变量：
 * <b>实现放开而忘了加守卫 ⇒ 立刻红</b>。
 */
@DisplayName("SPEC-P120：批量操作端点角色守卫")
class BatchOperationControllerGuardStructureTest {

    private static final String CONTROLLER =
            "src/main/java/com/huicai/agency/batch/controller/BatchOperationController.java";

    /** 控制器内全部端点，逐个断言 */
    private static final List<String> ENDPOINTS = List.of(
            "/import", "/audit-vouchers", "/audit-invoices", "/close");

    @Test
    @DisplayName("每个端点都必须接入角色守卫")
    void everyEndpointHasRoleGuard() throws Exception {
        String src = read(CONTROLLER);
        List<String> unguarded = new ArrayList<>();

        Matcher m = Pattern.compile("@PostMapping\\(\"([^\"]+)\"\\)").matcher(src);
        while (m.find()) {
            String path = m.group(1);
            // 取该方法体（按大括号配平）
            String body = methodBody(src, openBraceAfter(src, m.end()));

            boolean guarded = body.contains("roleGuard.")
                    || body.contains("requireAgencyAdmin")
                    || body.contains("requireVisibleEnterprise")
                    || body.contains("resolveVisibleEnterpriseIds")
                    || body.contains("@PreAuthorize");
            if (!guarded) {
                unguarded.add(path);
            }
        }

        assertTrue(unguarded.isEmpty(),
                "以下批量操作端点未接入角色守卫 —— 实现放开时即成为越权入口：\n  "
                        + String.join("\n  ", unguarded));
    }

    @Test
    @DisplayName("四个端点数量不得减少（防止有人删端点绕过守卫清单）")
    void endpointCountMustNotDrop() throws Exception {
        String src = read(CONTROLLER);
        Matcher m = Pattern.compile("@PostMapping\\(\"([^\"]+)\"\\)").matcher(src);
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1));
        }
        assertTrue(found.size() >= ENDPOINTS.size(),
                "端点数量从 " + ENDPOINTS.size() + " 降到 " + found.size()
                        + " —— 若为删除，须同步确认守卫清单；若有人靠删端点绕过守卫，此处即红");
    }

    @Test
    @DisplayName("三处仍未实现的 501 必须保留 —— 不得为了让前端「看起来能用」而恢复假成功")
    void notImplementedStubMustRemainFailLoud() throws Exception {
        // 与 BatchServiceNotImplementedTest 呼应：那里锁 Service 行为，
        // 这里锁控制器级不变量。两个层次任一被破坏都会被发现。
        assertTrue(read(CONTROLLER).contains("class BatchOperationController"),
                "文件路径/类名已变，本守卫的锚点失效");

        // 控制器本身不 return R.ok(...) 之外的成功包装 —— 由 Service 抛 501，
        // 控制器不得捕获后转成成功响应。
        String src = read(CONTROLLER);
        assertTrue(!src.contains("catch (BusinessException"),
                "控制器不得捕获 BatchOperation 的 BusinessException —— "
                        + "那会把 501 假成功包装成 200，是本 SPEC 明令禁止的假成功");
        assertTrue(!src.contains("try {"),
                "控制器不得用 try 包裹批量调用 —— 同上，会吞噬 fail-loud");
    }

    @Test
    @DisplayName("守卫自身有效：构造无守卫端点，规则必须判红")
    void guardDetectsUnguardedEndpoint() {
        String bad = "    @PostMapping(\"/import\")\n"
                + "    public R<Void> f(List f, Long e) {\n"
                + "        return R.ok(svc.doIt(f, e));\n    }";
        String good = "    @PostMapping(\"/import\")\n"
                + "    public R<Void> f(List f, Long e) {\n"
                + "        roleGuard.requireAgencyAdmin(\"批量导入\");\n"
                + "        return R.ok(svc.doIt(f, e));\n    }";

        assertTrue(bad.contains("roleGuard.") == false);
        assertTrue(good.contains("roleGuard."),
                "规则必须能识别已加守卫的端点");
    }

    // ── 工具 ──

    private static String read(String rel) throws Exception {
        return Files.readString(Path.of(rel), StandardCharsets.UTF_8);
    }

    /** 从 {@code from} 起找第一个 `{` */
    private static int openBraceAfter(String src, int from) {
        int i = src.indexOf('{', from);
        assertTrue(i > 0, "找不到方法体左括号");
        return i;
    }

    /** 按大括号配平取方法体 */
    private static String methodBody(String src, int open) {
        int depth = 0;
        for (int k = open; k < src.length(); k++) {
            char c = src.charAt(k);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth <= 0) {
                    return src.substring(open, k + 1);
                }
            }
        }
        return src.substring(open);
    }
}