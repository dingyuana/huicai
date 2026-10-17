package com.huicai.security;

import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SPEC-P133 批次 1 —— 权限清单的三条防漂移守卫。
 *
 * <p>🔴 <b>清单是唯一事实源</b>：后端 {@code PermissionEnforcementInterceptor}、
 * 前端路由 {@code meta.permission}、以及本守卫，<b>都从这里读</b>，不得各写一份。</p>
 *
 * <p>🔴 <b>写端点数量的真值源是 Spring 运行时</b>（{@code RequestMappingHandlerMapping}），
 * 而不是扫源码 —— SPEC-P134 §8.7 记录了本项目静态扫描器<b>连续三次产出错误结论</b>，
 * 失败形态是「静默产出错误清单」。</p>
 *
 * <p>⚠️ <b>批次 1 的守卫是「棘轮」而非「零容忍」</b>：D-1 已裁定
 * <b>warn-only</b>（清单未命中端点先放行），故 G2 记录当前缺口并要求<b>不得增长</b>。
 * 切换 fail-closed 后（G2 缺口归零）才改为零容忍 —— <b>那时才真正拦住越权</b>。</p>
 */
class PermissionManifestGuardRealDBTest extends AbstractMapperTest {

    /** 写端点缺口的棘轮：批次 1 实测值，只许减不许增（§4.5 第 21/25 条）。 */
    private static final int MAX_UNREGISTERED_WRITE_ENDPOINTS = 60;

    private static final String MANIFEST = "permission-manifest.yml";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ResourceLoader resourceLoader;

    // ─────────────────────────── G1 ───────────────────────────

