package com.huicai.security;

import com.huicai.common.security.PermissionManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P154 项④：权限清单结构守卫。
 *
 * <p>根因：历史提交把真端点（salary/asset-changes 共 11 条）误追加到 {@code excluded:}
 * 段之后，而 {@link PermissionManifest} 只加载 {@code endpoints:} 段 ⇒ 这些端点从未
 * 被拦截器登记执行（enforcing 模式下仍 warn 放行），即「哑火」表象的真正来源
 * ——不是拦截器有 bug，是清单段错位导致漏登记。</p>
 *
 * <p>本守卫锁死清单文件的<b>结构不变量</b>（不依赖运行时 bean，纯解析 YAML 事实）：</p>
 * <ol>
 *   <li>{@code excluded:} 段任何条目都不得含 {@code permission} 键（含即段错位）；</li>
 *   <li>{@code endpoints:} 段每条都必须含 {@code permission} 键（缺即漏配）；</li>
 *   <li>清单加载后端点数 &ge; 阈值棘轮（只许增不许减，防止再次整段落错丢失）。</li>
 * </ol>
 */
@DisplayName("SPEC-P154 项④：权限清单结构守卫——excluded/endpoints 段职责分离")
class PermissionManifestStructureTest {

    /** 当前 endpoints 段条目数基线（只许增不许减；段错位会导致骤减）。 */
    private static final int ENDPOINTS_BASELINE = 283;

    @SuppressWarnings("unchecked")
    private Map<String, Object> load() throws Exception {
        try (InputStream in = new org.springframework.core.io.ClassPathResource("permission-manifest.yml")
                .getInputStream()) {
            return new Yaml().load(in);
        }
    }

    @Test
    @DisplayName("excluded 段不得含 permission 键（真端点误落此段即静默漏登记）")
    @SuppressWarnings("unchecked")
    void excludedMustNotCarryPermissionKey() throws Exception {
        List<Map<String, Object>> excluded = (List<Map<String, Object>>) load().get("excluded");
        if (excluded == null) return; // 无 excluded 段视为通过
        for (int i = 0; i < excluded.size(); i++) {
            Map<String, Object> e = excluded.get(i);
            assertTrue(!e.containsKey("permission"),
                    "excluded 段第 " + (i + 1) + " 条含 permission 键（" + e.get("method") + " "
                            + e.get("path") + "）—— 真端点必须在 endpoints 段，误落 excluded 会被加载器丢弃");
        }
    }

