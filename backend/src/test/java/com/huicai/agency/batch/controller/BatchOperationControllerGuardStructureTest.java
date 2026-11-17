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
 * 4 个端点此前<b>全是 501 未实现桩</b>（P107 D3 为避免假成功而显式禁用）；
 * P162 接入真实状态机后，端点必须继续同时受角色和目标企业归属校验保护。
 *
 * <p>但本 SPEC 在修复 `EnterpriseController` 时<b>漏扫了同功能域的本控制器</b>，
 * 一度把它记成「会计仍可跨客户批量结账/审核」。实为过度断言，
 * 结论已据实更正。教训沉淀为：<b>判定「有漏洞」前先看端点到底是实现还是桩</b>，
 * 别因为「没有守卫」就自动推导「可被打」。
 *
 * <h2>本守卫的价值</h2>
 * 这些端点已接入现有状态机。守卫把「端点必须有角色校验和目标企业归属校验」
 * 钉成机械不变量，防止后续新增/修改时漏掉代理边界。
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

            int roleCheck = body.indexOf("requireAgencyAdmin");
            int enterpriseCheck = body.indexOf("requireVisibleEnterprise");
            int delegation = body.indexOf("return R.ok(");
            if (roleCheck < 0 || enterpriseCheck < 0 || delegation < 0
                    || roleCheck > enterpriseCheck || enterpriseCheck > delegation) {
                unguarded.add(path);
            }
        }

        assertTrue(unguarded.isEmpty(),
                "以下端点缺少按顺序执行的角色/企业归属守卫：\n  "
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
    @DisplayName("每个端点都先校验角色与企业归属，不吞掉服务层失败")
    void everyEndpointChecksEnterpriseOwnershipAndPropagatesFailures() throws Exception {
        String src = read(CONTROLLER);
        Matcher m = Pattern.compile("@PostMapping\\(\"([^\"]+)\"\\)").matcher(src);
        List<String> missingOwnershipGuard = new ArrayList<>();
        while (m.find()) {
            String path = m.group(1);
            String body = methodBody(src, openBraceAfter(src, m.end()));
            if (!body.contains("requireVisibleEnterprise")) {
                missingOwnershipGuard.add(path);
            }
        }
        assertTrue(missingOwnershipGuard.isEmpty(),
                "以下端点未校验目标企业属于当前代理：\n  "
                        + String.join("\n  ", missingOwnershipGuard));

        assertTrue(!src.contains("catch (BusinessException"),
                "控制器不得吞掉业务异常并包装成成功响应");
        assertTrue(!src.contains("try {"),
                "控制器不得用 try 包裹批量调用并隐藏失败");
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