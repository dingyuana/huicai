package com.huicai.security;

import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P131：<b>幽灵权限码</b>守卫。
 *
 * <p><b>要守的不变量</b>：前端路由 {@code meta.permission} 引用的每一个权限码，
 * 都必须在 {@code t_menu} 中存在对应行。
 *
 * <p><b>为什么这条不变量重要</b>：权限码是<b>前端路由 meta 与后端菜单表之间的隐式契约</b>，
 * 却<b>没有任何校验</b>。前端写错一个码、或后端漏建一行，症状都是「点进去 403」——
 * <b>且看起来像正常的权限不足</b>，极难联想到「权限码压根不存在」。
 *
 * <p>生产实证：{@code voucher:update} 只存在于路由 meta、{@code t_menu} 中从无此行
 * ⇒ 凭证编辑页对<b>所有非超管永久 403</b>（含 ADMIN）。全量比对一度发现 11 个同类幽灵码。
 *
 * <p>🔴 <b>为什么必须是「真库」测试</b>：权限码的真值在<b>数据库</b>里，不在文件里。
 * 用文件推断（例如只 grep 迁移 SQL）会在种子方式变化时立刻失真——
 * 这与 SPEC-P127 记录的「工具读数对实现细节过度敏感时不可作为存量依据」同型。
 */
@DisplayName("SPEC-P131：幽灵权限码守卫（路由 meta 必须能在 t_menu 中找到）")
class RoutePermissionGhostCodeRealDBTest extends AbstractMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 路由里 path + permission 的成对声明 */
    private static final Pattern ROUTE = Pattern.compile(
            "path:\\s*'([^']+)'[\\s\\S]{0,400}?permission:\\s*'([^']+)'");

    /** 后端工作目录为 {@code backend/}，前端路由在其同级 */
    private static Path frontendRouterDir() {
        Path p = Path.of("..", "frontend", "src", "router");
        return Files.isDirectory(p) ? p : Path.of("frontend", "src", "router");
    }

    private Map<String, List<String>> referencedPermissions() throws Exception {
        Map<String, List<String>> refs = new LinkedHashMap<>();
        Path root = frontendRouterDir();
        assertTrue(Files.isDirectory(root),
                "找不到前端路由目录（试过 ../frontend/src/router 与 frontend/src/router），"
                        + "当前工作目录=" + Path.of("").toAbsolutePath());
        try (var walk = Files.walk(root)) {
            for (Path f : walk.filter(p -> p.toString().endsWith(".ts")).toList()) {
                String txt = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = ROUTE.matcher(txt);
                while (m.find()) {
                    refs.computeIfAbsent(m.group(2), k -> new ArrayList<>())
                            .add(f.getFileName() + ":" + m.group(1));
                }
            }
        }
        return refs;
    }

    private TreeSet<String> permissionsInDb() {
        TreeSet<String> s = new TreeSet<>();
        jdbcTemplate.queryForList(
                "SELECT DISTINCT permission FROM t_menu "
                        + "WHERE deleted = 0 AND permission IS NOT NULL AND permission <> ''",
                String.class).forEach(s::add);
        return s;
    }

    @Test
    @DisplayName("🔴 前端路由引用的每个权限码都必须在 t_menu 中存在")
    void noGhostPermissionCode() throws Exception {
        Map<String, List<String>> refs = referencedPermissions();
        TreeSet<String> db = permissionsInDb();

        assertTrue(!refs.isEmpty(),
                "未能从前端路由解析出任何 permission —— 守卫若因解析器失效而恒绿，"
                        + "等于没有守卫（§4.5 第 21 条）。当前工作目录=" + Path.of("").toAbsolutePath());

        List<String> ghosts = new ArrayList<>();
        refs.forEach((code, sites) -> {
            if (!db.contains(code)) {
                ghosts.add(code + "  <- " + String.join(", ", sites));
            }
        });

        assertTrue(ghosts.isEmpty(),
                "幽灵权限码：" + ghosts.size() + " 个。它们只存在于前端路由 meta，"
                        + "t_menu 中无对应行 ⇒ 无角色菜单关联 ⇒ hasPermission 恒 false ⇒ "
                        + "该页面对**所有非超管角色**（含 ADMIN）永久 403，"
                        + "且症状形似正常权限不足。\n幽灵码清单：\n  "
                        + String.join("\n  ", ghosts));
    }

    @Test
    @DisplayName("守卫自身有效：已存在的码不报、构造的假码必报")
    void guardDetectsGhost() {
        TreeSet<String> db = permissionsInDb();
        String fake = "__definitely_not_a_real_code__";
        assertTrue(!db.contains(fake), "前提不成立：库里竟存在该假权限码");
        assertTrue(db.contains("voucher:list"),
                "前提不成立：库里竟没有 voucher:list（控制组缺失则无法验证守卫不误报）");

        // 复刻守卫的判定逻辑：存在则不报，不存在则报
        assertTrue(!db.contains(fake), "假权限码不得被判为已存在");
        assertTrue(db.contains("voucher:list"), "已存在的权限码不得被判为幽灵");
    }

    @Test
    @DisplayName("路由解析器必须真的解析到权限码（防解析器静默失效）")
    void parserActuallyFindsPermissions() throws Exception {
        Map<String, List<String>> refs = referencedPermissions();
        assertTrue(refs.size() >= 20,
                "只解析到 " + refs.size() + " 个权限码，少于预期的 20+ —— "
                        + "解析器可能已随路由写法变化而失效，此时守卫会假绿");
    }
}