    @Test
    @DisplayName("endpoints 段每条都必须含 permission 键")
    @SuppressWarnings("unchecked")
    void endpointsMustAllCarryPermissionKey() throws Exception {
        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) load().get("endpoints");
        assertTrue(endpoints != null && !endpoints.isEmpty(), "endpoints 段不得为空");
        for (int i = 0; i < endpoints.size(); i++) {
            Map<String, Object> e = endpoints.get(i);
            assertTrue(e.get("permission") != null,
                    "endpoints 段第 " + (i + 1) + " 条缺 permission 键（" + e.get("method") + " "
                            + e.get("path") + "）");
        }
    }

    @Test
    @DisplayName("endpoints 条目数棘轮：只许增不许减（段错位会导致加载数骤减）")
    @SuppressWarnings("unchecked")
    void endpointsCountRatchet() throws Exception {
        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) load().get("endpoints");
        assertTrue(endpoints.size() >= ENDPOINTS_BASELINE,
                "endpoints 段条目数 " + endpoints.size() + " < 基线 " + ENDPOINTS_BASELINE
                        + " —— 可能有端点被误移到 excluded 段（加载器只读 endpoints，会静默漏登记）");
    }

    @Test
    @DisplayName("清单能被 PermissionManifest 成功加载（结构合法，不抛异常）")
    void manifestLoadsCleanly() {
        assertDoesNotThrow(() -> {
            PermissionManifest m = new PermissionManifest();
            m.loadForTest();
            assertTrue(m.size() >= ENDPOINTS_BASELINE, "加载后端点数应达基线");
        });
    }

    @Test
    @DisplayName("曾误落的端点已在 endpoints 段可命中（回归锁：salary-bills / asset-changes）")
    void previouslyMisplacedEndpointsNowResolve() {
        PermissionManifest m = new PermissionManifest();
        m.loadForTest();
        // 这几条正是历史上被追加到 excluded 段之后、加载器读不到的条目
        assertTrue(m.find("GET", "/api/sme/salary/v1/salary-bills") != null,
                "GET salary-bills 必须在 endpoints 段可命中");
        assertTrue(m.find("POST", "/api/sme/salary/v1/salary-bills") != null,
                "POST salary-bills 必须在 endpoints 段可命中");
        assertTrue(m.find("POST", "/api/sme/salary/v1/salary-bills/1/confirm") != null,
                "POST salary-bills/confirm 必须在 endpoints 段可命中");
        assertTrue(m.find("POST", "/api/sme/asset/v1/asset-changes") != null,
                "POST asset-changes 必须在 endpoints 段可命中");
    }

    @Test
    @DisplayName("SPEC-P157 字面量优先：/my 不被 {id} 吞（自助端点命中 salary:self:view）")
    void literalPathWinsOverWildcard() {
        PermissionManifest m = new PermissionManifest();
        m.loadForTest();

        // GET /my 必须命中 /my 条目（salary:self:view），而非 /{id}（salary:list）
        PermissionManifest.Entry my = m.find("GET", "/api/sme/salary/v1/salary-bills/my");
        assertTrue(my != null, "/my 必须可命中");
        assertEquals("salary:self:view", my.permission(),
                "/my 不得被 /{id}（[^/]+）吞掉 —— 字面量路径优先于通配段");

        // GET /my/1/items 同理
        PermissionManifest.Entry myItems = m.find("GET", "/api/sme/salary/v1/salary-bills/my/1/items");
        assertTrue(myItems != null);
        assertEquals("salary:self:view", myItems.permission(), "/my/{id}/items 命中自助码");

        // 回归面：普通数字 id 仍命中 {id} 条目（salary:list）
        PermissionManifest.Entry byId = m.find("GET", "/api/sme/salary/v1/salary-bills/1");
        assertTrue(byId != null && "salary:list".equals(byId.permission()),
                "数字 id 仍走 /{id} 条目");

        // 反证锚：/my 若走 /{id} 条目，此断言即红（守卫真实参战）
        assertNotEquals("/api/sme/salary/v1/salary-bills/{id}", my.path(),
                "/my 命中的不得是 {id} 模板路径");
    }

    @Test
    @DisplayName("SPEC-P157 字面量优先（合成条目，顺序无关）：{id} 在前也必须让位字面量")
    void literalWinsRegardlessOfOrder() {
        PermissionManifest m = new PermissionManifest();
        // 合成清单：{id} 条目故意排在字面量 /my 之前（生产清单顺序恰好侥幸，
        // 该守卫锁的是匹配语义本身，防未来条目重排/新同类路径踩坑）
        java.util.List<PermissionManifest.Entry> seeded = java.util.List.of(
                new PermissionManifest.Entry("GET", "/api/x/v1/objs/{id}", "x:one", false,
                        PermissionManifest.toPattern("/api/x/v1/objs/{id}")),
                new PermissionManifest.Entry("GET", "/api/x/v1/objs/my", "x:self", false,
                        PermissionManifest.toPattern("/api/x/v1/objs/my")));
        m.injectEntriesForTest(seeded);

        PermissionManifest.Entry hit = m.find("GET", "/api/x/v1/objs/my");
        assertTrue(hit != null && "x:self".equals(hit.permission()),
                "{id} 排前时 /my 仍须命中字面量条目（旧首中逻辑在此必红）");
        assertEquals("/api/x/v1/objs/my", hit.path());
    }
}
