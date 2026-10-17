package com.huicai.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SPEC-P133 —— 权限清单加载器（唯一事实源）。
 *
 * <p>🔴 <b>本类是「端点需要哪个权限」的唯一读取入口</b>：后端拦截器与三条守卫都从它读。
 * 刻意<b>不</b>用正则逐行解析 YAML —— 那会让「守卫的解析口径」与「运行时的解析口径」
 * 各自漂移一次（§4.5 第 45 条：评估覆盖率必须用工具自己的口径）。
 * 统一走 snakeyaml，解析失败时 <b>fail-loud</b>。</p>
 */
@Slf4j
@Component
public class PermissionManifest {

    public record Entry(String method, String path, String permission,
                        boolean write, Pattern pattern) {
        public boolean matches(String reqMethod, String uri) {
            return method.equalsIgnoreCase(reqMethod) && pattern.matcher(uri).matches();
        }
    }

    private List<Entry> entries = new ArrayList<>();
    private boolean enforcing;

    /**
     * 🔴 踩坑留证（两轮，两轮报错都指错方向）：
     * <ol>
     *   <li>首版<b>只写了 {@code @PostConstruct} 却忘了 {@code @Component}</b>
     *       ⇒ 不是 bean ⇒ {@code NoSuchBeanDefinitionException: PermissionManifest}。
     *       <b>@PostConstruct 是生命周期回调，不是注册注解。</b></li>
     *   <li>加上 {@code @Component} 后，<b>L1 仍 97 个用例报同一异常</b> ——
     *       因为 {@code @WebMvcTest} 是<b>切片上下文</b>：它只加载
     *       {@code @Controller/@ControllerAdvice/WebMvcConfigurer/Filter/HandlerInterceptor} 等
     *       白名单 bean，<b>不扫描普通 @Component</b>。
     *       而 {@code PermissionWebMvcConfig} 恰好实现了 {@code WebMvcConfigurer}
     *       ⇒ 被切片加载 ⇒ 它依赖的清单却不存在。
     *       正解：把清单声明为该配置里的 <b>{@code @Bean}</b>，
     *       这样「任何会加载 WebMvcConfigurer 的上下文」都必然带上它。</li>
     * </ol>
     * <b>判据：新增的协作对象若被切片测试间接依赖，要么放进切片的白名单里
     * （如 {@code @Component + WebMvcConfigurer}），要么由配置显式 {@code @Bean} 声明。</b>
     */
    @PostConstruct
    public void load() {
        loadForTest();
    }

    /**
     * 加载清单（可被测试直接调用）。
     *
     * <p>之所以把实现体单独拆出来：测试需要构造一个「强制 enforcing」的清单实例来验证
     * fail-closed 分支，而 {@code load()} 上挂着 {@code @PostConstruct}（那是容器回调，
     * 对手工 new 的实例不会触发）。</p>
     */
    public void loadForTest() {
        entries = doLoad();
        long writes = entries.stream().filter(Entry::write).count();
        log.warn("权限清单已加载：共 {} 条（写端点 {} 条），执行模式={} —— {}",
                entries.size(), writes, enforcing ? "enforcing" : "warn-only",
                enforcing
                        ? "命中但缺权限将返回 403"
                        : "命中但缺权限**只告警仍放行**，**尚未完成权限管控**");
    }

    @SuppressWarnings("unchecked")
    private List<Entry> doLoad() {
        ClassPathResource res = new ClassPathResource("permission-manifest.yml");
        if (!res.exists()) {
            throw new IllegalStateException(
                    "找不到 permission-manifest.yml —— 权限清单是唯一事实源，缺失即拒绝启动，"
                            + "不得降级为「无清单 = 全部放行」");
        }
        try (InputStream in = res.getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            Object mode = root.get("enforcement");
            if (mode == null) {
                throw new IllegalStateException(
                        "permission-manifest.yml 缺少 enforcement 段 —— 执行模式必须**显式声明**，"
                                + "不得靠默认值，否则将来切 fail-closed 时没人知道现值是什么");
            }
            enforcing = "enforcing".equalsIgnoreCase(String.valueOf(mode));
            if (!enforcing && !"warn-only".equalsIgnoreCase(String.valueOf(mode))) {
                throw new IllegalStateException("enforcement 取值非法：" + mode
                        + "（只允许 warn-only / enforcing）");
            }

            List<Map<String, Object>> raw =
                    (List<Map<String, Object>>) root.get("endpoints");
            if (raw == null) {
                throw new IllegalStateException("permission-manifest.yml 缺少 endpoints 段");
            }
            List<Entry> out = new ArrayList<>(raw.size());
            for (Map<String, Object> m : raw) {
                String method = String.valueOf(m.get("method"));
                String path = String.valueOf(m.get("path"));
                String perm = String.valueOf(m.get("permission"));
                boolean write = Boolean.TRUE.equals(m.get("write"));
                out.add(new Entry(method, path, perm, write, toPattern(path)));
            }
            return out;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析 permission-manifest.yml 失败", e);
        }
    }

    /** {@code /api/v1/x/{id}} ⇒ {@code ^/api/v1/x/[^/]+$}（{name} 允许含点，不允许跨段）。 */
    /**
     * 🔴 刻意 public：路径匹配语义是<b>安全边界</b>，必须有测试直接验证
     * 「{@code {id}} 不跨段」这一点。若它只是包级可见，
     * 唯一能测它的测试就得挪进同一个包 —— 那会诱使人为了测试而放宽可见性。
     */
    public static Pattern toPattern(String path) {
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        while (i < path.length()) {
            char c = path.charAt(i);
            if (c == '{') {
                int end = path.indexOf('}', i);
                if (end < 0) {
                    throw new IllegalStateException("清单路径的花括号未闭合：" + path);
                }
                String name = path.substring(i + 1, end);
                if (!name.matches("[A-Za-z0-9_]+")) {
                    throw new IllegalStateException("清单路径变量名非法：" + path);
                }
                sb.append("[^/]+");
                i = end + 1;
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        sb.append("$");
        return Pattern.compile(sb.toString());
    }

    /** 找到与该请求匹配的清单项；没有则返回 null（= 未登记）。 */
    public Entry find(String method, String uri) {
        for (Entry e : entries) {
            if (e.matches(method, uri)) {
                return e;
            }
        }
        return null;
    }

    /** 当前是否 fail-closed。批次 4 之前恒为 false。 */
    public boolean isEnforcing() {
        return enforcing;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }
}