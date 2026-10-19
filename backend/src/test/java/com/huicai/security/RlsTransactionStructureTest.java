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
 *
 * <p><b>🔴 这条依据已被实测推翻过一次，不可当定论</b>：守卫首版正是按此判据把
 * {@code AiTaskService} 判为「已修」，因为它已挂类级 {@code @Transactional}；
 * 但部署到服务器后症状<b>丝毫未变</b>（DB 有数据仍 {@code total=0}），日志显示
 * {@code SqlSession was not registered for synchronization because synchronization is not active}
 * ⇒ <b>Service 层的事务代理在该路径上没有生效</b>。
 * {@code VoucherTypeServiceImpl} 同样是「类级注解在、运行时仍无事务」。
 *
 * <p><b>目前唯一两次实测有效的修法是把事务上提到 Controller</b>
 * （{@code VoucherTypeController}、{@code AiTaskController}）——
 * 那里日志确为 {@code will be managed by Spring} 且能读到数据。
 *
 * <p>因此本守卫的正确用法是：<b>它是「必要非充分」的粗筛</b> ——
 * 挡掉「压根没写注解」这一大类；剩下的必须靠<b>运行时对照 DB 行数</b>才能确认。
 * 真正的根治（让 Service 层事务代理生效）尚未定位，属未竟项。
 *
 * <p>登记的白名单只允许缩小不允许扩大 —— 扩大等于把爆炸半径藏起来（§4.5 第 43 条）。
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
     * 明确豁免：这些 Service <b>虽有 Mapper 依赖，但被守卫点名的方法并不触表</b>，
     * 故不需要事务。每条须写明「哪个方法 + 为什么不触表」——
     * 🔴 只写「不需要事务」而不写理由，等于把判断权交还给未来的修改者，
     *    他把一个真读表的方法加进来时不会被拦住。
     */
    private static final Map<String, String> EXEMPT = new LinkedHashMap<>();

    static {
        // classifyType 是 static 且只调 BankClassification.routeType（纯规则路由），不查库；
        // isSmallAmountDirectVoucher 同理只读入参。两者无 Mapper 调用。
        EXEMPT.put("AutoGenerationService", "被点名的 classifyType/isSmallAmountDirectVoucher 是 static 纯规则路由，不触表；"
                + "其读表方法 generateDocThenVoucher 等已在类内各自带事务");
        // previewInvoices 只解析上传的 Excel 文件，不查库；写方法 confirmImport/importInvoices 已有事务。
        EXEMPT.put("SalesInvoiceImportService", "被点名的 previewInvoices 仅解析上传文件不触表；写方法已有 @Transactional");
        EXEMPT.put("InputInvoiceImportService", "被点名的 previewInvoices 仅解析上传文件不触表；写方法已有 @Transactional");
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
            scanned++;
            String src = stripComments(Files.readString(service, StandardCharsets.UTF_8));

            // 只看真正注入 Mapper/Repository 的（无持久化依赖的纯计算 Service 不受 GUC 影响）
            boolean touchesPersistence = hasMapperField(src);
            if (!touchesPersistence) {
                continue;
            }

            // 🔴 豁免必须**同时**跳过计数与判定：首版只在计数处判断，
            // 判定处照常报警 ⇒ 豁免形同虚设，且报错里不提示「已豁免」，
            // 让人以为豁免没生效而反复改代码（本次即被此绕了一圈）。
            if (EXEMPT.containsKey(name)) {
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

    /**
     * 所有 public 方法是否各自带 {@code @Transactional}。
     *
     * <p>🔴 <b>首版实现是错的，且错得极其隐蔽</b>：它取「方法所在行行首 → 方法名」之间的文本
     * 来找注解，而真实 Java 方法都是<b>行首缩进 + 方法声明</b>，该片段 trim 后是空串
     * ⇒ <b>每个方法都被当成「无注解」跳过</b> ⇒ 判定恒为 false
     * ⇒ 守卫把全仓所有 Service 都报成违规（实测误报 9 个），连自证用例也一并失败。
     *
     * <p><b>教训</b>：这个守卫在本轮之前从未被全量 L1 跑到（它是我上一轮新加的），
     * 所以「单跑该类时绿/红」都不能替代「在全量里跑一次」——
     * 单元测试意义上的「通过」与「在全量构建中被执行」是两件事。
     *
     * <p>正确做法：<b>按行向上回溯</b>，跳过空行与 {@code @} 注解行，
     * 遇到第一个非注解行即停（那是方法体或字段声明）。
     */
    private static boolean allPublicMethodsTransactional(String src) {
        String[] lines = src.split("\n", -1);
        int total = 0;
        int annotated = 0;

        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (!t.startsWith("public ")) {
                continue;
            }
            // 类型声明不是方法
            if (t.startsWith("public class") || t.startsWith("public interface")
                    || t.startsWith("public enum") || t.startsWith("public @interface")
                    || t.startsWith("public record")) {
                continue;
            }
            // 必须是方法声明：含 '(' 且 '(' 之前没有 '='（否则是字段初始化）
            int paren = t.indexOf('(');
            if (paren < 0) {
                continue;
            }
            if (t.substring(0, paren).contains("=")) {
                continue;
            }
            // 构造器没有返回类型：`public FooServiceImpl(` 的括号前无空格分隔的返回类型
            String beforeParen = t.substring(0, paren).trim();
            if (!beforeParen.contains(" ")) {
                continue;
            }
            total++;

            boolean hasTx = false;
            for (int j = i - 1; j >= 0; j--) {
                String p = lines[j].trim();
                if (p.isEmpty()) {
                    continue;
                }
                if (p.startsWith("@")) {
                    if (p.contains("Transactional")) {
                        hasTx = true;
                    }
                    continue;
                }
                break;
            }
            if (hasTx) {
                annotated++;
            }
        }
        return total > 0 && total == annotated;
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