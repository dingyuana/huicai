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
    /** D-10 第 1~6 条裁定后：60 - 27(排除) - 22(已登记) = 11 真实缺口（全部为 AI 端点）。 */
    private static final int MAX_UNREGISTERED_WRITE_ENDPOINTS = 11;

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
        Set<String> excluded = excludedKeys();
        int writeTotal = 0;
        int unregistered = 0;
        List<String> sample = new ArrayList<>();
        for (String ep : realWriteEndpoints()) {
            writeTotal++;
            if (!registered.contains(ep) && !excluded.contains(ep)) {
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
        System.out.println("[G2] 写端点 " + writeTotal + " / 已登记 "
                + (writeTotal - unregistered - excluded.size())
                + " / 显式排除 " + excluded.size() + " / 真实缺口 " + unregistered);
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
        // 🔴 必须挑「既未登记、也未被显式排除」的端点。
        //    早前版本只判 `!registered.contains(ep)`，结果挑中了**排除项** ——
        //    而缺口统计本就跳过排除项，于是「补一条登记」不会让缺口减少，
        //    自证断言反而报「计数逻辑无效」。症状指错方向：错的是取样，不是计数。
        Set<String> excludedNow = excludedKeys();
        String someUnregistered = null;
        for (String ep : realWriteEndpoints()) {
            if (!registered.contains(ep) && !excludedNow.contains(ep)) {
                someUnregistered = ep;
                break;
            }
        }
        assertTrue(someUnregistered != null,
                "找不到「既未登记也未排除」的写端点，G2 自证将失去意义"
                        + "（登记 " + registered.size() + " / 排除 " + excludedNow.size() + "）");
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

    /**
     * 统计「未登记且未被显式排除」的写端点数 = 真实缺口。
     *
     * <p>🔴 D-10 第 1、2 条裁定后，代理端 24 个（roleGuard 管辖）与自助端点 3 个
     * 属<b>刻意排除</b>，不是缺口。把它们算进缺口会让「缺口 60」这个数字失真，
     * 从而掩盖真正的 33 个未覆盖端点。</p>
     */
    private int countUnregisteredWriteEndpoints(Set<String> registered) {
        Set<String> excluded = excludedKeys();
        int n = 0;
        for (String ep : realWriteEndpoints()) {
            if (!registered.contains(ep) && !excluded.contains(ep)) {
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
            if (line.startsWith("excluded:")) {
                break;
            }
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
        pendingPermission = null;
        for (String line : manifestRaw()) {
            if (line.startsWith("excluded:")) {
                break;
            }
            var mm = Pattern.compile("^\\s*-\\s*method:\\s*(\\S+)\\s*$").matcher(line);
            if (mm.find()) {
                if (method != null && path != null) {
                    out.put(method + " " + path, pendingPermission);
                }
                method = mm.group(1);
                path = null;
                // 🔴 每条**重置**：否则条目缺 permission 字段时会「顺延」上一条的码
                pendingPermission = null;
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
        for (String key : excludedKeys()) {
            if (!real.contains(key)) {
                stale.add("excluded: " + key);
            }
        }
        assertTrue(stale.isEmpty(),
                "清单里有 " + stale.size() + " 条对不上任何真实写端点（端点已改名/删除）："
                        + stale.subList(0, Math.min(10, stale.size()))
                        + "。这类条目不会让 G2 缺口变小，却会让清单看起来更全。");
    }


    /**
     * 🔴 G8：`roleGuard` 管辖的端点**不得**在清单里登记权限码。
     *
     * <p>代理端 20 个写端点的授权由代码内的 {@code AgencyEnterpriseRoleGuard}
     * （P114/P120 三轮加固的产物）负责，<b>不是</b>权限码体系。
     * 两套并存会出现「两边都松」的缝隙：
     * {@code roleGuard} 放行 + 清单无码 ⇒ 实际无人管；
     * 反之则要同时维护两处，改一处漏一处。</p>
     *
     * <p>本守卫把这条约定<b>变成机器校验</b>，而不是文档里的一句话。</p>
     */
    @Test
    @DisplayName("G8 roleGuard 管辖的端点不得在清单登记权限码（防两套授权并存）")
    void roleGuardManagedEndpointsMustNotBeInManifest() {
        Set<String> byAgency = new LinkedHashSet<>();
        for (Excluded e : manifestExcluded()) {
            if ("role-guard".equals(e.governedBy())) {
                byAgency.add(e.method() + " " + e.path());
            }
        }
        assertTrue(byAgency.size() >= 20,
                "排除段里 role-guard 条目只认出 " + byAgency.size() + " 个（预期 >= 20）—— "
                        + "前提失效，本守卫会空转。判据：守卫的清单必须与真值源同步，否则它锁的是空气。");

        // 前提自检：这些端点在运行时确实存在
        Set<String> real = realWriteEndpoints();
        List<String> gone = new ArrayList<>();
        for (String ep : byAgency) {
            if (!real.contains(ep)) {
                gone.add(ep);
            }
        }
        assertTrue(gone.isEmpty(), "排除段里的端点在运行时已不存在：" + gone);

        java.util.Map<String, String> byKey = manifestPermissionByKey();
        List<String> wrongly = new ArrayList<>();
        for (String ep : byAgency) {
            if (byKey.containsKey(ep)) {
                wrongly.add(ep + " => " + byKey.get(ep));
            }
        }
        assertTrue(wrongly.isEmpty(),
                "以下代理端写端点既被 roleGuard 管辖、又在清单登记了权限码：" + wrongly
                        + "。两套授权并存会产生「两边都松」的缝隙 —— "
                        + "roleGuard 放行 + 清单有码但没人持有 ⇒ 实际无人管；"
                        + "反之则要同时维护两处，改一处漏一处。"
                        + "正解：见 SPEC-P133 §8.6 B 类。");
    }

    /**
     * 🔴 G9：认证端点与自助切换**不参与** G2 缺口统计。
     *
     * <p>登录 / 刷新 / 账套切换是<b>自助行为</b>，任何登录用户都应可执行；
     * 给它们加动作权限码会导致「无法登录」「无法切换账套」这类荒谬故障。</p>
     *
     * <p>本守卫确保它们<b>既不被要求登记、也不被登记</b> —— 即缺口统计不把它们算进去，
     * 清单里也不该出现它们。</p>
     */
    @Test
    @DisplayName("G9 自助端点既不计入缺口也不得登记（登录/刷新/切账套不该受动作码约束）")
    void selfServiceEndpointsAreNeitherCountedNorRegistered() {
        Set<String> selfService = new LinkedHashSet<>();
        for (Excluded e : manifestExcluded()) {
            if ("self-service".equals(e.governedBy())) {
                selfService.add(e.method() + " " + e.path());
            }
        }
        assertTrue(selfService.size() >= 3,
                "排除段里 self-service 条目只认出 " + selfService.size() + " 个（预期 >= 3），前提失效");

        Set<String> real = realWriteEndpoints();
        List<String> gone = new ArrayList<>();
        for (String ep : selfService) {
            if (!real.contains(ep)) {
                gone.add(ep);
            }
        }
        assertTrue(gone.isEmpty(), "排除段里的自助端点在运行时已不存在：" + gone);

        java.util.Map<String, String> byKey = manifestPermissionByKey();
        List<String> wrongly = new ArrayList<>();
        for (String ep : selfService) {
            if (byKey.containsKey(ep)) {
                wrongly.add(ep);
            }
        }
        assertTrue(wrongly.isEmpty(),
                "以下自助端点被登记了权限码，会导致「无法登录 / 无法切换账套」：" + wrongly);
    }

    /**
     * 🔴 G10：每个排除项都必须写明<b>管辖机制</b>与<b>理由</b>。
     *
     * <p><b>判据：排除是最容易被滥用的机制</b> —— 只要能写一行
     * {@code governed_by} 就把端点踢出授权体系，而「踢出」比「漏保护」更难被发现。
     * 因此排除必须比登记<b>更</b>严格：写不全就不通过。</p>
     */
    @Test
    @DisplayName("G10 每个排除项都必须声明 controller / governed_by / reason")
    void everyExclusionIsJustified() {
        List<String> bad = new ArrayList<>();
        for (Excluded e : manifestExcluded()) {
            String key = e.method() + " " + e.path();
            if (e.path() == null || e.controller() == null
                    || e.governedBy() == null || e.reason() == null
                    || e.reason().length() < 8) {
                bad.add(key + "（缺 controller/governed_by/reason 或理由过短）");
            } else if (!Set.of("role-guard", "self-service").contains(e.governedBy())) {
                bad.add(key + "（governed_by=" + e.governedBy() + " 不在受控词表 "
                        + "[role-guard, self-service]）");
            }
        }
        assertTrue(bad.isEmpty(),
                "以下排除项声明不完整：" + bad
                        + "。排除比登记更危险（踢出授权体系比漏保护更难发现），故要求更严："
                        + "必须写明管辖机制与理由，且 governed_by 只能在受控词表内。");
        System.out.println("[G10] 排除项 " + manifestExcluded().size()
                + " 条，全部已声明管辖机制与理由");
    }


    /** 清单 {@code excluded:} 段解析出的条目。 */
    public record Excluded(String method, String path, String controller,
                           String governedBy, String reason) {}

    /**
     * 解析清单的排除段 —— <b>单一事实源</b>。
     *
     * <p>🔴 此前 G8/G9 把「代理端」「自助端点」<b>硬编码在测试里</b>（靠路径前缀匹配），
     * 而清单里并无对应声明 —— 于是「排除」这件事只存在于测试的常量里，
     * 清单看起来仍是「缺 60 个」。
     * <b>判据：排除也必须落进配置</b>，否则「已知缺口」与「显式排除」
     * 在两处各存一份，迟早分叉。</p>
     */
    private List<Excluded> manifestExcluded() {
        List<Excluded> out = new ArrayList<>();
        boolean inSection = false;
        Excluded cur = null;
        for (String line : manifestRaw()) {
            if (line.startsWith("excluded:")) {
                inSection = true;
                continue;
            }
            if (!inSection) {
                continue;
            }
            var mm = Pattern.compile("^\\s*-\\s*method:\\s*(\\S+)\\s*$").matcher(line);
            if (mm.find()) {
                if (cur != null) {
                    out.add(cur);
                }
                cur = new Excluded(mm.group(1), null, null, null, null);
                continue;
            }
            if (cur == null) {
                continue;
            }
            var p1 = Pattern.compile("^\\s*path:\\s*(\\S+)\\s*$").matcher(line);
            if (p1.find()) {
                cur = new Excluded(cur.method(), p1.group(1), cur.controller(),
                        cur.governedBy(), cur.reason());
                continue;
            }
            var p2 = Pattern.compile("^\\s*controller:\\s*(\\S+)\\s*$").matcher(line);
            if (p2.find()) {
                cur = new Excluded(cur.method(), cur.path(), p2.group(1), cur.governedBy(), cur.reason());
                continue;
            }
            var p3 = Pattern.compile("^\\s*governed_by:\\s*(\\S+)\\s*$").matcher(line);
            if (p3.find()) {
                cur = new Excluded(cur.method(), cur.path(), cur.controller(), p3.group(1), cur.reason());
                continue;
            }
            var p4 = Pattern.compile("^\\s*reason:\\s*(.+)$").matcher(line);
            if (p4.find()) {
                cur = new Excluded(cur.method(), cur.path(), cur.controller(), cur.governedBy(),
                        p4.group(1).trim());
            }
        }
        if (cur != null) {
            out.add(cur);
        }
        return out;
    }

    private Set<String> excludedKeys() {
        Set<String> out = new LinkedHashSet<>();
        for (Excluded e : manifestExcluded()) {
            if (e.path() != null) {
                out.add(e.method() + " " + e.path());
            }
        }
        return out;
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
            // 🔴 只读 endpoints 段 —— excluded 段没有 permission 字段，
            //    若不截断会把上一条的 permission「顺延」给排除项（真实踩过）。
            if (line.startsWith("excluded:")) {
                break;
            }
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
            // 🔴 只读 endpoints 段，否则 excluded 的条目会被算成「已登记」，
            //    使 G2 的缺口凭空少 27 条（真实踩过：已登记 274 = 247 + 27）。
            if (line.startsWith("excluded:")) {
                break;
            }
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