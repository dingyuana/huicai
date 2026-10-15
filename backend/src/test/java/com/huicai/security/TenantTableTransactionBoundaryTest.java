package com.huicai.security;

import com.huicai.sme.cash.service.impl.BankStatementExcelImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P127 / REQ-2026-143：租户表<b>事务边界</b>守卫。
 *
 * <p><b>要守的不变量</b>：凡<b>直接访问 Mapper</b> 的业务方法，必须被事务覆盖
 * （类级或方法级 {@code @Transactional}）。
 *
 * <p><b>为什么这条不变量是安全底线</b>：企业隔离开关 {@code app.enterprise_id}
 * 由 {@code TenantGucSetter} 用 {@code SET LOCAL} 写入，而 {@code SET LOCAL}
 * <b>只在事务内有效</b>，且 {@code apply()} 首行即 {@code if (!isActualTransactionActive()) return;}。
 * 无事务 ⇒ GUC 恒为 NULL ⇒ RLS 谓词 {@code enterprise_id = NULL} 恒不成立：
 * <ul>
 *   <li>INSERT/UPDATE/DELETE ⇒ 抛 {@code new row violates row-level security policy}</li>
 *   <li>SELECT ⇒ <b>静默返 0 行</b>（最危险：看起来像业务数据本来就没有）</li>
 * </ul>
 * 生产实证：{@code BankStatementExcelImportService} 全类无事务 ⇒ 银行对账单导入
 * 每一行都被拒，{@code t_bank_statement} 恒为 0 行，该功能从未成功导入过。
 *
 * <p>🔴 <b>本守卫为何用字节码而非正则</b>：首版扫描脚本用「缩进 4 空格的正则 + 括号计数」
 * 切分方法，<b>自检失败</b> —— 已知真阳性 {@code confirmImport} 都没被扫出来
 * （该文件存在顶格缩进的异常行，括号计数失配）。同一脚本还漏判了标准写法的
 * {@code @Transactional}（注解独占一行），候选数一度虚高 17→6。
 * ⇒ <b>工具读数对实现细节过度敏感时不可作为存量清单</b>。改用 ASM 读指令流，
 * 不受缩进与括号影响。
 *
 * <p>🔴 <b>守卫必须先自证对真阳性敏感</b>（AGENTS §4.5 第 21 条）：
 * {@link #scannerDetectsTheKnownMapperTouchingMethod()} 先断言扫描装置
 * <b>确实能</b>识别出 {@code confirmImport} 引用了 Mapper；该用例失败即说明装置坏了，
 * 此时下面那些「通过」全是假绿。
 */
@DisplayName("SPEC-P127 租户表事务边界守卫")
class TenantTableTransactionBoundaryTest {

    /** 与 {@code TenantRlsInitializer} 保持逐字一致；不一致则本守卫评估的不是真实切面。 */
    private static final String ASPECT_EXPRESSION =
            "@annotation(org.springframework.transaction.annotation.Transactional) "
                    + "|| @within(org.springframework.transaction.annotation.Transactional)";

    private AspectJExpressionPointcut pointcut() {
        AspectJExpressionPointcut pc = new AspectJExpressionPointcut();
        pc.setExpression(ASPECT_EXPRESSION);
        return pc;
    }

    // ------------------------------------------------------------------
    // 装置自证：先证明扫描器与切面求值器本身有效，否则后续绿灯无意义
    // ------------------------------------------------------------------

    /**
     * 真阳性自证：{@code confirmImport} 必须被扫描装置识别为「引用了 Mapper」。
     * 若此用例失败 ⇒ 装置失效 ⇒ 本类其余断言全部无效。
     */
    @Test
    @DisplayName("装置自证：扫描器能识别出 confirmImport 引用了 Mapper（真阳性）")
    void scannerDetectsTheKnownMapperTouchingMethod() throws Exception {
        Method confirmImport =
                BankStatementExcelImportService.class.getMethod("confirmImport", String.class);
        assertTrue(scannerDetectsMapperReference(confirmImport),
                "扫描装置失效：未能识别 confirmImport 对 Mapper 的引用"
                        + "（真阳性都扫不出，则本守卫其余用例全是假绿）");
    }

    /** 对照组：切面求值器对「自身声明事务的方法」必须命中，否则是装置问题。 */
    @Test
    @DisplayName("对照：切面求值器对自身声明 @Transactional 的方法命中")
    void aspectMatchesTransactionalMethod() throws Exception {
        Method confirmImport =
                BankStatementExcelImportService.class.getMethod("confirmImport", String.class);
        assertTrue(pointcut().matches(confirmImport, BankStatementExcelImportService.class),
                "切面求值装置无效：连自身声明 @Transactional 的方法都不命中");
    }

    // ------------------------------------------------------------------
    // 核心不变量
    // ------------------------------------------------------------------

    /**
     * 老丁裁定 D-1 选项 1 的直接落点：导入确认必须开事务。
     *
     * <p>反证方式：把该注解去掉，本用例立即转红（ATOMIC 场景下另见
     * {@link #importMustRollbackForCheckedException()}）。
     */
    @Test
    @DisplayName("confirmImport 必须标注 @Transactional（否则 RLS 拒绝全部行）")
    void confirmImportMustBeTransactional() throws Exception {
        Method m = BankStatementExcelImportService.class.getMethod("confirmImport", String.class);
        Transactional tx = AnnotatedElementUtils.findMergedAnnotation(m, Transactional.class);
        assertNotNull(tx,
                "confirmImport 没有事务：SET LOCAL app.enterprise_id 失效 ⇒ GUC 为 NULL ⇒ "
                        + "每行 INSERT 都被 RLS 拒绝（new row violates row-level security policy）");
    }

    /**
     * 铁律 #11 + D-2 选项 A（整批原子）：必须 {@code rollbackFor = Exception.class}，
     * 否则受检异常会「提交已插入的部分行」，形成半截进账。
     */
    @Test
    @DisplayName("confirmImport 必须 rollbackFor = Exception.class（整批原子，D-2-A）")
    void importMustRollbackForCheckedException() throws Exception {
        Method m = BankStatementExcelImportService.class.getMethod("confirmImport", String.class);
        Transactional tx = AnnotatedElementUtils.findMergedAnnotation(m, Transactional.class);
        assertNotNull(tx, "confirmImport 没有事务注解，前置不变量已破");
        assertTrue(Arrays.asList(tx.rollbackFor()).contains(Exception.class),
                "confirmImport 必须 rollbackFor = Exception.class："
                        + "否则受检异常提交已插入的行 ⇒ 半截进账，违反 D-2-A 整批原子");
    }

    /**
     * 类级不变量：{@code BankStatementExcelImportService} 中<b>任何</b>直接访问 Mapper 的
     * public 方法都必须被事务覆盖。
     *
     * <p>本条是「存量清零」的棘轮：现在该类已无违规方法；将来有人新增一个直接调
     * {@code statementMapper.*} 的 public 方法且忘了事务，本条立即转红。
     */
    @Test
    @DisplayName("类级不变量：所有直接访问 Mapper 的 public 方法都被事务覆盖")
    void everyMapperTouchingMethodIsTransactional() {
        List<String> violations = new ArrayList<>();
        for (Method m : BankStatementExcelImportService.class.getDeclaredMethods()) {
            if (m.isSynthetic() || !java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;
            if (!scannerDetectsMapperReference(m)) continue;
            Transactional tx = AnnotatedElementUtils.findMergedAnnotation(m, Transactional.class);
            Transactional clsTx = AnnotatedElementUtils.findMergedAnnotation(
                    BankStatementExcelImportService.class, Transactional.class);
            if (tx == null && clsTx == null) violations.add(m.getName());
        }
        assertTrue(violations.isEmpty(),
                "以下方法直接访问 Mapper 却无事务边界，RLS 下会失败或静默丢数据："
                        + violations + "（SPEC-P127 D-1 选项 1）");
    }

    // ------------------------------------------------------------------
    // 装置实现：ASM 读指令流，判定方法是否引用了 *Mapper 字段/方法
    // （只用 ClassVisitor/MethodVisitor —— Spring 只带 ASM 核心，不含 tree API）
    // ------------------------------------------------------------------

    private static Set<String> mapperReferencingMethods(Class<?> type) {
        Set<String> result = new LinkedHashSet<>();
        Map<String, Boolean> touchesPerMethod = new HashMap<>();
        try (InputStream in = openBytecode(type)) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc,
                                                String signature, String[] exceptions) {
                    touchesPerMethod.put(name + desc, false);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String fieldName,
                                                   String descriptor) {
                            if (ownerIsMapper(owner)) touchesPerMethod.put(key(), true);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String descriptor, boolean isInterface) {
                            if (ownerIsMapper(owner)) touchesPerMethod.put(key(), true);
                        }

                        private String key() {
                            return name + desc;
                        }
                    };
                }
            }, 0);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("读取字节码失败：" + type.getName(), e);
        }
        touchesPerMethod.forEach((k, v) -> {
            if (Boolean.TRUE.equals(v)) result.add(k);
        });
        return result;
    }

    private static InputStream openBytecode(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        InputStream in = type.getResourceAsStream(resource);
        if (in == null) throw new IllegalStateException("读不到字节码：" + resource);
        return in;
    }

    private static boolean scannerDetectsMapperReference(Method m) {
        Class<?> declaring = m.getDeclaringClass();
        Set<String> hits = mapperReferencingMethods(declaring);
        // 桥接/合成方法可能改名，退化为按「同名的任一重载」判定
        return hits.stream().anyMatch(s -> s.startsWith(m.getName() + "("));
    }

    private static boolean ownerIsMapper(String internalOwner) {
        if (internalOwner == null) return false;
        return internalOwner.contains("Mapper") || internalOwner.contains("Repository");
    }

    /** 负向自证：与 Mapper 无关的方法不得被误判为「访问 Mapper」。 */
    @Test
    @DisplayName("装置负向自证：不访问 Mapper 的方法不被误判")
    void scannerDoesNotFlagUnrelatedMethod() throws Exception {
        Method currentOperator = findMethod("currentOperatorId", 0);
        assertFalse(scannerDetectsMapperReference(currentOperator),
                "扫描装置过于宽泛：把不访问 Mapper 的方法也判成了访问");
    }

    private static Method findMethod(String name, int paramCount) {
        for (Method m : BankStatementExcelImportService.class.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) return m;
        }
        throw new IllegalStateException("找不到方法：" + name);
    }
}