package com.huicai.security;

import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P153 A-2：🔴 全仓机械守卫 —— <b>Controller 直调「RLS 表的 Mapper」必须在事务内</b>。
 *
 * <h2>缺陷机理（与 {@link RlsTransactionStructureTest} 同根，覆盖面补盲）</h2>
 * <pre>
 * TenantGucSetter#apply:
 *     if (enterpriseId == null || !isActualTransactionActive()) return;   ← 无事务直接返回
 * RLS 谓词：enterprise_id = NULLIF(current_setting('app.enterprise_id', true),'')::bigint
 * </pre>
 * 既有守卫只扫 <b>Service</b>；而 Controller 直注 Mapper 的 7 个类（绕过 Service 层）
 * 完全在其点切之外。SPEC-P153 取证实测 <b>4 个类全部真实故障</b>：
 * <ul>
 *   <li>{@code BusinessDocController#templates} → []，DB 有 18 个模板
 *       （同表对照：走 Service 的 /voucher-templates/all 返 18 行）</li>
 *   <li>{@code ReconciliationReportController} 全端点 → total=0，DB ent1 有 20 条单据</li>
 *   <li>{@code EnterpriseReadinessController} → 就绪度对<b>所有企业恒报「未配置」</b>
 *       （count 恒 0，且被 catch(Exception){false} 双保险掩盖）</li>
 *   <li>{@code VoucherTemplateController#enrichLines} → 分录行科目代码/名称恒 null
 *       （service 事务<b>之外</b>直调 subjectMapper，DB ent1 有 92 科目）</li>
 * </ul>
 *
 * <h2>为什么 RLS 表集合从真库拿而不是硬编码</h2>
 * 同 {@code RoutePermissionGhostCodeRealDBTest} 的先例：真值在数据库（V106 起
 * pg_policies），硬编码清单在策略变更时必然失真。Mapper→表映射走
 * {@code BaseMapper<Entity>} 泛型 + 实体 {@code @TableName}，同样取源码真值。
 *
 * <h2>已知局限（必须如实知道）</h2>
 * 只识别「方法体直接出现 {@code rlsMapperField.}」以及一层显式方法调用转递
 * （如 enrichLines）。经第三方工具类间接访问 Mapper 的写法不在识别面内。
 */
@DisplayName("SPEC-P153：Controller 直调 RLS 表 Mapper 必须在事务内（否则 GUC 不设 ⇒ 整表被过滤）")
class ControllerRlsTransactionGuardRealDBTest extends AbstractMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Path SRC = Path.of("src/main/java");

    /**
     * 豁免清单（类名 → 为什么直调不需要事务）。
     * 🔴 只允许缩小不允许扩大（§4.5 第 43 条）；每条必须写明理由。
     */
    private static final Map<String, String> EXEMPT = new LinkedHashMap<>();

    // ───────────────────────────── 真值装载 ─────────────────────────────

    /** 真库 RLS 表全集（enterprise_policy 挂载的表）。 */
    private Set<String> rlsTables() {
        Set<String> s = new HashSet<>();
        jdbcTemplate.queryForList(
                "SELECT DISTINCT tablename FROM pg_policies WHERE policyname = 'enterprise_policy'",
                String.class).forEach(s::add);
        assertFalse(s.isEmpty(), "pg_policies 未读到任何 enterprise_policy —— 真值装载失效，守卫必须报警而不是恒绿");
        return s;
    }

    /** 解析全仓 *Mapper.java：Mapper 简单名 → @TableName 表名（解析不出的不入表）。 */
    private Map<String, String> mapperToTable(Map<String, String> entityTable) throws IOException {
        Map<String, String> mapperEntity = new HashMap<>();
        Pattern iface = Pattern.compile(
                "interface\\s+(\\w+Mapper)[\\s\\S]{0,300}?extends\\s+BaseMapper<\\s*(\\w+)\\s*>");
        for (Path p : javaFilesUnder(SRC)) {
            if (!p.getFileName().toString().endsWith("Mapper.java")) {
                continue;
            }
            String src = stripComments(Files.readString(p, StandardCharsets.UTF_8));
            Matcher m = iface.matcher(src);
            while (m.find()) {
                mapperEntity.put(m.group(1), m.group(2));
            }
        }
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> e : mapperEntity.entrySet()) {
            String table = entityTable.get(e.getValue());
            if (table != null) {
                out.put(e.getKey(), table);
            }
        }
        return out;
    }

    /** 解析全仓实体：类简单名 → @TableName("t_xxx")。 */
    private Map<String, String> entityTable() throws IOException {
        Map<String, String> map = new HashMap<>();
        Pattern tn = Pattern.compile("@TableName\\(\"(\\w+)\"\\)");
        for (Path p : javaFilesUnder(SRC)) {
            String name = p.getFileName().toString();
            if (!name.endsWith(".java")) {
                continue;
            }
            String src = stripComments(Files.readString(p, StandardCharsets.UTF_8));
            Matcher m = tn.matcher(src);
            if (m.find()) {
                map.put(name.replace(".java", ""), m.group(1));
            }
        }
        return map;
    }

    // ───────────────────────────── 守卫主判据 ─────────────────────────────

    @Test
    @DisplayName("Controller 中触 RLS 表 Mapper 的端点方法必须被 @Transactional 覆盖")
    void controllerDirectMapperCallsMustBeTransactional() throws IOException {
        Set<String> rlsTables = rlsTables();
        Map<String, String> m2t = mapperToTable(entityTable());

        List<String> violations = new ArrayList<>();
        int rlsTouchingControllers = 0;

        for (Path p : javaFilesUnder(SRC)) {
            String cls = p.getFileName().toString().replace(".java", "");
            if (!cls.endsWith("Controller")) {
                continue;
            }
            String src = stripComments(Files.readString(p, StandardCharsets.UTF_8));

            // 字段：private [final] XxxMapper xxxMapper;
            Map<String, String> rlsFields = new HashMap<>();  // fieldName -> table
            Matcher fm = Pattern.compile("(?:private|protected)\\s+(?:final\\s+)?(\\w+Mapper)\\s+(\\w+)\\s*;")
                    .matcher(src);
            while (fm.find()) {
                String table = m2t.get(fm.group(1));
                if (table != null && rlsTables.contains(table)) {
                    rlsFields.put(fm.group(2), table);
                }
            }
            if (rlsFields.isEmpty()) {
                continue;   // 无 RLS Mapper 字段（含仅注入非 RLS Mapper 的 AuthController 等）
            }
            if (EXEMPT.containsKey(cls)) {
                continue;
            }
            rlsTouchingControllers++;

            boolean classTx = src.contains("@Transactional")
                    && src.indexOf("@Transactional") < src.indexOf("class " + cls);

            List<MethodInfo> methods = parseMethods(src);
            // 一层转递标记：直接调用 rlsField. 的方法
            Set<String> touching = new HashSet<>();
            for (MethodInfo mi : methods) {
                if (touchesRlsField(mi.body, rlsFields.keySet())) {
                    touching.add(mi.name);
                }
            }
            // 迭代转递（私有 helper 被端点调用的形态）
            boolean grown = true;
            while (grown) {
                grown = false;
                for (MethodInfo mi : methods) {
                    if (touching.contains(mi.name)) {
                        continue;
                    }
                    for (String t : touching) {
                        if (mi.body.matches("(?s).*\\b" + Pattern.quote(t) + "\\s*\\(.*")) {
                            touching.add(mi.name);
                            grown = true;
                            break;
                        }
                    }
                }
            }

            for (MethodInfo mi : methods) {
                if (!mi.isEndpoint || !touching.contains(mi.name)) {
                    continue;
                }
                if (classTx || mi.hasTransactional) {
                    continue;
                }
                violations.add(cls + "#" + mi.name + " 直调 RLS 表 "
                        + String.join("/", new HashSet<>(rlsFields.values()))
                        + " 但无事务 ⇒ GUC 恒 NULL ⇒ RLS 谓词滤掉整表（症状: 恒空/恒 0/false，不报错）");
            }
        }

        assertTrue(violations.isEmpty(),
                "Controller 直调 RLS 表 Mapper 缺事务。修法（两次实测唯一有效位）：事务挂 Controller ——\n"
                        + "读端点 @Transactional(readOnly = true, rollbackFor = Exception.class)；\n"
                        + "读+写混合端点用非 readOnly（readOnly 会禁掉内部 service 的写）。\n"
                        + "违规（" + violations.size() + "）：\n  " + String.join("\n  ", violations)
                        + "\n（触 RLS Mapper 的 Controller 共 " + rlsTouchingControllers + " 个，"
                        + "豁免 " + EXEMPT.size() + " 个）");
    }

    // ───────────────────────────── 守卫自证（防恒绿） ─────────────────────────────

    @Test
    @DisplayName("🔴 判定逻辑自证：坏片段必须判红、好片段必须判绿、helper 转递必须识别")
    void guardLogicSelfProof() {
        String bad = """
                @RestController
                public class ProbeController {
                    private final FooMapper fooMapper;
                    @GetMapping
                    public R<?> list() { return R.ok(fooMapper.selectList(null)); }
                }
                """;
        assertFalse(judgeProbe(bad, "list"), "无事务直调 mapper 的端点必须判红（否则守卫恒绿）");

        String good = """
                @RestController
                @Transactional(readOnly = true)
                public class ProbeController2 {
                    private final FooMapper fooMapper;
                    @GetMapping
                    public R<?> list() { return R.ok(fooMapper.selectList(null)); }
                }
                """;
        assertTrue(judgeProbe(good, "list"), "类级事务必须被识别（否则误报全部）");

        String goodMethod = """
                @RestController
                public class ProbeController3 {
                    private final FooMapper fooMapper;
                    @Transactional(rollbackFor = Exception.class)
                    @GetMapping
                    public R<?> list() { return R.ok(fooMapper.selectList(null)); }
                }
                """;
        assertTrue(judgeProbe(goodMethod, "list"), "方法级事务必须被识别");

        String helper = """
                @RestController
                public class ProbeController4 {
                    private final FooMapper fooMapper;
                    @GetMapping
                    public R<?> list() { return R.ok(enrich()); }
                    private Object enrich() { return fooMapper.selectById(1L); }
                }
                """;
        assertFalse(judgeProbe(helper, "list"),
                "端点经私有 helper 转递直调 mapper 也必须判红（VoucherTemplateController#enrichLines 形态）");

        String nonRlsFieldIgnored = """
                @RestController
                public class ProbeController5 {
                    private final MenuMapper menuMapper;
                    @GetMapping
                    public R<?> list() { return R.ok(menuMapper.selectList(null)); }
                }
                """;
        // menuMapper 不在 rlsFields（探针里只把 fooMapper 当 RLS），应视为无 RLS 直调 ⇒ 不判红
        assertTrue(judgeProbeNonRls(nonRlsFieldIgnored), "非 RLS Mapper 字段不得触发判定");
    }

    // ───────────────────────────── 解析实现 ─────────────────────────────

    private record MethodInfo(String name, String body, boolean hasTransactional, boolean isEndpoint) {}

    /** 对探针源码判定：方法 {@code name} 在「仅 FooMapper 算 RLS」口径下是否被事务覆盖。 */
    private boolean judgeProbe(String src, String name) {
        boolean classTx = src.contains("@Transactional") && src.indexOf("@Transactional") < src.indexOf("class ");
        for (MethodInfo mi : parseMethods(src)) {
            if (mi.name.equals(name)) {
                boolean touching = touchesRlsField(mi.body, Set.of("fooMapper"));
                if (!touching) {
                    // helper 转递
                    for (MethodInfo other : parseMethods(src)) {
                        if (touchesRlsField(other.body, Set.of("fooMapper"))
                                && mi.body.matches("(?s).*\\b" + Pattern.quote(other.name) + "\\s*\\(.*")) {
                            touching = true;
                            break;
                        }
                    }
                }
                if (!touching) {
                    return true; // 不触 RLS ⇒ 无需事务
                }
                return classTx || mi.hasTransactional;
            }
        }
        return true;
    }

    private boolean judgeProbeNonRls(String src) {
        for (MethodInfo mi : parseMethods(src)) {
            if (touchesRlsField(mi.body, Set.of("fooMapper"))) {
                return false;
            }
        }
        return true;
    }

    private boolean touchesRlsField(String body, Set<String> fieldNames) {
        for (String f : fieldNames) {
            if (body.matches("(?s).*\\b" + Pattern.quote(f) + "\\s*\\..*")) {
                return true;
            }
        }
        return false;
    }

    /** 顶层方法切分：签名 + 花括号配平的 body + 签名上方注解区。 */
    private List<MethodInfo> parseMethods(String src) {
        List<MethodInfo> out = new ArrayList<>();
        Matcher sm = Pattern.compile("(?m)^\\s{4}(public|private|protected)\\s+[\\w<>?,.\\[\\]\\s]+?\\s(\\w+)\\s*\\(")
                .matcher(src);
        while (sm.find()) {
            String name = sm.group(2);
            if (name.equals("if") || name.equals("for") || name.equals("while") || name.equals("switch")) {
                continue;
            }
            int braceStart = src.indexOf('{', sm.end());
            if (braceStart < 0) {
                continue;
            }
            int end = matchBrace(src, braceStart);
            if (end < 0) {
                continue;
            }
            String body = src.substring(braceStart, end + 1);
            // 签名之前的注解区：向上收集连续的 @ 行（跨 20 行足够容纳中文长注释）
            String before = src.substring(0, sm.start());
            String[] lines = before.split("\n", -1);
            boolean tx = false;
            boolean endpoint = false;
            for (int i = lines.length - 1; i >= 0; i--) {
                String t = lines[i].trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (t.startsWith("@")) {
                    if (t.startsWith("@Transactional")) {
                        tx = true;
                    }
                    if (t.startsWith("@Get") || t.startsWith("@Post") || t.startsWith("@Put")
                            || t.startsWith("@Delete") || t.startsWith("@Patch") || t.startsWith("@RequestMapping")) {
                        endpoint = true;
                    }
                    continue;
                }
                // 允许隔着注释行到达注解区
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                    continue;
                }
                break;
            }
            out.add(new MethodInfo(name, body, tx, endpoint));
        }
        return out;
    }

    /** 从 openIdx 的 '{' 找配平闭括号下标；找不到返回 -1。 */
    private int matchBrace(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private List<Path> javaFilesUnder(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
