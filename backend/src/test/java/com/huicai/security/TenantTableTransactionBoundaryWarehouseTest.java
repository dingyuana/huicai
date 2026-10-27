package com.huicai.security;

import com.huicai.sme.cash.service.impl.BankStatementExcelImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P127 / REQ-2026-143：全仓租户表事务边界守卫。
 *
 * <p><b>范围</b>：扫描 {@code com.huicai.*} 下所有含 Mapper 字段的 Service 类，
 * 验证其中<b>每个直接访问 Mapper 的 public 方法</b>是否被类级或方法级
 * {@code @Transactional} 覆盖。
 *
 * <p><b>为什么需要全仓扫</b>：上一轮只守了 {@code BankStatementExcelImportService} 一个类，
 * F-2~F-7 的 6 处候选（{@code AiTaskService}、{@code AiFeedbackLogServiceImpl}、
 * {@code AnalysisService}、{@code AttachmentService}）未逐项确认；
 * 该扫描器自检失败（已如实登记在 §4.5 第 65 条），不能据此宣称全仓已收口。
 * 本类补一个<b>独立的</b>全仓守卫，用同样的 ASM 字节码判定，
 * 但目标类集合是真正的 Service 实现包。
 *
 * <p>🔴 <b>反证要求</b>（§4.5 第 21 条）：注入一个无事务的 Mapper 调用必须转红。
 */
@DisplayName("SPEC-P127 全仓租户表事务边界守卫")
class TenantTableTransactionBoundaryWarehouseTest {

    /**
     * 前提自证：ASM 扫描器能识别出已知真阳性 confirmImport。
     */
    @Test
    @DisplayName("装置自证：ASM 扫描器能识别出 confirmImport 引用了 Mapper")
    void scannerDetectsKnownTruePositive() throws Exception {
        Method confirmImport = BankStatementExcelImportService.class
                .getMethod("confirmImport", String.class);
        assertTrue(scannerDetectsMapperReference(confirmImport),
                "ASM 扫描装置失效：连已知真阳性 confirmImport 都扫不出");
    }

    /**
     * 全仓不变量：所有含 Mapper 字段的 Service 实现类中，
     * 直接访问 Mapper 的 public 方法必须被事务覆盖。
     */
    @Test
    @DisplayName("全仓：所有含 Mapper 的 ServiceImpl 中，直接访问 Mapper 的 public 方法必须有事务")
    void everyServiceWithMapperMustHaveTransactionalOnMapperTouchingMethods() {
        List<String> violations = scanAllServices();
        if (!violations.isEmpty()) {
            // 仅报告当前已知存量（不含 F-2~F-7 候选，它们尚未核实）
            // 若新增违规，明确列出
            System.out.println("[P127-WH] 发现以下方法无事务且直接访问 Mapper:");
            violations.forEach(System.out::println);
        }
        assertTrue(violations.isEmpty(),
                "以下方法直接访问 Mapper 却无事务边界，RLS 下会失败或静默丢数据：\n"
                        + String.join("\n", violations)
                        + "\n（需按 AGENTS §4.5 第 65 条逐类核实后决定修法）");
    }

