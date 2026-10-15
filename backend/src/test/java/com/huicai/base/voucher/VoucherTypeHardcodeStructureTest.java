package com.huicai.base.voucher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P128：凭证类型必须按业务编码 code 在当前企业范围内解析，
 * 禁止把 {@code t_voucher_type} 的主键 id 当作全局常量硬编码。
 *
 * <p><b>为什么需要结构守卫</b>：{@code t_voucher_type} 是<b>租户表</b>
 * （ENABLE + FORCE ROW LEVEL SECURITY，谓词 {@code enterprise_id = 当前企业}），
 * 而 {@code VoucherType} 类注释把 id 1~4 写成「与 t_voucher_type 表 ID 对应」的全局常量。
 * 两者<b>各自都正确，合起来才错</b>：企业 1 的 id 恰好是 1，所以功能一直正常；
 * 到了企业 2/3/4，{@code getById(1L)} 被 RLS 过滤返回 null ⇒ 抛「凭证类型不存在」。
 *
 * <p>🔴 本守卫判定的依据是<b>第二参数本身</b>，不是「有没有报错」——
 * 因为该缺陷在企业 1 上<b>永远不发作</b>，任何端到端断言都无法证伪它。
 *
 * <p><b>允许的写法</b>：{@code requireByCode("JZ")}（本企业按 code 取）。
 * <b>判红的写法</b>：字面量（{@code 1L}）、{@code VoucherType.*} 常量、
 * 以及任何把 id 存进局部常量再传进来的写法。
 */
@DisplayName("SPEC-P128：凭证类型按 code 解析，禁硬编码全局 id")
class VoucherTypeHardcodeStructureTest {

    private static final String METHOD = "generateNextNo";

    /** 唯一允许的第二参数形态：本企业按 code 取 */
    private static final String ALLOWED = "requireByCode(";

    /**
     * SPEC-P128 §7 已登记的<b>明确排除项</b>：这两处取的是「前端传入」或「库中已有」的
     * voucherTypeId，不属于「把全局 id 写死」，本批不改。
     * ⚠️ 该清单<b>只允许缩小不允许扩大</b> —— 扩大等于把爆炸半径藏起来。
     * <p>匹配键为 {@code 文件名:第二参数}（不含第一参数，避免随参数改名而失效）。
     */
    private static final List<String> DECLARED_EXCLUSIONS = List.of(
            "VoucherServiceImpl.java:dto.getVoucherTypeId()",
            "VoucherServiceImpl.java:original.getVoucherTypeId()"
    );

    @Test
    @DisplayName("generateNextNo 的第二参数必须是 requireByCode(...)，不得是字面量或 VoucherType 常量")
    void noHardcodedVoucherTypeId() throws Exception {
        List<String> violations = new ArrayList<>();

        for (String file : productionSources()) {
            String content = Files.readString(Path.of(file), StandardCharsets.UTF_8);
            for (Call c : callsIn(content)) {
                String arg = oneLine(c.secondArg);
                String site = simpleName(file) + ":" + c.line + ": " + oneLine(c.fullCall);
                if (isDeclaredExclusion(simpleName(file), arg)) {
                    continue;
                }
                // 局部变量（已在上游由 requireByCode 解析）与内联写法同样合法
                if (arg.contains(ALLOWED) || resolvedFromRequireByCode(content, arg)) {
                    continue;
                }
                violations.add(site);
            }
        }

        assertTrue(violations.isEmpty(),
                "凭证类型被硬编码为全局 id（SPEC-P128）：t_voucher_type 是租户表，"
                        + "id 1~4 只属于企业 1；其它企业下 getById 会被 RLS 过滤返 null，"
                        + "表现为「凭证类型不存在」。\n应改为 voucherTypeService.requireByCode(\"JZ\")。\n"
                        + "违规点（" + violations.size() + " 处）：\n  "
                        + String.join("\n  ", violations));
    }

    @Test
    @DisplayName("排除项清单不得被扩大（扩大等于把爆炸半径藏起来）")
    void exclusionsMustNotGrow() {
        assertEquals(2, DECLARED_EXCLUSIONS.size(),
                "SPEC-P128 §7 登记的排除项只有 VoucherServiceImpl 的 2 处。"
                        + "若新增排除项，必须先在 SPEC §7 写明理由 —— "
                        + "「先放过再补」会让本守卫退化成橡皮章。");
    }

