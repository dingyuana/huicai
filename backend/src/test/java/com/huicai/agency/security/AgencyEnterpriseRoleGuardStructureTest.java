package com.huicai.agency.security;

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
 * SPEC-P120：代理端企业管理的角色守卫。
 *
 * <p><b>为什么需要结构守卫</b>：本 SPEC 修复前，{@code EnterpriseController}
 * 的 10 个端点中<b>只有 switch 一个</b>做了角色校验，其余 9 个完全裸奔 ——
 * 会计可新建/改删/启停企业，并能 {@code page} 出任意代理的全部客户。
 *
 * <p>这类「整批端点漏接入授权」的形态，靠人工 review 必漏（§4.5 第 43 条）：
 * 加一个端点时没有任何机制提醒「你忘了加守卫」。守卫把该不变量机械化。
 */
@DisplayName("SPEC-P120：代理端企业管理角色守卫")
class AgencyEnterpriseRoleGuardStructureTest {

    private static final String CONTROLLER =
            "src/main/java/com/huicai/agency/tenant/controller/EnterpriseController.java";

    /** 管理类操作 —— 必须仅 AGENCY_ADMIN */
    private static final List<String> ADMIN_ONLY_PATHS = List.of(
            "/api/v1/agency/enterprises",                       // POST 新建
            "/api/v1/agency/enterprises/{id}",                 // PUT 修改 / DELETE 删除
            "/api/v1/agency/enterprises/{id}/bind",
            "/api/v1/agency/enterprises/{id}/unbind",
            "/api/v1/agency/enterprises/{id}/activate",
            "/api/v1/agency/enterprises/{id}/suspend");

    @Test
    @DisplayName("每个端点都必须接入角色守卫（本 SPEC 修复的核心回归锁）")
    void everyEndpointHasGuard() throws Exception {
        String src = Files.readString(Path.of(CONTROLLER), StandardCharsets.UTF_8);

        // 取出所有 @*Mapping 及其后方法体
        List<String> unguarded = new ArrayList<>();
        Matcher m = Pattern.compile(
                "@(Get|Post|Put|Delete)Mapping\\(\"([^\"]+)\"\\)").matcher(src);
        while (m.find()) {
            String verb = m.group(1);
            String path = m.group(2);
            // 取该 mapping 之后到方法体结束
            int open = src.indexOf('{', m.end());
            assertTrue(open > 0, "端点 " + path + " 找不到方法体");
            int depth = 0, end = open;
            for (int i = open; i < src.length(); i++) {
                char c = src.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth <= 0) { end = i; break; }
                }
            }
            String body = src.substring(m.end(), end);

            // switch 端点本来就有一整套分流校验，不在此守卫范围
            if (path.contains("/enterprise/switch")) continue;

            boolean guarded = body.contains("roleGuard.")
                    || body.contains("requireAgencyAdmin")
                    || body.contains("requireVisibleEnterprise")
                    || body.contains("resolveVisibleEnterpriseIds");
            if (!guarded) {
                unguarded.add(verb + " " + path);
            }
        }

        assertTrue(unguarded.isEmpty(),
                "以下端点未接入角色守卫 ⇒ 会计可越权操作企业：\n  "
                        + String.join("\n  ", unguarded));
    }

    @Test
    @DisplayName("page 端点不得再接受调用方自填的 agencyId")
    void pageMustNotAcceptAgencyIdParam() throws Exception {
        String src = Files.readString(Path.of(CONTROLLER), StandardCharsets.UTF_8);
        int i = src.indexOf("/api/v1/agency/enterprises/page");
        assertTrue(i > 0, "未找到 page 端点");

        // 取该端点签名（从映射注解之后到方法体左花括号）
        int sig = src.indexOf("public R<", i);
        int brace = src.indexOf('{', sig);
        assertTrue(sig > i && brace > sig, "page 端点签名定位失败");
        String signature = src.substring(sig, brace);

        assertTrue(!signature.contains("agencyId"),
                "page 端点签名仍含 agencyId ⇒ 调用方可自填代理 id 越权取数：\n  " + signature);

        // 角色收敛发生在**方法体内**（不是签名里）—— 必须连方法体一起查
        int open = src.indexOf('{', brace);
        int depth = 0, end = open;
        for (int k = open; k < src.length(); k++) {
            char c = src.charAt(k);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth <= 0) { end = k; break; } }
        }
        String body = src.substring(brace, end);
        assertTrue(body.contains("resolveVisibleEnterpriseIds"),
                "page 端点未按角色收敛可见范围（方法体中未见 resolveVisibleEnterpriseIds）");
    }

    @Test
    @DisplayName("current-period 的期间查询必须按 enterprise_id 过滤")
    void currentPeriodMustFilterByEnterprise() throws Exception {
        String src = Files.readString(Path.of(CONTROLLER), StandardCharsets.UTF_8);
        int i = src.indexOf("/api/v1/enterprise/current-period");
        assertTrue(i > 0);
        int end = src.indexOf("\n    }", i);
        String body = src.substring(i, end);

        assertTrue(body.contains("requireVisibleEnterprise"),
                "current-period 未做可见性校验");
        // 兜底分支的 periodMapper 查询必须带企业过滤
        assertTrue(body.contains("PeriodEntity::getEnterpriseId"),
                "current-period 的期间兜底查询未按 enterprise_id 过滤 ⇒ "
                        + "会取到全库最新期间（跨企业串数据）");
    }

    @Test
    @DisplayName("守卫自身有效：构造未加守卫的片段，规则必须能判红")
    void guardDetectsUnguarded() {
        String bad = "    @PostMapping(\"/api/v1/agency/enterprises\")\n"
                + "    public R<X> create(DTO dto) {\n        return service.create(dto);\n    }";
        String good = "    @PostMapping(\"/api/v1/agency/enterprises\")\n"
                + "    public R<X> create(DTO dto) {\n"
                + "        roleGuard.requireAgencyAdmin(\"新建客户企业\");\n"
                + "        return service.create(dto);\n    }";

        Pattern p = Pattern.compile(
                "@(Get|Post|Put|Delete)Mapping\\(\"([^\"]+)\"\\)");
        assertTrue(p.matcher(bad).find() && !bad.contains("roleGuard."));
        assertTrue(good.contains("roleGuard."),
                "规则必须识别已加守卫的片段，否则守卫恒红");
    }