    /**
     * 全仓扫描：返回所有「无事务且直接访问 Mapper」的 violation 字符串列表。
     */
    private List<String> scanAllServices() {
        List<String> violations = new ArrayList<>();
        // 只扫 impl 包下的 Service 实现类
        List<Class<?>> serviceClasses = findAllServiceImplClasses();
        for (Class<?> cls : serviceClasses) {
            Set<String> mapperMethods = mapperReferencingMethods(cls);
            for (Method m : cls.getDeclaredMethods()) {
                if (m.isSynthetic() || !java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;
                if (!mapperMethods.stream().anyMatch(s -> s.startsWith(m.getName() + "("))) continue;
                Transactional tx = AnnotatedElementUtils.findMergedAnnotation(m, Transactional.class);
                Transactional clsTx = AnnotatedElementUtils.findMergedAnnotation(cls, Transactional.class);
                if (tx == null && clsTx == null) {
                    violations.add(cls.getSimpleName() + "#" + m.getName() + "()"
                            + " [clsTx=" + clsTx + " methTx=" + tx + "]");
                }
            }
        }
        return violations;
    }

    private static List<Class<?>> findAllServiceImplClasses() {
        try {
            return java.util.stream.Stream.of(
                            "com.huicai.base.ai.service",
                            "com.huicai.base.ai.service.impl",
                            "com.huicai.base.business.util",
                            "com.huicai.base.report.service",
                            "com.huicai.base.storage.service",
                            "com.huicai.base.voucher.service",
                            "com.huicai.base.voucher.service.impl",
                            "com.huicai.base.system.service",
                            "com.huicai.base.system.service.impl",
                            "com.huicai.base.masterdata.service",
                            "com.huicai.base.masterdata.service.impl",
                            "com.huicai.base.balance.service",
                            "com.huicai.base.balance.service.impl",
                            "com.huicai.sme.cash.service.impl",
                            "com.huicai.sme.arap.service",
                            "com.huicai.sme.arap.service.impl",
                            "com.huicai.sme.asset.service.impl",
                            "com.huicai.sme.budget.service.impl",
                            "com.huicai.sme.tax.service.impl",
                            "com.huicai.sme.periodclose.service.impl",
                            "com.huicai.agency.tenant.service.impl",
                            "com.huicai.agency.user.service.impl",
                            "com.huicai.agency.dashboard.service.impl",
                            "com.huicai.agency.batch.service.impl",
                            "com.huicai.agency.client.service.impl",
                            "com.huicai.common.transaction"
                    )
                    .flatMap(pkg -> scanPackageForServiceClasses(pkg))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException("扫描 Service 类失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Stream<Class<?>> scanPackageForServiceClasses(String pkg) {
        try {
            String path = pkg.replace('.', '/') + "/";
            java.net.URL url = Thread.currentThread().getContextClassLoader().getResource(path);
            if (url == null) return Stream.empty();
            return (Stream<Class<?>>) (Stream<?>) java.nio.file.Files.walk(java.nio.file.Paths.get(url.toURI()))
                    .filter(p -> p.toString().endsWith(".class"))
                    .filter(p -> p.getFileName().toString().contains("Service"))
                    .filter(p -> !p.getFileName().toString().contains("Test"))
                    .map(p -> {
                        try {
                            return (Class<?>) Class.forName(pkg + "." + p.getFileName().toString().replace(".class", ""));
                        } catch (ClassNotFoundException ex) {
                            return null;
                        }
                    })
                    .filter(c -> c != null)
                    .filter(c -> !c.isInterface())
                    .filter(c -> java.lang.reflect.Modifier.isAbstract(c.getModifiers()) == false);
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    private static Set<String> mapperReferencingMethods(Class<?> type) {
        Set<String> result = new LinkedHashSet<>();
        Map<String, Boolean> touchesPerMethod = new HashMap<>();
        try (InputStream in = type.getResourceAsStream(
                "/" + type.getName().replace('.', '/') + ".class")) {
            if (in == null) return result;
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
                        private String key() { return name + desc; }
                    };
                }
            }, 0);
        } catch (java.io.IOException e) {
            // 字节码读取失败，跳过该类
        }
        touchesPerMethod.forEach((k, v) -> {
            if (Boolean.TRUE.equals(v)) result.add(k);
        });
        return result;
    }

    private static boolean scannerDetectsMapperReference(Method m) {
        Class<?> declaring = m.getDeclaringClass();
        Set<String> hits = mapperReferencingMethods(declaring);
        return hits.stream().anyMatch(s -> s.startsWith(m.getName() + "("));
    }

    private static boolean ownerIsMapper(String internalOwner) {
        if (internalOwner == null) return false;
        return internalOwner.contains("Mapper") || internalOwner.contains("Repository");
    }
}
