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
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P102 出参面铁律 #13 的结构守卫：**Controller 不得直出 Entity**
 *
 * <h2>为什么必须有它 —— 入参已零容忍，出参却是空白</h2>
 * 同目录的 {@link TenantDtoIsolationStructureTest#noEntityRequestBodyAnywhere}
 * 已把「写端点直收 {@code @RequestBody Entity}」打成**零容忍**（例外清单恒为空）。
 * 但**出参面**此前没有任何等价守卫：本类的姊妹守卫
 * {@link MasterDataVoContractTest} 只检查「**已登记进 VO_CONTRACTS 的**那些 VO」，
 * 也就是说 —— <b>一个从没登记过的 {@code R&lt;XxxEntity&gt; 照样全绿</b>。
 * 反证实测（2026-10-09）：把 {@code AssetDisposalController} 退回
 * {@code R&lt;AssetDisposalEntity&gt;} 后，{@code MasterDataVoContractTest} 与
 * {@code TenantDtoIsolationStructureTest} <b>两个守卫都 exit 0</b>。
 * ⇒ 即「66 个 Entity 出参返回点」在改造完成前<b>完全没有回归保护</b>，
 * 下次有人新写一个 {@code R&lt;XxxEntity&gt;} 不会被任何人发现。
 *
 * <h2>为什么用棘轮而不是零容忍</h2>
 * 存量实测 <b>66 个返回点 / 11 个控制器</b>（2026-10-09）。
 * 若直接零容忍，本类一上线就长期红 —— 恒红等于没有信号（AGENTS §4.5 第 21 条）。
 * 故改为与覆盖率/接口覆盖同款的<b>棘轮</b>：上限钉在实测值，<b>每清一处必须同步下调</b>。
 * 与覆盖率阈值的区别：覆盖率数值是「浮动的估计」，这里的上限是<b>可枚举的离散计数</b>，
 * 因此上调必须极有理由 —— <b>只许下不许上</b>。
 *
 * <p>⚠️ <b>上限下调纪律</b>：改 VO 时顺手把 {@link #MAX_ENTITY_RETURN_POINTS} -1；
 * 存量清零后把它改成 {@code 0} 并删掉 {@link #ENTITY_BASELINE}，
 * 本类即升级为真正的零容忍守卫（届时把断言从「不超过基线」改为「必须为空」）。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 * @see AGENTS.md §4.5 第 31 条（守卫的判定依据必须覆盖违规的全部形态）
 */
@DisplayName("P102 出参面：Controller 不得直出 Entity（棘轮）")
class OutParamEntityStructureTest {

/**
     * 存量基线（2026-10-09 实测）
     *
     * <p>⚠️ 逐点对应关系见 AGENTS §0；本清单只登记控制器名，
     * 供「哪个控制器还没改」一眼可见——断言用的是<b>返回点总数</b>而非控制器数。
     *
     * <p><b>批次 14 已清零 {@code AssetDisposalController} 与
     * {@code AssetInventoryController}</b>（各 4 处，共 8 处）。
     * <p><b>批次 15 已清零 {@code ArapSettlementController}（4 处）、
     * {@code BadDebtController}（5 处）、{@code CustomerStatementController}（3 处）、
     * {@code BusinessDocController}（1 处）</b> —— 共 13 处。
     * 这 4 条是 {@link #baselineMatchesActual} <b>判红后</b>才同步的，
     * 不是手工猜测：只有真的把返回点改成 VO，它才会转绿。
     */
    private static final Set<String> ENTITY_BASELINE = Set.of(
            "BankStatementController", "BudgetController",
            "ReconciliationController", "ReconciliationReportController", "TaxController");

    /**
     * 棘轮上限。初始 = 66（2026-10-09 实测）。
     * 批次 14 清出 8 处后下调至 58，<b>批次 15 再清出 13 处 ⇒ 45</b>。
     *
     * <p>⚠️ 上调必须极有理由——<b>只许下不许上</b>；存量清零后把它改成 {@code 0}
     * 并删 {@link #ENTITY_BASELINE}，本类即升级为零容忍守卫。
     */
    private static final int MAX_ENTITY_RETURN_POINTS = 45;

    @Test
    @DisplayName("全仓扫描：Controller 不得直出 Entity（棘轮 = 存量基线）")
    void noEntityInControllerReturnTypes() throws Exception {
        java.util.Map<String, Integer> byController = scanEntityReturnPoints();

        int total = byController.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(total <= MAX_ENTITY_RETURN_POINTS,
                "Entity 出参返回点共 " + total + " 处，超过棘轮上限 " + MAX_ENTITY_RETURN_POINTS
                        + " 处 ⇒ 新增了 Entity 直出。分布：" + byController
                        + "。请改用 VO（参考已完成批次的 from() 模式）");
    }

    @Test
    @DisplayName("反向自检：守卫能识别真正的 Entity 直出（否则上面是假绿）")
    void guardDetectsRealEntityReturnType() throws Exception {
        // 前置：本类自身所在包外，至少有一个已知 Entity 直出的控制器；
        // 扫描结果里必须能命中 AssetDisposalController 之外的存量控制器，
        // 且命中的类型确实以 Entity 结尾 —— 两者缺一即说明扫描或命名判定失效。
        java.util.Map<String, Integer> byController = scanEntityReturnPoints();
        assertTrue(!byController.isEmpty(),
                "一个 Entity 出参返回点都没扫到 ⇒ 扫描失效，本类全部断言都是假绿");

        // 逐个核验命中项：反射到的泛型实参必须真以 Entity 结尾
        int verified = 0;
        for (String ctrlName : byController.keySet()) {
            for (Method m : methodsOf(ctrlName)) {
                for (Type t : allTypeArgs(m.getGenericReturnType())) {
                    if (t instanceof Class<?> c && c.getSimpleName().endsWith("Entity")) {
                        verified++;
                    }
                }
            }
        }
        assertTrue(verified > 0,
                "扫到 " + byController.size() + " 个控制器但一个 Entity 泛型实参都没核验到"
                        + " ⇒ 命名判定或反射逻辑失效");
    }

    @Test
    @DisplayName("基线清单与实际存量一致（防止有人改 Controller 却忘了改清单）")
    void baselineMatchesActual() throws Exception {
        java.util.Map<String, Integer> byController = scanEntityReturnPoints();
        Set<String> actual = new TreeSet<>(byController.keySet());
        Set<String> baseline = new TreeSet<>(ENTITY_BASELINE);

        // 已从存量中消失的控制器必须从基线移除（否则基线会掩盖「真的清干净了」）
        Set<String> gone = new TreeSet<>(baseline);
        gone.removeAll(actual);
        assertTrue(gone.isEmpty(),
                "基线里登记但实际已无 Entity 出参的控制器 " + gone
                        + " ⇒ 请同步删除基线条目（存量真的清掉了是好消息）");

        // 反向：新出现的控制器必须补进基线，否则会「悄悄增加存量」
        Set<String> added = new TreeSet<>(actual);
        added.removeAll(baseline);
        assertTrue(added.isEmpty(),
                "新出现 Entity 直出的控制器 " + added
                        + " ⇒ 已 VO 化的模块不应回退，或新模块直接用了 Entity 出参");
    }

    // ── 扫描实现 ──

    private static java.util.Map<String, Integer> scanEntityReturnPoints() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        java.util.Map<String, Integer> hits = new java.util.TreeMap<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.huicai")) {
            Class<?> ctrl = Class.forName(bd.getBeanClassName());
            int n = 0;
            for (Method m : ctrl.getDeclaredMethods()) {
                for (Type t : allTypeArgs(m.getGenericReturnType())) {
                    if (t instanceof Class<?> c && isEntity(c)) {
                        n++;
                    }
                }
            }
            if (n > 0) {
                hits.put(ctrl.getSimpleName(), n);
            }
        }
        return hits;
    }

    private static Method[] methodsOf(String simpleName) throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (BeanDefinition bd : scanner.findCandidateComponents("com.huicai")) {
            Class<?> c = Class.forName(bd.getBeanClassName());
            if (c.getSimpleName().equals(simpleName)) {
                return c.getDeclaredMethods();
            }
        }
        return new Method[0];
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
     * 判定「这是不是 Entity 直出」。
     *
     * <p><b>⚠️ 判据为什么不能沿父类链</b>（本轮实测踩到）：初版沿父类链找
     * {@code *Entity} 后缀，结果把 {@link com.huicai.base.business.dto.vo.ArapSettlementVO}
     * 判成了 Entity —— 因为它 {@code extends ArapSettlementEntity}。
     * 于是实测点数 60 而真实存量只有 58，虚高 2 处。
     *
     * <p><b>顺带暴露的真缺陷</b>（已另行修正）：{@code ArapSettlementVO extends
     * ArapSettlementEntity} 意味着它<b>继承了 Entity 的全部字段</b>
     * （{@code deleted / enterpriseId / version / createdBy …}）
     * ⇒ 该 VO 名义上做了隔离、实际上<b>内部字段照样外露</b>，铁律 #13 并未真正收口。
     * 这正是 {@code MasterDataVoContractTest} 的盲区：它用
     * {@code getDeclaredFields()}，<b>看不见继承来的字段</b>（AGENTS §4.5 第 16 条
     * 「反向缺口」在测试侧的同型 —— 扫描口径覆盖不全）。
     *
     * <p>⇒ 故此处只看<b>类型自身的名字</b>：项目约定 Entity 一律以 {@code Entity} 结尾，
     * VO/DTO 一律以 {@code VO}/{@code DTO} 结尾，两者互斥，无歧义。
     */
    private static boolean isEntity(Class<?> c) {
        return c.getSimpleName().endsWith("Entity");
    }
}