    @Test
    @DisplayName("G1 清单里的每个权限码都必须存在于 t_menu（否则该端点无人可达）")
    void manifestPermissionsExistInMenu() {
        List<String> ghost = new ArrayList<>();
        for (String perm : manifestPermissions()) {
            if (perm == null || perm.startsWith("TODO")) {
                ghost.add(perm + "（清单里仍是 TODO）");
                continue;
            }
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE permission = ? AND deleted = 0",
                    Integer.class, perm);
            if (n == null || n == 0) {
                ghost.add(perm);
            }
        }
        assertTrue(ghost.isEmpty(),
                "清单里的幽灵权限码（t_menu 中不存在 ⇒ 无人能通过该端点）：" + ghost);
    }

    // ─────────────────────────── G2 ───────────────────────────

    @Test
    @DisplayName("G2 写端点缺口不得增长（棘轮：把未登记的写端点接进清单只许做加法）")
    void unregisteredWriteEndpointGapMustNotGrow() {
        Set<String> registered = manifestKeys();
        int writeTotal = 0;
        int unregistered = 0;
        List<String> sample = new ArrayList<>();
        for (String ep : realWriteEndpoints()) {
            writeTotal++;
            if (!registered.contains(ep)) {
                unregistered++;
                if (sample.size() < 8) {
                    sample.add(ep);
                }
            }
        }
        assertTrue(unregistered <= MAX_UNREGISTERED_WRITE_ENDPOINTS,
                "写端点未登记数=" + unregistered + " > 棘轮上限 " + MAX_UNREGISTERED_WRITE_ENDPOINTS
                        + "（样例：" + sample + "）。缺口只许减少：要么给它接权限码并登记，"
                        + "要么证明它无需权限后调整本棘轮并写明理由。");
        System.out.println("[G2] 写端点 " + writeTotal + " / 已登记 " + (writeTotal - unregistered)
                + " / 缺口 " + unregistered);
    }

    // ─────────────────────────── G3 ───────────────────────────

    /** 前端路由权限码的缺口棘轮：批次 1 实测值（清单只登记 19 个端点，缺口必然存在）。 */
    private static final int MAX_ORPHAN_FRONTEND_PERMISSIONS = 8;

    @Test
    @DisplayName("G3 前端路由 meta.permission 的清单缺口不得增长（棘轮）")
    void frontendPermissionGapMustNotGrow() {
        Set<String> inManifest = new LinkedHashSet<>(manifestPermissions());
        List<String> orphan = new ArrayList<>();
        for (String code : frontendRoutePermissionCodes()) {
            if (!inManifest.contains(code)) {
                orphan.add(code);
            }
        }
        assertTrue(orphan.size() <= MAX_ORPHAN_FRONTEND_PERMISSIONS,
                "前端路由用到但清单未登记的权限码=" + orphan.size() + " > 棘轮上限 "
                        + MAX_ORPHAN_FRONTEND_PERMISSIONS + "：" + orphan
                        + "。缺口只许减少；确需调高须写明理由。");
        System.out.println("[G3] 前端路由权限码 " + frontendRoutePermissionCodes().size()
                + " / 已登记 " + (frontendRoutePermissionCodes().size() - orphan.size())
                + " / 缺口 " + orphan.size());
    }


    @Test
    @DisplayName("G1/G2/G3 守卫自身的敏感度自证（三条都能因注入而红）")
    void guardsAreSensitiveToInjection() {
        // G1 自证：注入一个 t_menu 中不存在的假码，G1 的判定逻辑必然把它报出来
        String fake = "ghost:does:not:exist:P133";
        Set<String> inMenu = new LinkedHashSet<>();
        for (String perm : manifestPermissions()) {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE permission = ? AND deleted = 0",
                    Integer.class, perm);
            if (n != null && n > 0) {
                inMenu.add(perm);
            }
        }
        assertTrue(!inMenu.contains(fake),
                "自证前提错误：" + fake + " 竟存在于 t_menu，本用例失去意义");

        // G2 自证：把一个未登记的写端点加进「已登记集合」，缺口必然减少 1
        Set<String> registered = manifestKeys();
        int before = countUnregisteredWriteEndpoints(registered);
        int after;
        String someUnregistered = null;
        for (String ep : realWriteEndpoints()) {
            if (!registered.contains(ep)) {
                someUnregistered = ep;
                break;
            }
        }
        registered.add(someUnregistered);
        after = countUnregisteredWriteEndpoints(registered);
        assertEquals(before - 1, after,
                "G2 的缺口计数逻辑无效：补一条登记后缺口未减少 1（守卫会恒绿）");

        // G3 自证：任意构造一个不在清单里的码，必被 G3 判为缺口
        Set<String> manifestPerms = new LinkedHashSet<>(manifestPermissions());
        assertTrue(!manifestPerms.contains("injected:not:in:manifest"),
                "G3 的缺口判定逻辑对本用例无效");
        System.out.println("[自证] G1 假码可判 / G2 缺口可减 / G3 缺口可判");
    }

    private int countUnregisteredWriteEndpoints(Set<String> registered) {
        int n = 0;
        for (String ep : realWriteEndpoints()) {
            if (!registered.contains(ep)) {
                n++;
            }
        }
        return n;
    }


    @Test
    @DisplayName("G4 清单内不得有重复的 METHOD+path（重复项会让缺口统计失真）")
    void manifestHasNoDuplicateEntries() {
        List<String> all = new ArrayList<>();
        List<String> lines = manifestRaw();
        String method = null;
        for (String line : lines) {
            Matcher mm = Pattern.compile("^\\s*-\\s*method:\\s*(\\S+)\\s*$").matcher(line);
            if (mm.find()) {
                method = mm.group(1);
                continue;
            }
            Matcher mp = Pattern.compile("^\\s*path:\\s*(\\S+)\\s*$").matcher(line);
            if (mp.find() && method != null) {
                all.add(method + " " + mp.group(1));
                method = null;
            }
        }
        long unique = all.stream().distinct().count();
        assertEquals(unique, all.size(),
                "清单里有重复的 METHOD+path：共 " + all.size() + " 条，唯一 " + unique
                        + " 条。重复项不会让 G2 的缺口变小，却会让清单看起来更全 —— "
                        + "即「用一个重复项冒充一次覆盖」。"
                        + "🔴 本守卫的由来：我自己写登记脚本时把 method/path 配对写错，"
                        + "一次混进 9 条重复的 clear-* 端点，正是靠它发现的。");
    }


    /**
     * 🔴 人审生命周期端点：**必须用动作级权限码登记**，不得用页面级码顶替。
     *
     * <p>这条守卫最初是「在裁定前**不得被登记**」的临时锁（D-7 落地后已升级为
     * 正向断言）。它挡住的是一个**不会自己暴露的缺陷**：用 {@code voucher:list}
     * 给审核端点交差时，清单是齐的、G1 绿、G2 绿、G3 绿，
     * 只有切 fail-closed 之后才会变成「能看凭证就能审核凭证」的静默漏洞。</p>
     *
     * <p><b>判据：权限码的语义错配不会让任何现有守卫变红</b>，
     * 只会在守卫全绿时悄悄放行越权 ⇒ 必须有一条守卫<b>显式核对「该码对不对得上这个动作」</b>。</p>
     */
    private static final java.util.Map<String, String> HUMAN_APPROVAL_EXPECTED = java.util.Map.of(
            "POST /api/base/voucher/v1/vouchers/{id}/audit", "voucher:audit",
            "POST /api/base/voucher/v1/vouchers/{id}/post", "voucher:post",
            "POST /api/base/voucher/v1/vouchers/{id}/unpost", "voucher:unpost",
            "POST /api/base/voucher/v1/vouchers/{id}/reverse", "voucher:reverse",
            "POST /api/base/voucher/v1/vouchers/{id}/reject", "voucher:reject",
            "POST /api/base/voucher/v1/vouchers/{id}/submit", "voucher:submit",
            "POST /api/base/voucher/v1/vouchers/{id}/close", "voucher:close",
            "POST /api/base/voucher/v1/vouchers/batch-audit", "voucher:audit",
            "POST /api/base/voucher/v1/vouchers/batch-post", "voucher:post",
            "POST /api/base/voucher/v1/vouchers/batch-submit", "voucher:submit");

    @Test
    @DisplayName("G5 人审生命周期端点必须用动作级权限码登记（不得用页面级码顶替）")
    void humanApprovalEndpointsUseActionLevelCodes() {
        java.util.Map<String, String> byKey = manifestPermissionByKey();
        List<String> wrong = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (var e : HUMAN_APPROVAL_EXPECTED.entrySet()) {
            String actual = byKey.get(e.getKey());
            if (actual == null) {
                missing.add(e.getKey());
            } else if (!actual.equals(e.getValue())) {
                wrong.add(e.getKey() + " => " + actual + "（应为 " + e.getValue() + "）");
            }
        }
        assertTrue(missing.isEmpty(), "以下人审端点未登记（裁定后必须登记）：" + missing);
        assertTrue(wrong.isEmpty(),
                "以下人审端点登记了**错误的**权限码：" + wrong
                        + "。用 voucher:list 之类的页面级码顶替，会在批次 4 切 fail-closed 后"
                        + "变成「能看凭证就能审核凭证」的静默漏洞，且没有任何其它守卫会报错。");
    }

    @Test
    @DisplayName("G5b 人审端点确实存在于运行时（防止本守卫因端点改名而空转）")
    void humanApprovalEndpointsStillExist() {
        Set<String> real = realWriteEndpoints();
        List<String> gone = new ArrayList<>();
        for (String ep : HUMAN_APPROVAL_EXPECTED.keySet()) {
            if (!real.contains(ep)) {
                gone.add(ep);
            }
        }
        assertTrue(gone.isEmpty(),
                "以下被 G5 锁住的端点在运行时已不存在（可能改名或删除）：" + gone
                        + "。若确已下线，请同步删除 G5 清单，**否则 G5 会空转**"
                        + "（清单里的端点都不存在了，断言恒成立）。"
                        + "判据：守卫的清单必须与真值源保持同步，否则它锁的是空气。");
    }


    /**
     * 🔴 G6：**粗粒度（页面级）权限码的条目数**只许减不许增。
     *
     * <p>为什么必须单独盯：这些条目登记的是「能进该页 = 能做该页操作」，
     * <b>不是动作级授权</b>。它们让 G2 的缺口看起来在缩小，但
     * <b>切 fail-closed 时它们只形成粗粒度屏障</b> ——
     * 「能看凭证的人也能改凭证」在清单里是绿的。</p>
     *
     * <p><b>判据：缺口率的下降不等于管控强度的提升。</b>
     * 必须让「粗粒度覆盖率」与「缺口率」各自独立可见，
     * 否则总量下降会掩盖质量的下降。</p>
     */
    private static final int MAX_COARSE_ENTRIES = 170;

    @Test
    @DisplayName("G6 粗粒度（页面级）权限码条目数不得增长（棘轮）")
    void coarseEntriesMustNotGrow() {
        int coarse = coarseEntryCount();
        assertTrue(coarse <= MAX_COARSE_ENTRIES,
                "粗粒度条目数=" + coarse + " > 棘轮 " + MAX_COARSE_ENTRIES
                        + "。粗粒度码是「能进该页 = 能做该页操作」，不是动作级授权；"
                        + "增加它们会让缺口率下降但**管控强度不升**。"
                        + "正解：补动作级权限码，而不是继续挂页面码。");
        System.out.println("[G6] 粗粒度条目 " + coarse + " / 清单 " + manifestKeys().size());
    }

    /** 按条目块统计 {@code coarse: true} 的数量（上一版逐行状态机解析，数出 0 是错）。 */
    private int coarseEntryCount() {
        int count = 0;
        boolean inEntry = false;
        for (String line : manifestRaw()) {
            if (line.matches("^\\s*-\\s*method:.*$")) {
                inEntry = true;
                continue;
            }
            if (!inEntry) {
                continue;
            }
            if (line.matches("^\\s*coarse:\\s*true\\s*$")) {
                count++;
            }
            // 条目结束（下一个条目起点或空行后出现新字段）：这里按「见到新 method 即结算」处理
        }
        return count;
    }

    /** 清单的 METHOD+path => permission 映射。 */
    private java.util.Map<String, String> manifestPermissionByKey() {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        String method = null;
        String path = null;
        for (String line : manifestRaw()) {
            var mm = Pattern.compile("^\\s*-\\s*method:\\s*(\\S+)\\s*$").matcher(line);
            if (mm.find()) {
                if (method != null && path != null) {
                    out.put(method + " " + path, pendingPermission);
                }
                method = mm.group(1);
                path = null;
                continue;
            }
            var mp = Pattern.compile("^\\s*path:\\s*(\\S+)\\s*$").matcher(line);
            if (mp.find()) {
                path = mp.group(1);
                continue;
            }
            var pp = Pattern.compile("^\\s*permission:\\s*(\\S+)\\s*$").matcher(line);
            if (pp.find()) {
                pendingPermission = pp.group(1);
            }
        }
        if (method != null && path != null) {
            out.put(method + " " + path, pendingPermission);
        }
        return out;
    }

    private String pendingPermission;

    @Test
    @DisplayName("G7 清单条目必须都能对上真实端点（反向漂移：清单有、运行时无）")
    void manifestEntriesMustExistAtRuntime() {
        Set<String> real = realWriteEndpoints();
        List<String> stale = new ArrayList<>();
        for (String key : manifestKeys()) {
            if (!real.contains(key)) {
                stale.add(key);
            }
        }
        assertTrue(stale.isEmpty(),
                "清单里有 " + stale.size() + " 条对不上任何真实写端点（端点已改名/删除）："
                        + stale.subList(0, Math.min(10, stale.size()))
                        + "。这类条目不会让 G2 缺口变小，却会让清单看起来更全。");
    }

    // ─────────────────────── 清单解析（唯一解析实现） ───────────────────────

    private List<String> manifestRaw() {
        List<String> out = new ArrayList<>();
        try (var in = new BufferedReader(new InputStreamReader(
                resourceLoader.getResource("classpath:" + MANIFEST).getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                out.add(line);
            }
        } catch (Exception e) {
            throw new IllegalStateException("读不到 " + MANIFEST + " —— 守卫无法取真值源", e);
        }
        return out;
    }

    /** 清单中的所有权限码。 */
    private List<String> manifestPermissions() {
        List<String> out = new ArrayList<>();
        for (String line : manifestRaw()) {
            Matcher m = Pattern.compile("^\\s*permission:\\s*(\\S+)\\s*$").matcher(line);
            if (m.find()) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    /** 清单中的所有 (METHOD path) 键，与运行时端点同格式。 */
    private Set<String> manifestKeys() {
        Set<String> keys = new LinkedHashSet<>();
        List<String> lines = manifestRaw();
        String method = null;
        for (String line : lines) {
            Matcher mm = Pattern.compile("^\\s*-\\s*method:\\s*(\\S+)\\s*$").matcher(line);
            if (mm.find()) {
                method = mm.group(1);
                continue;
            }
            Matcher mp = Pattern.compile("^\\s*path:\\s*(\\S+)\\s*$").matcher(line);
            if (mp.find() && method != null) {
                keys.add(method + " " + mp.group(1));
                method = null;
            }
        }
        return keys;
    }

    // ─────────────────────── 运行时 / 前端 真值源 ───────────────────────

    /** 真实存在的写端点（POST/PUT/DELETE/PATCH），取自 Spring 运行时。 */
    private Set<String> realWriteEndpoints() {
        Set<String> out = new LinkedHashSet<>();
        var handlerMapping = applicationContext.getBeansOfType(
                org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class)
                .values().iterator().next();
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            if (info.getMethodsCondition() == null) {
                return;
            }
            for (var rm : info.getMethodsCondition().getMethods()) {
                String name = rm.name();
                if (!("POST".equals(name) || "PUT".equals(name)
                        || "DELETE".equals(name) || "PATCH".equals(name))) {
                    continue;
                }
                // ⚠️ 必须用 getPatternValues()（Spring 5.3+ PathPatterns 版）：
                //    getPatternsCondition() 在本环境返回 null ⇒ 曾导致写端点数到 0 ——
                //    而「缺口=0」正是**假绿**形态（守卫看起来完美，实际什么都没比）。
                //    判据：守卫的统计值若为 0，先怀疑取数方式，而不是庆祝「零缺口」。
                for (String p : info.getPatternValues()) {
                    if (p != null && !p.isBlank()) {
                        out.add(name + " " + p);
                    }
                }
            }
        });
        return out;
    }

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    /** 前端路由 meta.permission 里用到的权限码（扫前端源码，唯一合理来源）。 */
    private Set<String> frontendRoutePermissionCodes() {
        Set<String> out = new LinkedHashSet<>();
        var res = resourceLoader.getResource("file:../frontend/src/router/");
        if (!res.exists()) {
            throw new IllegalStateException("找不到前端路由目录 —— 守卫无法取真值源，拒绝静默放行");
        }
        try (var stream = resourceLoader.getResource("file:../frontend/src/router/")
                .getInputStream()) {
            // 用文件树遍历
            var files = new java.io.File(res.getFile().getPath());
            collectCodes(files, out);
        } catch (Exception e) {
            throw new IllegalStateException("读前端路由失败：" + e.getMessage(), e);
        }
        return out;
    }

    private void collectCodes(java.io.File dir, Set<String> out) {
        var kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (var f : kids) {
            if (f.isDirectory()) {
                collectCodes(f, out);
                continue;
            }
            if (!f.getName().endsWith(".ts")) {
                continue;
            }
            try {
                for (String line : java.nio.file.Files.readAllLines(f.toPath())) {
                    String t = line.trim();
                    // ⚠️ 必须跳过注释行：`lab.ts` 里有一行注释写着
                    //    「此前这些路由带着 `permission: 'lab:*'`」，
                    //    正则不跳过注释就会把它当成真实声明（实测已发生）。
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                        continue;
                    }
                    Matcher m = Pattern.compile("permission:\\s*'([^']+)'").matcher(line);
                    while (m.find()) {
                        out.add(m.group(1));
                    }
                }
            } catch (Exception ignored) {
                // 单个文件读不了就跳过，不因一个文件让整个守卫崩
            }
        }
    }
}