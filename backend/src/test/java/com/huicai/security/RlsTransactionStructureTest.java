package com.huicai.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 全仓机械守卫：<b>访问 RLS 租户表的 Service 必须有事务</b>。
 *
 * <h2>缺陷机理（为何「没事务」等于「查不到数据」）</h2>
 * <pre>
 * TenantGucSetter#apply:
 *     if (enterpriseId == null || !isActualTransactionActive()) return;   ← 无事务直接返回
 *     ... SET LOCAL app.enterprise_id = N                                  ← 只在事务内有效
 *
 * RLS 谓词（72 张表逐字一致）：
 *     enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::bigint
 * </pre>
 * 无事务 ⇒ GUC 从不设置 ⇒ 谓词右值为 NULL ⇒ <b>该表全部行被过滤</b>。
 * 症状是<b>「列表恒空」</b>或<b>「记录不存在」</b>的 500 —— 不报错、不告警。
 *
 * <h2>为什么 L2 全绿测不出来</h2>
 * {@code AbstractMapperTest} 连的是 Testcontainers 的<b>超级用户</b>，
 * 而超级用户<b>绕过 RLS</b>（AGENTS §4.5 第 33 条）⇒ 谓词根本不执行，
 * "0 行" 这种症状<b>不可能在测试里出现</b>。
 *
 * <h2>已实证的两处真实故障（服务器 2026-10-18）</h2>
 * <ul>
 *   <li>{@code VoucherTypeController} 两个读方法无事务 ⇒ 凭证类型下拉恒空、<b>无法制证</b></li>
 *   <li>{@code AiTaskService#getById} 等读方法无事务 ⇒ DB 有 3 行时
 *       {@code /api/v1/ai/tasks/page} 返回 {@code total=0}、
 *       {@code /api/v1/ai/tasks/{id}} 报「AI 任务不存在」</li>
 * </ul>
 *
 * <h2>🔴 本守卫的判定依据与它的盲区（必须如实知道）</h2>
 * 依据是「Service 类上有没有 {@code @Transactional}（类级或该方法级）」。
 * <b>它查不出「注解在但不生效」</b> —— 事实上 {@code VoucherTypeServiceImpl}
 * 早就挂着类级 {@code @Transactional}，实测该路径<b>依然</b>是
 * {@code non transactional SqlSession}。所以本守卫是<b>必要非充分</b>的护栏：
 * 它挡住「压根没写注解」这一大类，剩下的必须靠<b>运行时对照 DB 行数</b>才能发现。
 * <p>登记的白名单同样只允许缩小不允许扩大 —— 扩大等于把爆炸半径藏起来。
 */
@DisplayName("RLS 守卫：访问 RLS 租户表的 Service 必须有事务（否则 GUC 不设 ⇒ 整表被过滤）")
class RlsTransactionStructureTest {

    /** 主代码根目录 */
    private static final Path SRC = Path.of("src/main/java");

    /**
     * 已实证「有注解仍不生效」的类：其类级注解存在，但运行时该路径仍无事务，
     * 故真正的保障在 Controller 侧（见 {@code VoucherTypeControllerTransactionGuardTest}）。
     * <p>登记它们是为了让本守卫<b>不去误报</b>，而不是承认它们已修好。
     */
    private static final List<String> KNOWN_ANNOTATION_BUT_NOT_EFFECTIVE = List.of(
            "VoucherTypeServiceImpl"
    );

    /**
     * 明确豁免：这些 Service 访问的表<b>不在 RLS 名单内</b>，无 GUC 依赖。
     * 每条须写明理由；只允许缩小不允许扩大（§4.5 第 43 条）。
     */
    private static final Map<String, String> EXEMPT = new LinkedHashMap<>();

    static {
        // 健康检查/纯内存计算：无 Mapper 依赖，不触达任何 RLS 表
        EXEMPT.put("CodeGeneratorServiceImpl", "纯代码生成，无 Mapper 依赖");
    }

    @Test
    @DisplayName("访问 RLS 表的 Service 类必须声明 @Transactional（类级或全部读方法级）")
    void rlsTouchingServicesMustBeTransactional() throws IOException {
        List<String> violations = new ArrayList<>();
        int scanned = 0;

        for (Path service : javaFilesUnder(SRC)) {
            String name = service.getFileName().toString().replace(".java", "");
            if (!name.endsWith("Service") && !name.endsWith("ServiceImpl")) {
                continue;
            }
            if (!EXEMPT.containsKey(name)) {
                scanned++;
            }
            String src = stripComments(Files.readString(service, StandardCharsets.UTF_8));

            // 只看真正注入 Mapper/Repository 的（无持久化依赖的纯计算 Service 不受 GUC 影响）
            boolean touchesPersistence = hasMapperField(src);
            if (!touchesPersistence) {
                continue;
            }

            if (hasClassLevelTransactional(src, name)) {
                continue;
            }
            // 类级没有，则要求「所有 public 方法」各自都有事务（等价于全覆盖）
            if (allPublicMethodsTransactional(src)) {
                continue;
            }

            violations.add(name + (KNOWN_ANNOTATION_BUT_NOT_EFFECTIVE.contains(name)
                    ? "  [已知: 类级注解存在但实测未生效，见 RLS 白名单说明]"
                    : ""));
        }

        assertTrue(violations.isEmpty(),
                "以下 Service 访问 Mapper 却缺 @Transactional ⇒ TenantGucSetter 提前 return "
                        + "⇒ GUC 恒 NULL ⇒ RLS 谓词取 NULL ⇒ **该 Service 触及的所有租户表恒返 0 行**。\n"
                        + "症状：列表恒空 / 「记录不存在」的 500，不报错不告警。\n"
                        + "修法：给类加 @Transactional(rollbackFor = Exception.class)。\n"
                        + "违规（" + violations.size() + "）：\n  " + String.join("\n  ", violations)
                        + "\n（已扫描 Service " + scanned + " 个）");
    }

    @Test
    @DisplayName("🔴 守卫自身有效：构造「有 Mapper 但无事务」的片段，判定逻辑必须判红")
    void guardDetectsMissingTransactional() {
        String bad = """
                @Service
                public class ProbeService {
                    private final FooMapper fooMapper;
                    public List<X> page() { return fooMapper.selectList(null); }
                }
                """;
        assertTrue(!hasClassLevelTransactional(bad, "ProbeService"),
                "无事务的类级判定必须为 false（否则守卫恒绿）");
        assertTrue(!allPublicMethodsTransactional(bad),
                "无任何事务注解时，方法级全覆盖判定必须为 false");
        assertTrue(hasMapperField(bad), "Mapper 字段识别失效");

        String good = """
                @Service
                @Transactional(rollbackFor = Exception.class)
                public class ProbeService2 {
                    private final FooMapper fooMapper;
                    public List<X> page() { return fooMapper.selectList(null); }
                }
                """;
        assertTrue(hasClassLevelTransactional(good, "ProbeService2"),
                "类级 @Transactional 必须被识别（否则守卫误报全部 Service）");
        assertTrue(hasMapperField(good), "注解不应影响 Mapper 识别");

        String goodByMethod = """
                @Service
                public class ProbeService3 {
                    private final FooMapper fooMapper;
                    @Transactional(rollbackFor = Exception.class)
                    public List<X> page() { return fooMapper.selectList(null); }
                }
                """;
        assertTrue(allPublicMethodsTransactional(goodByMethod),
                "方法级 @Transactional 必须被识别（否则误报逐方法标注的写法）");
    }

    @Test
    @DisplayName("🔴 括号/注释处理不得让判定失真（自证）")
    void stripCommentsKeepsLineCount() {
        String src = "// c1\n/* block\n   line\n*/\n@Service\npublic class A {}";
        String s = stripComments(src);
        assertTrue(s.lines().count() >= 4, "剥离注释后应保留换行，行数不能塌缩");
        assertTrue(!s.contains("block"), "块注释内容必须被剥离");
        assertTrue(!s.contains("//"), "行注释必须被剥离");
    }

    // ── 实现 ──

    private static boolean hasMapperField(String src) {
        Matcher m = Pattern.compile(
                "private\\s+(?:final\\s+)?\\w*(?:Mapper|Repository)\\s+\\w+\\s*;").matcher(src);
        return m.find();
    }

    /** 类声明之前（向前 1200 字符窗口内）出现 @Transactional 即算类级 */
    private static boolean hasClassLevelTransactional(String src, String clsName) {
        int idx = src.indexOf("class " + clsName);
        if (idx < 0) {
            idx = src.lastIndexOf("class ");
        }
        if (idx < 0) {
            return false;
        }
        return src.substring(Math.max(0, idx - 1200), idx).contains("@Transactional");
    }

    /** 所有 public 方法体之前都带 @Transactional */
    private static boolean allPublicMethodsTransactional(String src) {
        Matcher m = Pattern.compile("public\\s+(?!class|interface)[\\w<>,\\[\\]\\s\\.]+\\s+\\w+\\s*\\(").matcher(src);
        int count = 0, ok = 0;
        while (m.find()) {
            // 构造器/静态初始化块不算业务方法
            String head = src.substring(Math.max(0, m.start() - 400), m.start());
            int lineStart = src.lastIndexOf('\n', m.start()) + 1;
            String thisLine = src.substring(lineStart, m.start()).trim();
            if (thisLine.isEmpty() || thisLine.startsWith("//")) {
                continue;
            }
            count++;
            if (head.contains("@Transactional")) {
                ok++;
            }
        }
        return count > 0 && count == ok;
    }

    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i = Math.min(n, i + 2);
                out.append(' ');
            } else if (c == '"' || c == '\'') {
                char q = c;
                i++;
                while (i < n && src.charAt(i) != q) {
                    if (src.charAt(i) == '\\') {
                        i++;
                    }
                    i++;
                }
                i++;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static List<Path> javaFilesUnder(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/com/huicai/"))
                    .toList();
        }
    }
}