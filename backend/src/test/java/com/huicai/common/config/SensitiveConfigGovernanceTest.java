package com.huicai.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D6 敏感配置治理（REQ-2026-134 / P107）：仓库内不得留明文口令，密钥必须外部注入。
 *
 * <p>静态检查类测试：按**文件路径**读源码配置（不能用 classpath 读，测试资源会优先命中
 * {@code src/test/resources/application.yml} 而测不到主配置），断言：
 * ①数据库/MQ/对象存储口令均为 {@code ${ENV:占位}} 形式而非裸明文；
 * ②JWT 密钥无默认值（缺 {@code JWT_SECRET} 时必须启动失败，杜绝可预测密钥伪造 token）。
 *
 * <p>读源码而非编译产物：{@code target/classes} 里的副本可能是上一次构建的陈旧内容
 * （D1 期间实测踩过：撤掉 V160 迁移后测试仍假绿，根因就是 target/classes 残留）。
 */
class SensitiveConfigGovernanceTest {

    private static final Path MAIN_YML = Path.of("src/main/resources/application.yml");
    private static final Path MAIN_DEV_YML = Path.of("src/main/resources/application-dev.yml");
    private static final Path COMPOSE_YML = Path.of("../docker-compose.yml");
    private static final Path TEST_YML = Path.of("src/test/resources/application.yml");

    /** 裸明文口令：password: huicai123 这类右侧不带 ${} 的写法 */
    private static final Pattern PLAINTEXT_PASSWORD =
            Pattern.compile("^\\s*password:\\s*(\\S+)\\s*$", Pattern.MULTILINE);

    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), "配置文件缺失: " + path.toAbsolutePath());
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static List<String> plaintextPasswords(String yml) {
        List<String> offenders = new ArrayList<>();
        Matcher m = PLAINTEXT_PASSWORD.matcher(yml);
        while (m.find()) {
            if (!m.group(1).startsWith("${")) {
                offenders.add(m.group().trim());
            }
        }
        return offenders;
    }

    @Test
    void 主配置_数据库与中间件口令不得为裸明文() throws IOException {
        List<String> offenders = plaintextPasswords(read(MAIN_YML));
        assertTrue(offenders.isEmpty(),
                "以下口令仍为裸明文（应改为 ${ENV:占位} 以便外部注入）: " + offenders);
    }

    @Test
    void dev配置_口令不得为裸明文() throws IOException {
        List<String> offenders = plaintextPasswords(read(MAIN_DEV_YML));
        assertTrue(offenders.isEmpty(),
                "以下 dev 口令仍为裸明文（应改为 ${ENV:占位}）: " + offenders);
    }

    @Test
    void 主配置_JWT密钥不得有默认值() throws IOException {
        String yml = read(MAIN_YML);
        Matcher m = Pattern.compile("^\\s*secret:\\s*\\$\\{JWT_SECRET(:[^}]*)?\\}\\s*$",
                Pattern.MULTILINE).matcher(yml);
        assertTrue(m.find(), "应存在 jwt.secret: ${JWT_SECRET} 配置行");
        assertNull(m.group(1),
                "JWT 密钥不得带默认值 —— 可预测的默认密钥等于无密钥：未注入 JWT_SECRET 时应用必须启动失败");
    }

    @Test
    void compose_口令不得硬编码在编排文件() throws IOException {
        // docker-compose 的口令应走 ${VAR:-占位}，由 .env 注入，编排文件本身不落明文
        List<String> offenders = new ArrayList<>();
        Matcher m = Pattern.compile(
                "^\\s*(POSTGRES_PASSWORD|MINIO_ROOT_PASSWORD|MINIO_SECRET_KEY|"
                        + "RABBITMQ_DEFAULT_PASS|HUICAI_DB_PASSWORD|HUICAI_MINIO_SECRET_KEY|"
                        + "SPRING_DATASOURCE_PASSWORD|SPRING_RABBITMQ_PASSWORD|JWT_SECRET|"
                        + "HUICAI_RABBITMQ_URL)\\s*:\\s*(\\S+)\\s*$", Pattern.MULTILINE)
                .matcher(read(COMPOSE_YML));
        while (m.find()) {
            String value = m.group(2);
            if (value.contains(":-")) continue;           // ${VAR:-占位} 为可接受形态
            if (value.startsWith("${") && value.endsWith("}")) {
                // ${VAR} 形态：值来自 .env；URL 型需整体变量化
                if (m.group(1).endsWith("URL") && value.contains("@")) {
                    offenders.add(m.group().trim() + "（URL 内联了凭证）");
                }
                continue;
            }
            offenders.add(m.group().trim());
        }
        assertTrue(offenders.isEmpty(), "编排文件不得硬编码凭证: " + offenders);
    }

    @Test
    void 测试配置自带独立密钥不依赖主配置兜底() throws IOException {
        // 保证主配置去掉默认值后 @SpringBootTest 仍能装配 JwtProvider
        String testYml = read(TEST_YML);
        assertTrue(testYml.contains("secret:"),
                "测试配置应自带 JWT 密钥，不依赖主配置默认值");
    }
}