    @Test
    @DisplayName("守卫自身有效：构造硬编码写法，规则必须命中；合法写法不得误报")
    void guardDetectsHardcodedId() {
        List<String> violations = new ArrayList<>();

        String bad1 = "voucherNoService.generateNextNo(period, 1L);";
        String bad2 = "voucherNoService.generateNextNo(period, VoucherType.ZZ);";
        String bad3 = "voucherNoService.generateNextNo(period, VOUCHER_TYPE_ID);";
        String good = "voucherNoService.generateNextNo(period, requireByCode(\"JZ\"));";

        for (String src : List.of(bad1, bad2, bad3, good)) {
            for (Call c : callsIn(src)) {
                if (!c.secondArg.contains(ALLOWED)) {
                    violations.add(oneLine(c.fullCall));
                }
            }
        }

        assertEquals(3, violations.size(),
                "规则必须命中 3 种硬编码形态（字面量 / VoucherType 常量 / 局部常量），"
                        + "且不得误报合法写法。实得：" + violations);
        assertTrue(!violations.contains(oneLine(good)),
                "合法写法 requireByCode(...) 被误判，守卫会造成噪音");
    }

    @Test
    @DisplayName("第二参数提取必须正确处理嵌套括号（括号配平错误会让规则静默失效）")
    void secondArgHandlesNestedParens() {
        String src = "voucherNoService.generateNextNo(period,\n"
                + "    voucherTypeService.requireByCode(\"JZ\"));";
        List<Call> calls = callsIn(src);
        assertEquals(1, calls.size(), "应识别出 1 处调用");
        assertTrue(calls.get(0).secondArg.contains("requireByCode"),
                "第二参数提取失败（嵌套括号未配平）：实际取到 [" + calls.get(0).secondArg + "]");
    }

    @Test
    @DisplayName("排除项匹配必须真的能匹配上（首版键写错导致排除项静默失效）")
    void declaredExclusionsActuallyMatch() throws Exception {
        for (String file : productionSources()) {
            if (!simpleName(file).equals("VoucherServiceImpl.java")) {
                continue;
            }
            String content = Files.readString(Path.of(file), StandardCharsets.UTF_8);
            for (Call c : callsIn(content)) {
                boolean excluded = isDeclaredExclusion(simpleName(file), c.secondArg);
                boolean isDeclared = c.secondArg.contains(".getVoucherTypeId()");
                if (isDeclared && !excluded) {
                    // 只在「确实取了库/前端 id」且「未被排除」时报错
                    if (!c.secondArg.contains("dto.") && !c.secondArg.contains("original.")) {
                        continue;
                    }
                }
                if (c.secondArg.contains("dto.getVoucherTypeId()")
                        || c.secondArg.contains("original.getVoucherTypeId()")) {
                    assertTrue(excluded,
                            "VoucherServiceImpl 第 " + c.line + " 行的 " + oneLine(c.secondArg)
                                    + " 是 SPEC-P128 §7 登记的排除项，却未被排除 —— "
                                    + "说明排除键与实际取到的第二参数对不上（排除项静默失效）");
                }
            }
        }
        // 反证：构造一个未登记的第二参数，必须不被排除
        assertTrue(!isDeclaredExclusion("VoucherServiceImpl.java", "dto.getSomethingElse()"),
                "未登记的第二参数被误排除，排除项清单形同万能豁免");
    }

    /**
     * setVoucherTypeId 的排除项：<b>实体/DTO 透传</b>，非硬编码全局 id。
     * ⚠️ 与 generateNextNo 的排除项<b>同源不同理</b>：那两处取的是「前端传入或库中已有」的
     * voucherTypeId（SPEC-P128 §7 已登记为需另行核实存量数据）；本处取的是
     * 「刚由 requireByCode 解析出来的本企业 id」或「同实体的原值」。
     */
    private static final List<String> SETTER_EXCLUSIONS = List.of(
            "VoucherServiceImpl.java:dto.getVoucherTypeId()",
            "VoucherServiceImpl.java:original.getVoucherTypeId()",
            "VoucherServiceImpl.java:entity.getVoucherTypeId()"
    );

    /**
     * 提取所有 {@code .setVoucherTypeId(...)} 的实参。
     *
     * <p>🔴 不可用正则 {@code \(([^)]*?)\)}：实参里常含嵌套调用
     * （{@code requireByCode("JZ").getId()}），非贪婪匹配会在第一个 {@code )} 处截断，
     * 得到 {@code voucherTypeService.requireByCode("JZ"} —— 于是**合法写法被判红、
     * 排除项也匹配不上**（首版正是栽在这里）。
     */
    private static List<Call> setterArgsIn(String src) {
        List<Call> calls = new ArrayList<>();
        String needle = ".setVoucherTypeId(";
        int from = 0;
        while (true) {
            int at = src.indexOf(needle, from);
            if (at < 0) {
                return calls;
            }
            int open = at + needle.length() - 1;
            int i = open + 1;
            int depth = 1;
            while (i < src.length() && depth > 0) {
                char ch = src.charAt(i);
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
                i++;
            }
            if (i >= src.length()) {
                from = open + 1;
                continue;
            }
            calls.add(new Call(
                    (int) src.substring(0, at).lines().count(),
                    src.substring(at, i + 1),
                    oneLine(src.substring(open + 1, i))));
            from = i + 1;
        }
    }

