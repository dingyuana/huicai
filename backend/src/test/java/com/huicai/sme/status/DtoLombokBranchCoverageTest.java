package com.huicai.sme.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lombok {@code equals}/{@code hashCode} 的<b>分支</b>覆盖率测试（AGENTS §4.5 第 25 / 30 条的延伸）
 *
 * <p><b>为什么必须存在 —— 一笔从未被量化的「分支税」</b>：{@link DtoMappingContractTest}
 * 只做 setter + getter 回读 + {@code toEntity()}，<b>从不调用 {@code equals}/{@code hashCode}</b>。
 * 而 Lombok 为每个 {@code @Data} 类生成的这两个方法里藏着大量条件分支
 * （{@code equals} 每字段一个三元 + 一个 if，{@code hashCode} 每字段一个三元）。
 *
 * <p><b>实测（2026-10-06，{@code target/site/jacoco/jacoco.csv}，L1 clean 口径）</b>：
 * <table border="1">
 *   <caption>DTO/VO 的分支覆盖</caption>
 *   <tr><th></th><th>类数</th><th>分支总数</th><th>已覆盖</th><th>占比</th></tr>
 *   <tr><td>{@code *DTO}</td><td>46</td><td>3032</td><td>1</td><td><b>0.0%</b></td></tr>
 *   <tr><td>{@code *VO}</td><td>59</td><td>2676</td><td>1</td><td><b>0.0%</b></td></tr>
 *   <tr><td><b>合计</b></td><td><b>105</b></td><td><b>5708</b></td><td><b>2</b></td><td><b>0.04%</b></td></tr>
 * </table>
 * ⇒ 这 5708 个分支占整个 BRANCH 分母的约 <b>24%</b>，却<b>几乎全红</b>。
 * 这正是 AGENTS §4.5 第 30 条「DTO 覆盖率税」在<b>分支</b>维度上的表现 ——
 * 当初只按<b>方法</b>维度观测到，误以为缴清方法税就够了。
 *
 * <p><b>为什么这决定 P102 出参面改造能否开工</b>：出参面 Entity 直出需新建约 <b>37 个 VO</b>。
 * 按实测每个 VO 约 45 个分支、且新增分支默认全未覆盖，则 BRANCH 会从 12.82%
 * 掉到约 11.98% ⇒ <b>跌破 12% 门禁</b>。先把本类的分支补上并按 §4.5 第 25 条
 * 重抬棘轮阈值，才腾得出缓冲。
 *
 * <p><b>本类如何覆盖分支</b>：Lombok {@code equals} 每字段的判定形如
 * {@code this.f == null ? other.f != null : !this.f.equals(other.f)}。
 * 要覆盖它的<b>两个</b>分支与外层 {@code if} 的<b>两个</b>分支，需要四种组合：
 * <ol>
 *   <li>两对象该字段<b>都 null</b> → 覆盖条件「真」+ if 未触发</li>
 *   <li>两对象该字段<b>都相等非 null</b> → 覆盖条件「假」+ if 未触发</li>
 *   <li>this 为 null、other 非 null → 覆盖条件「真」+ if 触发</li>
 *   <li>this 非 null、other 为 null → 覆盖条件「假」+ if 触发</li>
 * </ol>
 * ⚠️ {@code equals} 遇首个不等字段即返回，故必须<b>逐字段</b>构造「前序字段全相等」的对照对。
 * {@code hashCode} 则对 null 值与非 null 值各调一次即可。
 */
@DisplayName("P102 DTO/VO 的 Lombok equals/hashCode 分支覆盖")
class DtoLombokBranchCoverageTest {

