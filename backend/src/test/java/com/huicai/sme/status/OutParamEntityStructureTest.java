package com.huicai.sme.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P102 出参面铁律 #13 的结构守卫：**Controller 不得直出 Entity**
 *
 * <h2>为什么必须有它</h2>
 * 同目录 {@link TenantDtoIsolationStructureTest#noEntityRequestBodyAnywhere} 已把
 * 「写端点直收 {@code @RequestBody Entity}」打成零容忍；姊妹守卫
 * {@link MasterDataVoContractTest} 只检查「已登记进 VO_CONTRACTS 的那些 VO」，
 * 一个从没登记过的 {@code R&lt;XxxEntity>} 照样全绿（反证实测 2026-10-09）。
 *
 * <h2>棘轮史（66 → 0，已清零）</h2>
 * 存量实测 66 个返回点 / 11 个控制器（2026-10-09）：批次 14 −8 → 58；
 * 批次 15 −13 → 45；批次 16（ReconciliationReport）−3 → 42；
 * 批次 17（BankStatement 8 / Budget 9 / Reconciliation 10 / Tax 15）−42 → <b>0</b>。
 * 棘轮时代每一步都是判红后才同步基线；清零后本例升级为<b>零容忍</b>：
 * 扫描全仓 {@code @RestController} 全部方法返回类型的所有泛型实参（R / IPage / List），
 * 只要出现任何以 {@code Entity} 结尾的类即判红。
 *
 * <h2>反向自检的换代</h2>
 * 棘轮时代的自检是「扫描结果必须非空」；清零后改为<b>验证扫描/反射管线本身仍然工作</b>：
 * ①扫描宇宙必须包含已 VO 化的已知控制器；②抽样方法（BankStatementController#detail）
 * 的返回泛型实参必须解析成 VO 结尾的类名 —— 否则「扫描失效 → 恒空 → 假绿」无人知晓。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 * @see AGENTS.md §4.5 第 31 条（守卫的判定依据必须覆盖违规的全部形态）
 */
@DisplayName("P102 出参面：Controller 不得直出 Entity（零容忍，棘轮已清零）")
class OutParamEntityStructureTest {

    @Test
    @DisplayName("全仓扫描：Controller 的返回类型必须为 0 个 Entity 直出")
    void noEntityInControllerReturnTypes() throws Exception {
        Map<String, Integer> byController = scanEntityReturnPoints();

        assertTrue(byController.isEmpty(),
                "Entity 出参返回点必须为 0，但扫到 " + byController
                        + " ⇒ 新增/回退了 Entity 直出。请改用 VO"
                        + "（参考已完成批次的 from() 模式，见 ReconciliationLogVO 等）");
    }

    @Test
    @DisplayName("管线自检：扫描器能找到已知控制器且反射能解析 VO 泛型实参（防假绿）")
    void scanPipelineStillWorks() throws Exception {
        Set<String> universe = scannedControllerNames();
        assertTrue(universe.contains("BankStatementController")
                        && universe.contains("TaxController")
                        && universe.contains("BudgetController"),
                "扫描宇宙缺已知控制器 ⇒ 扫描失效，本类全部断言都是假绿。宇宙大小="
                        + universe.size());

        Method detail = methodsOf("BankStatementController").stream()
                .filter(m -> m.getName().equals("detail"))
                .findFirst().orElseThrow(
                        () -> new AssertionError("BankStatementController#detail 不存在 ⇒ 反射自检失效"));
        List<Type> args = List.copyOf(allTypeArgs(detail.getGenericReturnType()));
        assertTrue(!args.isEmpty(),
                "R<BankStatementVO> 的泛型实参一个都没解析到 ⇒ 泛型解析失效");
        assertTrue(args.stream().anyMatch(t ->
                        t instanceof Class<?> c && c.getSimpleName().endsWith("VO")),
                "detail() 的返回泛型实参不是 VO 结尾：" + args + " ⇒ 该端点疑似被改成 Entity 直出");
    }

    private static Map<String, Integer> scanEntityReturnPoints() throws Exception {
        Map<String, Integer> hits = new java.util.TreeMap<>();
        for (Map.Entry<String, Class<?>> e : scanControllerClasses().entrySet()) {
            int n = 0;
            for (Method m : e.getValue().getDeclaredMethods()) {
                for (Type t : allTypeArgs(m.getGenericReturnType())) {
                    if (t instanceof Class<?> c && isEntity(c)) {
                        n++;
                    }
                }
            }
            if (n > 0) {
                hits.put(e.getKey(), n);
            }
        }
        return hits;
    }

    private static Set<String> scannedControllerNames() throws Exception {
        return scanControllerClasses().keySet();
    }

    private static Map<String, Class<?>> scanControllerClasses() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Map<String, Class<?>> out = new java.util.TreeMap<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.huicai")) {
            Class<?> ctrl = Class.forName(bd.getBeanClassName());
            out.put(ctrl.getSimpleName(), ctrl);
        }
        return out;
    }

    private static List<Method> methodsOf(String simpleName) throws Exception {
        Class<?> c = scanControllerClasses().get(simpleName);
        return c == null ? List.of() : List.of(c.getDeclaredMethods());
    }

    /** 递归收集返回类型里出现的所有泛型实参（R&lt;X&gt; / IPage&lt;X&gt; / List&lt;X&gt; …） */
    private static Set<Type> allTypeArgs(Type t) {
        Set<Type> out = new java.util.LinkedHashSet<>();
        Deque<Type> queue = new ArrayDeque<>();
        queue.add(t);
        while (!queue.isEmpty()) {
            Type cur = queue.poll();
            if (cur instanceof ParameterizedType pt) {
                for (Type arg : pt.getActualTypeArguments()) {
                    out.add(arg);
                    queue.add(arg);
                }
            }
        }
        return out;
    }

    /**
     * 判定「这是不是 Entity 直出」。判据不能沿父类链（历史实测：
     * {@code ArapSettlementVO extends ArapSettlementEntity} 会被误判），
     * 只看类型自身名字：Entity 一律以 {@code Entity} 结尾，VO/DTO 一律以
     * {@code VO}/{@code DTO} 结尾，两者互斥，无歧义。
     */
    private static boolean isEntity(Class<?> c) {
        return c.getSimpleName().endsWith("Entity");
    }
}
