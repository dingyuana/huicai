package com.huicai.ai.quota;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 🔴 配置镜像守卫：`ai.quota` 在<b>主配置</b>与<b>测试配置</b>中都必须声明，且取值一致。
 *
 * <p><b>为什么需要它（本轮真实踩到）</b>：本仓库的
 * {@code src/test/resources/application.yml} 是一个<b>独立的最小测试配置</b>（H2），
 * 它与 {@code src/main/resources/application.yml} <b>同名</b>，
 * 而 Spring Boot 的 {@code classpath:/application.yml} <b>只取第一个命中</b>
 * ⇒ 测试上下文里<b>主配置的全部键都不可见</b>。</p>
 *
 * <p>症状极具欺骗性：报错是
 * {@code BindValidationException: Field error on field 'daily': rejected value [null]}，
 * 看起来像「配置文件写错了」或「{@code @NotNull} 用错了地方」，
 * 真实原因却是<b>测试根本没读那份配置</b>。
 * 而如果当初没给 {@code daily} 加 {@code @NotNull}，
 * 这份「主配置必填」的键就会<b>静默地在测试中缺省为 null</b>，
 * 于是 AT-135-2（超限 429）在测试里会以「额度为 0 ⇒ 一律拒绝」的方式通过 ——
 * <b>守卫全绿，而真机上配额功能从未被测过</b>。</p>
 *
 * <p><b>判据沉淀</b>：当一份配置<b>同名地</b>存在多份（主/测试/环境），
 * 「必填校验」的实际覆盖面<b>只等于被加载的那一份</b>。
 * 凡「必填」是设计的一部分，就必须<b>逐份声明 + 机器校验</b>，
 * 否则「必填」在一半的环境里形同虚设。</p>
 */
class AiQuotaConfigMirrorTest {

    private static final Path MAIN = Paths.get("src/main/resources/application.yml");
    private static final Path TEST = Paths.get("src/test/resources/application.yml");

    @Test
    @DisplayName("主配置与测试配置都必须声明 ai.quota.daily，且默认值一致")
    void bothConfigFilesDeclareDaily() throws Exception {
        assertTrue(Files.exists(MAIN), "主配置不存在：" + MAIN.toAbsolutePath());
        assertTrue(Files.exists(TEST), "测试配置不存在：" + TEST.toAbsolutePath());

        String main = read(MAIN);
        String test = read(TEST);

        String mainDaily = extract(main);
        String testDaily = extract(test);

        assertTrue(mainDaily != null,
                "主配置缺少 ai.quota.daily —— 而 AiQuotaProperties 用 @NotNull 要求它必填，"
                        + "缺失会导致应用无法启动");
        assertTrue(testDaily != null,
                "🔴 测试配置缺少 ai.quota.daily。测试配置与主配置**同名且在 classpath 优先**，"
                        + "主配置的键在测试中全部不可见 ⇒ 若此处缺，"
                        + "AT-135-2 会以「额度为 0 ⇒ 一律 429」的方式假绿，"
                        + "而真机上配额从未被测过");
        assertEquals(mainDaily, testDaily,
                "两份配置的 ai.quota.daily 默认值不一致（主=" + mainDaily
                        + "，测试=" + testDaily + "）—— 测试会按与真机不同的额度运行");
    }

    @Test
    @DisplayName("豁免权限码两份配置也必须一致（否则测试里的豁免判定与真机不同）")
    void bothConfigFilesDeclareSameExemptions() throws Exception {
        String mainExempt = extractExempt(read(MAIN));
        String testExempt = extractExempt(read(TEST));
        assertTrue(mainExempt != null && !mainExempt.isBlank(),
                "主配置缺少 ai.quota.exempt-permissions");
        assertEquals(mainExempt, testExempt,
                "两份配置的豁免权限码不一致（主=" + mainExempt + "，测试=" + testExempt + "）");
    }

    private static String read(Path p) throws Exception {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** 提取 {@code ai: -> quota: -> daily:} 的值。 */
    private static String extract(String yml) {
        boolean inAi = false;
        boolean inQuota = false;
        for (String raw : yml.split("\n")) {
            String line = raw;
            String trimmed = line.trim();
            int indent = line.length() - line.replaceFirst("^\\s*", "").length();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (indent == 0) {
                inAi = "ai:".equals(trimmed);
                inQuota = false;
                continue;
            }
            if (inAi && indent == 2) {
                inQuota = trimmed.startsWith("quota:");
                continue;
            }
            if (inAi && inQuota && indent == 4 && trimmed.startsWith("daily:")) {
                return trimmed.substring("daily:".length()).trim();
            }
        }
        return null;
    }

    private static String extractExempt(String yml) {
        boolean inAi = false, inQuota = false, inExempt = false;
        StringBuilder sb = new StringBuilder();
        for (String raw : yml.split("\n")) {
            String trimmed = raw.trim();
            int indent = raw.length() - raw.replaceFirst("^\\s*", "").length();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (indent == 0) {
                // 🔴 原写成 `break`：一遇到 ai: 就跳出循环，子树永远读不到，
                //    于是「主配置缺 exempt-permissions」是**误报**。
                //    正确：进入 ai: 时继续，**离开** ai: 时才停。
                if (inAi && !"ai:".equals(trimmed)) {
                    break;
                }
                inAi = "ai:".equals(trimmed);
                inQuota = false;
                continue;
            }
            if (inAi && indent == 2) {
                inQuota = trimmed.startsWith("quota:");
                continue;
            }
            if (inAi && inQuota && indent == 4) {
                inExempt = trimmed.startsWith("exempt-permissions:");
                continue;
            }
            if (inExempt && indent == 6 && trimmed.startsWith("- ")) {
                sb.append(trimmed.substring(2).trim()).append(",");
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void assertTrue(boolean cond, String msg) {
        org.junit.jupiter.api.Assertions.assertTrue(cond, msg);
    }

    private static void assertEquals(Object a, Object b, String msg) {
        org.junit.jupiter.api.Assertions.assertEquals(a, b, msg);
    }
}