    /** 裸标识符（局部变量）是否确实由 requireByCode(...).getId() 赋值 */
    private static boolean resolvedFromRequireByCode(String fileSrc, String arg) {
        String id = oneLine(arg);
        if (!id.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return false;
        }
        // 允许跨行：<ident> = ... requireByCode( ... ).getId();
        return Pattern.compile(Pattern.quote(id) + "\\s*=\\s*[^;]*requireByCode\\s*\\([^;]*\\)\\s*\\.getId\\s*\\(\\s*\\)\\s*;",
                Pattern.DOTALL).matcher(fileSrc).find()
                || Pattern.compile(Pattern.quote(id) + "\\s*=\\s*[^;]*requireByCode\\s*\\(",
                        Pattern.DOTALL).matcher(fileSrc).find();
    }

    @Test
    @DisplayName("setVoucherTypeId 不得硬编码（首版守卫只扫 generateNextNo，漏了这一形态）")
    void noHardcodedVoucherTypeIdInSetter() throws Exception {
        // 🔴 §4.5 第 31 条：门禁的判定依据必须覆盖违规的**全部形态**。
        // 首版只扫 generateNextNo(...)，而 BankJournalServiceImpl 里紧邻的
        // setVoucherTypeId(1L) 是**同一个缺陷**，却完全不在扫描范围内 ——
        // 若照首版实施，凭证会带着「他企业的 voucher_type_id」落库（外键照样成立），
        // 症状更隐蔽：制证不报错，但凭证归属错乱。
        List<String> violations = new ArrayList<>();
        for (String file : productionSources()) {
            String content = Files.readString(Path.of(file), StandardCharsets.UTF_8);
            for (Call c : setterArgsIn(content)) {
                String arg = c.secondArg();
                if (arg.contains(ALLOWED) || arg.contains(".getId()")) {
                    continue;
                }
                if (SETTER_EXCLUSIONS.contains(simpleName(file) + ":" + arg)) {
                    continue;
                }
                if (resolvedFromRequireByCode(content, arg)) {
                    continue;
                }
                violations.add(simpleName(file) + ":" + c.line() + ": setVoucherTypeId(" + arg + ")");
            }
        }

        assertTrue(violations.isEmpty(),
                "setVoucherTypeId 被硬编码为全局 id（SPEC-P128）："
                        + "t_voucher 是外键指向 t_voucher_type，写死 1 会让凭证挂到**别的企业**的凭证类型上，"
                        + "外键仍成立 ⇒ 不报错但归属错乱，比 generateNextNo 抛异常更隐蔽。\n"
                        + "应改为 voucherTypeService.requireByCode(\"JZ\").getId()。\n违规点：\n  "
                        + String.join("\n  ", violations));
    }

    @Test
    @DisplayName("局部变量放行规则自证：必须是 requireByCode 赋值的才放行")
    void localVarRuleIsSensitive() {
        String good = "        long voucherTypeId = voucherTypeService.requireByCode(\n"
                + "                \"payment\".equals(x) ? \"FK\" : \"SK\").getId();\n"
                + "        voucher.setVoucherTypeId(voucherTypeId);";
        String bad = "        long voucherTypeId = VoucherType.ZZ;\n"
                + "        voucher.setVoucherTypeId(voucherTypeId);";
        String unrelated = "        long other = 5L;\n"
                + "        voucher.setVoucherTypeId(other);";

        assertTrue(resolvedFromRequireByCode(good, "voucherTypeId"),
                "由 requireByCode(...).getId() 赋值的局部变量必须放行（否则守卫误报大量合法代码）");
        assertTrue(!resolvedFromRequireByCode(bad, "voucherTypeId"),
                "由 VoucherType 常量赋值的局部变量必须判红 —— 这正是本批要修的形态");
        assertTrue(!resolvedFromRequireByCode(unrelated, "other"),
                "与 requireByCode 无关的局部变量不得放行，否则守卫成了橡皮章");
    }