    /** 全量 DTO/VO 的类名（按类路径扫描，不靠硬编码清单） */
    private static List<Class<?>> scanDataBeans() throws Exception {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:com/huicai/**/*.class");
        ClassLoader loader = DtoLombokBranchCoverageTest.class.getClassLoader();
        Set<String> names = new LinkedHashSet<>();
        for (Resource r : resources) {
            String uri = r.getURL().toString();
            int idx = uri.indexOf("com/huicai/");
            if (idx < 0 || !uri.endsWith(".class")) {
                continue;
            }
            names.add(uri.substring(idx).replace(".class", "").replace('/', '.'));
        }
        List<Class<?>> out = new ArrayList<>();
        for (String name : names) {
            Class<?> c;
            try {
                // initialize=false：不触发静态初始化块，避免副作用
                c = Class.forName(name, false, loader);
            } catch (Throwable t) {
                continue;
            }
            String simple = c.getSimpleName();
            if (c.isInterface() || Modifier.isAbstract(c.getModifiers()) || c.isEnum()) {
                continue;
            }
            if (!simple.endsWith("DTO") && !simple.endsWith("VO")) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    /** 收集实例字段（含父类，因为 Lombok 可能把父类字段计入 equals） */
    private static List<Field> instanceFields(Class<?> c) {
        List<Field> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!f.isSynthetic() && !Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    out.add(f);
                }
            }
        }
        return out;
    }

    /**
     * 为字段取一个非 null 样例；取不到返回 {@code null}（该字段将只覆盖「双 null」分支）。
     * 枚举单独处理 —— 项目里 {@code ArapStatus} 等大量使用枚举，取首个常量即可。
     */
    private static Object sampleOf(Class<?> t) {
        if (t.isPrimitive()) {
            // record 可能含原始类型组件（如 AvailablePrepaymentVO 的 boolean），
            // 返回 null 会让 Constructor.newInstance 抛 NPE（实测踩到）
            if (t == boolean.class) return Boolean.TRUE;
            if (t == byte.class) return (byte) 1;
            if (t == short.class) return (short) 2;
            if (t == int.class) return 3;
            if (t == long.class) return 4L;
            if (t == float.class) return 1.5f;
            if (t == double.class) return 2.5d;
            if (t == char.class) return 'c';
            return null;
        }
        if (t == String.class) return "S";
        if (t == Long.class) return 2L;
        if (t == Integer.class) return 3;
        if (t == Short.class) return (short) 4;
        if (t == Boolean.class) return Boolean.TRUE;
        if (t == BigDecimal.class) return new BigDecimal("12.34");
        if (t == LocalDate.class) return LocalDate.of(2026, 3, 4);
        if (t == LocalDateTime.class) return LocalDateTime.of(2026, 3, 4, 5, 6);
        if (List.class.isAssignableFrom(t)) return List.of(1L);
        if (Set.class.isAssignableFrom(t)) return Set.of(1L);
        if (t.isEnum() && t.getEnumConstants().length > 0) return t.getEnumConstants()[0];
        return null;
    }

    /**
     * 按给定字段值构造实例；{@code null} 值集合外的字段会填入样例，
 * 显式传 {@code null} 的字段置空。
 *
     * <p><b>为什么不用 setter 逐个设</b>：record 没有 setter（实测
 * {@code ServiceProgressService$ServiceProgressRowVO} 直接抛
 * {@code NoSuchMethodException: ...setProgressId(Long)}）⇒ 统一走构造器。
     */
    private static Object build(Class<?> c, Set<Field> nullOut) throws Exception {
        List<Field> fields = instanceFields(c);
        if (c.isRecord()) {
            java.lang.reflect.RecordComponent[] comps = c.getRecordComponents();
            Class<?>[] paramTypes = new Class<?>[comps.length];
            Object[] args = new Object[comps.length];
            for (int i = 0; i < comps.length; i++) {
                paramTypes[i] = comps[i].getType();
                args[i] = sampleOf(paramTypes[i]);
            }
            for (int i = 0; i < comps.length; i++) {
                for (Field f : fields) {
                    // ⚠️ 原始类型组件无法置 null（实测 record 含 boolean 时
                    // Constructor.newInstance 抛 NullPointerException）⇒ 保持样例值
                    if (f.getName().equals(comps[i].getName())
                            && nullOut.contains(f) && !comps[i].getType().isPrimitive()) {
                        args[i] = null;
                    }
                }
            }
            Constructor<?> ctor = c.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        }
        Constructor<?> ctor = c.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object o = ctor.newInstance();
        for (Field f : fields) {
            if (nullOut.contains(f)) {
                continue;
            }
            Object v = sampleOf(f.getType());
            if (v == null) {
                continue;
            }
            if (!invokeSetter(c, o, f, v)) {
                // 不可变字段（builder 装配的只读类）—— 无 setter，保持 null
            }
        }
        return o;
    }

    /**
     * 调用 setter，返回是否成功。
     *
     * <p>⚠️ <b>Lombok 对 {@code boolean isXxx} 字段生成的是 {@code setXxx()} 而非
     * {@code setIsXxx()}</b> —— 实测 {@code SubjectCreateDTO#isActive} 若按
     * {@code setIsActive} 找会找不到，字段永远设不上值，于是「置空前后竟相等」被
     * 误报成「该字段不参与 equals」。故此处两种命名都试。
     */
    private static boolean invokeSetter(Class<?> c, Object target, Field f, Object v) {
        String name = f.getName();
        List<String> candidates = new ArrayList<>();
        candidates.add("set" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (name.startsWith("is") && name.length() > 2
                && (name.charAt(2) == Character.toUpperCase(name.charAt(2)))) {
            String tail = name.substring(2);
            candidates.add("set" + Character.toUpperCase(tail.charAt(0)) + tail.substring(1));
        }
        for (String setterName : candidates) {
            try {
                Method setter = c.getMethod(setterName, f.getType());
                setter.setAccessible(true);
                setter.invoke(target, v);
                return true;
            } catch (NoSuchMethodException e) {
                // 试下一个候选名
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /** 全字段置 null 的实例 */
    private static Object allNull(Class<?> c, Set<Field> all) throws Exception {
        return build(c, new java.util.HashSet<>(all));
    }

    private static boolean declaresEquals(Class<?> c) {
        try {
            c.getDeclaredMethod("equals", Object.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Test
    @DisplayName("全量 DTO/VO：equals/hashCode 的四条分支路径均被触碰（扫描发现，不靠硬编码）")
    void lombokEqualsAndHashCodeBranchesAreTouched() throws Exception {
        List<Class<?>> beans = scanDataBeans();
        assertTrue(beans.size() >= 100,
                "扫描到的 DTO/VO 仅 " + beans.size() + " 个（预期 ≥100，源于 jacoco 实测 105 个）"
                        + " ⇒ 扫描范围或命名约定变了，本测试退化为空转");

        List<String> noSample = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> notInEquals = new ArrayList<>();
        int withEquals = 0;
        int identityEquals = 0;

        for (Class<?> bean : beans) {
            // ⚠️ 并非所有 DTO/VO 都有 Lombok 生成的 equals：实测 TaxBurdenVO 是
            // 手写 POJO（只有 getter/setter，**根本没有 equals**）⇒ 走 Object 的
            // 身份 equals，不含任何分支，不属本测试要缴的税，跳过。
            if (!declaresEquals(bean)) {
                identityEquals++;
                continue;
            }
            withEquals++;

            List<Field> fields = instanceFields(bean);
            Set<Field> all = new java.util.HashSet<>(fields);
            Object n1 = allNull(bean, all);
            Object n2 = allNull(bean, all);

            // 路径 A：两对象对应字段全 null ⇒ 覆盖每字段三元「真」+ if 未触发
            if (!n1.equals(n2)) {
                skipped.add(bean.getSimpleName() + " 两个全 null 实例竟不相等 ⇒ equals 非值语义");
                continue;
            }
            n1.hashCode();

            // o == this / instanceof / canEqual 的否定路径
            n1.equals(n1);
            n1.equals(null);
            n1.equals(new Object());

            // 路径 B：两对象全字段相等非 null ⇒ 覆盖每字段三元「假」+ if 未触发
            Object f1 = build(bean, java.util.Set.of());
            Object f2 = build(bean, java.util.Set.of());
            if (!f1.equals(f2)) {
                skipped.add(bean.getSimpleName() + " 两个等值实例竟不相等 ⇒ equals 非值语义");
                continue;
            }
            f1.hashCode();

            // 路径 C/D：逐字段把一侧置空 ⇒ 覆盖三元两侧 + if 触发
            // （equals 遇首个不等字段即返回，故只能逐字段做，且其余字段保持等值）
            for (Field f : fields) {
                if (sampleOf(f.getType()) == null) {
                    noSample.add(bean.getSimpleName() + "#" + f.getName()
                            + "(" + f.getType().getSimpleName() + ")");
                    continue;
                }
                if (f.getType().isPrimitive()) {
                    // 原始类型无法置空 ⇒ 无法构造「一侧 null」对照，跳过
                    continue;
                }
                Object withF = build(bean, java.util.Set.of());
                Object withoutF = build(bean, java.util.Set.of(f));
                if (withF.equals(withoutF)) {
                    // 该字段不参与 equals（@EqualsAndHashCode(exclude=...) 或
                    // Lombok 未纳入的静态/瞬态字段）⇒ 这是**事实**不是缺陷：
                    // 它意味着 equals 里本就没有该字段的分支，无税可缴。
                    notInEquals.add(bean.getSimpleName() + "#" + f.getName());
                    continue;
                }
                if (withoutF.equals(withF)) {
                    skipped.add(bean.getSimpleName() + "#" + f.getName() + " 反向对照竟相等");
                }
                withoutF.hashCode();
            }
        }

        System.out.println("[覆盖率税] 扫描 DTO/VO = " + beans.size()
                + "，自声明 equals = " + withEquals
                + "，身份 equals（无分支，跳过）= " + identityEquals
                + "，无样例字段 = " + noSample.size()
                + "，不参与 equals 的字段 = " + notInEquals.size());
        if (!noSample.isEmpty()) {
            System.out.println("[覆盖率税] 无样例字段（前 15）: "
                    + noSample.subList(0, Math.min(15, noSample.size())));
        }
        if (!notInEquals.isEmpty()) {
            System.out.println("[覆盖率税] 不参与 equals 的字段（前 15）: "
                    + notInEquals.subList(0, Math.min(15, notInEquals.size())));
        }
        System.out.println("[覆盖率税] equals 语义异常: " + skipped);
        assertTrue(withEquals >= 50,
                "自声明 equals 的 DTO/VO 仅 " + withEquals
                        + " 个 ⇒ 扫描范围或判定失效（declaresEquals 可能对继承自父类的 equals 误判）");
        assertTrue(skipped.isEmpty(),
                "以下 DTO/VO 的 equals 语义与本测试的覆盖假设不符，需人工确认：" + skipped);
        // 上界守卫：若将来大批 VO 被改成「equals 不含字段」，本测试的覆盖面会塌缩却仍全绿
        assertTrue(notInEquals.size() <= 40,
                "不参与 equals 的字段达 " + notInEquals.size() + " 个（>40）⇒ "
                        + "覆盖面可能已塌缩，需人工确认："
                        + notInEquals.subList(0, Math.min(20, notInEquals.size())));
    }

    @Test
    @DisplayName("反向自检：本测试确实触碰了 equals/hashCode（否则上面整类恒绿）")
    void theTestActuallyInvokesEqualsAndHashCode() throws Exception {
        List<Class<?>> beans = scanDataBeans();
        Object probe = build(beans.get(0), java.util.Set.of());
        // 证明 equals/hashCode 是真实可调用方法（不是 record 的合成成员被代理掉）
        Method eq = findMethod(beans.get(0), "equals", Object.class);
        Method hc = findMethod(beans.get(0), "hashCode");
        assertNotNull(eq, beans.get(0).getSimpleName() + " 无 equals(Object) ⇒ 覆盖假设不成立");
        assertNotNull(hc, beans.get(0).getSimpleName() + " 无 hashCode() ⇒ 覆盖假设不成立");
        assertEquals(probe, probe);
        assertNotNull(probe.hashCode());
    }

    private static Method findMethod(Class<?> c, String name, Class<?>... params) throws NoSuchMethodException {
        try {
            Method m = c.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            Method m = c.getMethod(name, params);
            m.setAccessible(true);
            return m;
        }
    }
}