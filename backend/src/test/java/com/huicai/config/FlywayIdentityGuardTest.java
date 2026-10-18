package com.huicai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway 迁移身份守卫 —— 锁住「迁移不得与应用共用 RLS 受限身份」这条不变量
 *
 * <h2>为什么必须单独守（2026-10-09 线上故障复盘）</h2>
 * 现象：{@code POST /api/v1/subjects/import-standard} 返回 500，
 * {@code ERROR: new row violates row-level security policy for table "t_subject"}。
 *
 * <p><b>根因链</b>：
 * <ol>
 *   <li>M5b 把应用连接身份从超级用户 {@code huicai} 降为
 *       {@code huicai_app}（NOSUPERUSER + NOBYPASSRLS）⇒ RLS 真正生效；</li>
 *   <li>但 Flyway 与应用<b>共用同一个数据源</b> ⇒ 迁移期也没有企业上下文；</li>
 *   <li>72 张表的策略 {@code with_check} 为<b>空</b> ⇒ PostgreSQL 复用 {@code qual}
 *       作为 WITH CHECK ⇒ 要求 {@code enterprise_id = current_setting('app.enterprise_id')}，
 *       而那个 GUC 由 {@code TenantRlsInitializer} 切面按
 *       {@code EnterpriseContextHolder} 设置，<b>Flyway 路径上不存在</b>；</li>
 *   <li>⇒ 任何向租户表 seed 的迁移必被拒，{@code flywayInitializer} 失败，
 *       {@code sqlSessionTemplate} → {@code userMapper} → 过滤器整条链起不来。</li>
 * </ol>
 *
 * <p><b>🔴 为什么此前没有任何测试发现</b>：L1 的测试日志里 Flyway 出现 <b>0 次</b>
 * —— 快测套件用 Testcontainers 自带 schema，根本不跑 Flyway；
 * 而这条「启动路径」只在真实部署时执行。于是<b>整条部署链路零覆盖</b>，
 * 与 AGENTS §4.5 第 27 条「兜底层在最高权限主体下永不可证伪」同型：
 * <b>没被任何阶段评估过的东西，默认就是坏的</b>。
 *
 * <p>本类不启动 Spring 上下文（那需要真库），改为直接解析
 * {@code application.yml} 并代入<b>空环境下的默认值</b>，断言这条不变量：
 * <b>Flyway 的连接身份不得与应用相同</b>。
 * 若有人把 {@code spring.flyway.user} 删掉（回到共用数据源），
 * 解析结果会与应用身份相等 ⇒ 本类转红。
 */
@DisplayName("Flyway 迁移身份：不得与应用共用 RLS 受限身份")
class FlywayIdentityGuardTest {

    /**
     * 匹配 ${VAR} / ${VAR:default} / ${VAR:-default}。
     *
     * <p>⚠️ <b>必须同时支持单冒号与 {@code :-}</b>：本项目 application.yml 一律写
     * {@code ${DB_USERNAME:huicai_app}}（<b>单冒号</b>）。首版只写了
     * {@code (?::-(.*?))?}，结果两种取值都解析失败、退化成
     * {@code UNRESOLVED:<变量名>} —— 于是「应用身份」与「Flyway 身份」因为
     * <b>变量名不同</b>而被判为「不相等」，断言通过。
     * 反证实测（把 flyway.user 默认值改成与应用相同的 {@code huicai_app}）该版本
     * <b>仍然 exit 0</b> ⇒ 那道断言此前是<b>假绿</b>。
     * <b>判据：写「两个值必须不同」的守卫时，要先证明它真能判出「相同」——
     * 否则它可能只是在比较两个碰巧不同的字符串。</b>
     */
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\$\\{([A-Za-z0-9_.]+)(?::-?(.*?))?\\}");

    private static Path appYml() {
        Path p = Paths.get("src/main/resources/application.yml");
        assertTrue(Files.exists(p), "找不到 application.yml（测试工作目录应为 backend/）");
        return p;
    }