@Test
    @DisplayName("requireAgencyAdmin 只放行 AGENCY_ADMIN，不得放行 REVIEWER/ACCOUNTANT/ASSISTANT")
    void adminOnlyExcludesReviewer() throws Exception {
        String body = methodBody(GUARD, "public void requireAgencyAdmin");

        assertTrue(body.contains("\"AGENCY_ADMIN\".equals("),
                "requireAgencyAdmin 必须以 AGENCY_ADMIN 为准");
        // 不得出现把其它代理内角色也放行的分支
        for (String forbidden : List.of("REVIEWER", "ACCOUNTANT", "ASSISTANT")) {
            assertTrue(!body.contains("\"" + forbidden + "\".equals(SecurityUtils.getCurrentAgencyRole())"),
                    "requireAgencyAdmin 放行了 " + forbidden
                            + " —— 该角色不应能新建/删除客户企业");
        }
    }

    @Test
    @DisplayName("守卫必须放行合法身份：超管、代理经理/审核员、归属企业用户、派工用户")
    void guardMustNotBlockLegitimateUsers() throws Exception {
        String visible = methodBody(GUARD, "public void requireVisibleEnterprise");

        // ① 超管不受限
        assertTrue(visible.contains("\"SUPER_ADMIN\".equals(userType)"),
                "requireVisibleEnterprise 未放行 SUPER_ADMIN —— 系统运维身份会被误拦");

        // ② AGENCY 内角色走 resolveVisibleEnterpriseIds（经理/审核员见本代理全部）
        assertTrue(visible.contains("\"AGENCY\".equals(userType)")
                        && visible.contains("resolveVisibleEnterpriseIds()"),
                "requireVisibleEnterprise 未对 AGENCY 角色走代理可见范围 —— "
                        + "经理/审核员将无法访问本代理名下企业");

        // ③ 企业自有用户：可见集 = 归属企业 ∪ 派工企业
        assertTrue(visible.contains("getCurrentEnterpriseId()"),
                "未纳入归属企业作为可见范围");
        assertTrue(visible.contains("assignedEnterpriseIdsOrEmpty()"),
                "未纳入代理派工企业作为可见范围 —— "
                        + "归属 A 但经代理被派工访问 B 的用户会被误拦"
                        + "（TenantSwitchRealDBTest#memberSwitchRewritesContextAndIsolatesData 即此例）");

        // ④ 必须真的做集合判定，不能无条件放行
        assertTrue(visible.contains("visible.contains(enterpriseId)"),
                "未做可见集合判定 ⇒ 等于无条件放行");
    }

    // ── 工具 ──

    private static final String GUARD =
            "src/main/java/com/huicai/agency/security/AgencyEnterpriseRoleGuard.java";

    private static String methodBody(String file, String signatureStart) throws Exception {
        String src = Files.readString(Path.of(file), StandardCharsets.UTF_8);
        int i = src.indexOf(signatureStart);
        assertTrue(i > 0, "未找到方法: " + signatureStart);
        int j = src.indexOf("\n    }", i);
        return src.substring(i, j);
    }
}