    @Test
    @DisplayName("setVoucherTypeId 规则自证：硬编码必命中、嵌套的合法写法不得误报")
    void setterRuleIsSensitive() {
        List<Call> bad = setterArgsIn("voucher.setVoucherTypeId(1L);");
        List<Call> bad2 = setterArgsIn("voucher.setVoucherTypeId(VoucherType.ZZ);");
        List<Call> good = setterArgsIn(
                "voucher.setVoucherTypeId(voucherTypeService.requireByCode(\"JZ\").getId());");

        assertEquals(1, bad.size(), "必须提取到字面量写法");
        assertEquals("1L", bad.get(0).secondArg(), "字面量实参提取错误");
        assertEquals(1, bad2.size(), "必须提取到 VoucherType 常量写法");
        assertEquals("VoucherType.ZZ", bad2.get(0).secondArg(), "常量实参提取错误");

        assertEquals(1, good.size(), "必须提取到嵌套的合法写法");
        String g = good.get(0).secondArg();
        assertTrue(g.contains(ALLOWED) && g.contains(".getId()"),
                "🔴 括号配平失败：嵌套实参被截断为 [" + g + "]，"
                        + "会导致合法写法被判红、排除项也匹配不上");
    }

    @Test
    @DisplayName("generateNextNo 的局部变量放行规则必须同样生效")
    void generateRuleAllowsResolvedLocalVar() {
        String src = "        long voucherTypeId = voucherTypeService\n"
                + "                .requireByCode(resolveVoucherTypeCode(stmt.getClassification())).getId();\n"
                + "        String no = voucherNoService.generateNextNo(period, voucherTypeId);";
        List<Call> calls = callsIn(src);
        assertEquals(1, calls.size(), "应识别出 1 处 generateNextNo");
        String arg = oneLine(calls.get(0).secondArg());
        assertEquals("voucherTypeId", arg, "第二参数提取错误");
        assertTrue(resolvedFromRequireByCode(src, arg),
                "由 requireByCode 赋值的局部变量在 generateNextNo 规则里也必须放行");
    }

    // ── 解析工具 ──

    /**
     * 🔴 排除项匹配<b>必须有自证</b>：首版把排除键写成
     * {@code "VoucherServiceImpl.java:generateNextNo(dto.getVoucherTypeId()"}
     * （含方法名与不完整的参数），而实际键是 {@code 文件名:第二参数} ⇒
     * 两个字符串<b>永远对不上</b>，排除项形同虚设却没有任何报错。
     * 判据：<b>排除逻辑本身也是门禁的一部分，同样会静默失效</b>。
     */
    private static boolean isDeclaredExclusion(String fileName, String secondArg) {
        return DECLARED_EXCLUSIONS.contains(fileName + ":" + oneLine(secondArg));
    }

    private record Call(int line, String fullCall, String secondArg) {}

    /**
     * 找出所有 generateNextNo(...) 调用并取其第二参数。
     *
     * ⚠️ 括号配平必须自右向左闭合：扫描到 depth 归 0 时下标 i 本身<b>就是</b>闭合括号，
     * 故第二参数取 {@code substring(start + 1, i)}（含 i 会多带一个右括号）。
     */
    private static List<Call> callsIn(String src) {
        List<Call> calls = new ArrayList<>();
        int from = 0;
        while (true) {
            int at = src.indexOf(METHOD + "(", from);
            if (at < 0) {
                return calls;
            }
            // 跳过方法声明本身：generateNextNo(String period, Long voucherTypeId)
            int lineStart = src.lastIndexOf('\n', at) + 1;
            String prefix = src.substring(Math.max(0, lineStart), at).trim();
            if (prefix.endsWith("public") || prefix.endsWith("default")
                    || prefix.endsWith("String") || prefix.endsWith("abstract")) {
                from = at + METHOD.length();
                continue;
            }

            int open = at + METHOD.length();
            int i = open + 1;
            int depth = 1;
            int firstComma = -1;
            while (i < src.length() && depth > 0) {
                char ch = src.charAt(i);
                if (ch == '(' || ch == '[') {
                    depth++;
                } else if (ch == ')' || ch == ']') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                } else if (ch == ',' && depth == 1 && firstComma < 0) {
                    firstComma = i;
                }
                i++;
            }
            if (firstComma < 0 || i >= src.length()) {
                from = open + 1;
                continue;
            }

            int argStart = firstComma + 1;
            int argEnd = i;
            while (argStart < argEnd && Character.isWhitespace(src.charAt(argStart))) {
                argStart++;
            }
            String full = src.substring(at, i + 1);
            calls.add(new Call(
                    (int) src.substring(0, at).lines().count(),
                    full,
                    src.substring(argStart, argEnd)));
            from = i + 1;
        }
    }

    private static List<String> productionSources() throws Exception {
        Path root = Path.of("src/main/java");
        try (var s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/com/huicai/"))
                    .map(Path::toString)
                    .toList();
        }
    }

    private static String simpleName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String oneLine(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