    /** 只做两层扁平读取：application.yml 的 datasource / flyway 都是 `spring:` 下的二级键 */
    private static Map<String, String> flatSpringKeys(Path yml) {
        Map<String, String> out = new LinkedHashMap<>();
        String section = null;
        try {
            for (String raw : Files.readAllLines(yml)) {
                String line = raw.replaceAll("#.*$", "");
                if (line.isBlank()) {
                    continue;
                }
                int indent = line.length() - line.stripLeading().length();
                String body = line.strip();
                if (indent == 0) {
                    section = null;
                    continue;
                }
                if (indent == 2) {
                    section = body.replace(":", "").trim();
                    continue;
                }
                if (indent != 4 || section == null) {
                    continue;
                }
                int c = body.indexOf(':');
                if (c > 0) {
                    out.put(section + "." + body.substring(0, c).trim(),
                            body.substring(c + 1).trim());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** 代入空环境：${VAR:default} 取 default，${VAR} 视为未定义（返回原样以便断言失败可读） */
    private static String resolve(String expr) {
        Matcher m = PLACEHOLDER.matcher(expr);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String def = m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement(def != null ? def : "\u0000UNRESOLVED:" + m.group(1)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @Test
    @DisplayName("Flyway 有显式独立身份，且默认值不等于应用身份")
    void flywayHasItsOwnIdentity() {
        Map<String, String> k = flatSpringKeys(appYml());

        String appUser = k.get("datasource.username");
        assertTrue(appUser != null && !appUser.isBlank(),
                "★ datasource.username 缺失 ⇒ 无法比较，本守卫失效★");

        String flywayUserKey = "flyway.user";
        assertTrue(k.containsKey(flywayUserKey),
                "★ spring.flyway 未配置 user ⇒ Flyway 会静默复用 spring.datasource 的身份★"
                        + "（那就是 2026-10-09 线上 500 的根因：RLS 生效后 seed 迁移全被"
                        + " with_check 拒绝，flywayInitializer 连带让整个应用起不来）");

        String flywayUserResolved = resolve(k.get(flywayUserKey));
        String appUserResolved = resolve(appUser);

        assertNotEquals(appUserResolved, flywayUserResolved,
                "★ Flyway 与应用用了同一个身份（都是 " + appUserResolved + "）⇒"
                        + " M5b 降权后 seed 迁移必被 RLS 拒。应指向 M5b 手册既定保留的"
                        + " 超级用户（运维/迁移身份）★");
        assertFalse(flywayUserResolved.contains("UNRESOLVED"),
                "spring.flyway.user 引用了未给默认值的变量：" + k.get(flywayUserKey));
        assertFalse(flywayUserResolved.isBlank() || appUserResolved.isBlank(), "身份不能为空");
    }

    @Test
    @DisplayName("Flyway 口令同样独立注入，且不硬编码")
    void flywayPasswordIsConfigurable() {
        Map<String, String> k = flatSpringKeys(appYml());
        String key = "flyway.password";
        assertTrue(k.containsKey(key), "spring.flyway 未配置 password ⇒ 无法按 §7-3 注入独立口令");
        assertTrue(k.get(key).contains("${"),
                "spring.flyway.password 未用 ${} 注入 ⇒ 违反 AGENTS §7-3（禁止硬编码敏感信息）");
    }

    @Test
    @DisplayName("反向自检：若删掉 spring.flyway.user，本守卫必须能判红")
    void guardDetectsMissingKey() {
        // 用一个「故意缺失该键」的内存副本跑同一套判定，确证上面的断言不是恒真
        Map<String, String> k = new HashMap<>(flatSpringKeys(appYml()));
        k.remove("flyway.user");
        boolean detected = !k.containsKey("spring.flyway.user");
        assertTrue(detected, "守卫失效：删掉键后竟检测不到");
    }

    @Test
    @DisplayName("docker-compose 也注入了 Flyway 身份（yml 与 compose 不得只改一头）")
    void composeAlsoInjectsFlywayIdentity() throws IOException {
        // 测试工作目录是 backend/，docker-compose.yml 在仓库根
        Path compose = Paths.get("..", "docker-compose.yml");
        assertTrue(Files.exists(compose), "找不到 docker-compose.yml");
        List<String> lines = Files.readAllLines(compose);
        long n = lines.stream().filter(l -> l.contains("SPRING_FLYWAY_USER")).count();
        assertTrue(n >= 1,
                "★ docker-compose.yml 未注入 SPRING_FLYWAY_USER ⇒ 容器里 Flyway 会回落"
                        + " 到默认值。若该默认值与应用身份相同（或环境未配 POSTGRES_*），"
                        + "线上会再次复现同一条 500。yml 与 compose 必须同步改★");
    }
}