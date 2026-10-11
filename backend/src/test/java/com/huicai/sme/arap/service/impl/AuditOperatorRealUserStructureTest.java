package com.huicai.sme.arap.service.impl;

import com.huicai.common.entity.BaseEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P118：审计字段必须记真实操作人，不得写死常量。
 *
 * <p><b>为什么需要结构守卫</b>：SPEC-P117 把 {@code created_by} 从幽灵字段
 * 修成真实落库列后，「写什么值」就成了唯一的质量关口。而 P117 之前这些赋值
 * <b>根本不影响任何行为</b>，所以「随便填个常量」是完全正常的写法 ——
 * 全仓曾有 34 处 {@code setCreatedBy(DEFAULT_USER_ID)}。
 *
 * <p>本守卫把该关口机械化：任何新增的「把常量直接赋给 createdBy/resolvedBy」
 * 都会立刻变红。
 *
 * <p><b>允许的两种写法</b>（其余一律判红）：
 * <ol>
 *   <li>{@code setCreatedBy(currentOperatorId())} —— 取登录人；</li>
 *   <li>{@code setCreatedBy(x != null ? x : currentOperatorId())} —— 有参优先、无参回退。</li>
 * </ol>
 * ⚠️ 直接写 {@code DEFAULT_USER_ID} 不被允许 —— 它是「无登录态」的兜底值，
 * 有登录态时写它等于审计字段撒谎。
 */
@DisplayName("SPEC-P118：审计字段记真实操作人")
class AuditOperatorRealUserStructureTest {

    /** 审计字段：赋值即视为「记录操作人」 */
    private static final Pattern AUDIT_SETTER =
            Pattern.compile("\\.set(CreatedBy|ResolvedBy)\\(");

    /** 裸常量赋值（判红的形态）：setCreatedBy(DEFAULT_USER_ID) 之类 */
    private static final Pattern HARDCODED_ASSIGN =
            Pattern.compile("\\.set(?:CreatedBy|ResolvedBy)\\(\\s*DEFAULT_USER_ID\\s*\\)");

    /** 合法的取登录人写法 */
    private static final Pattern REAL_USER_ASSIGN =
            Pattern.compile("\\.set(?:CreatedBy|ResolvedBy)\\([^;]*"
                    + "(currentOperatorId\\(\\)|getCurrentUserId\\(\\)|[A-Za-z_][A-Za-z0-9_]*\\s*!=\\s*null)"
                    + "[^;]*\\)");

    @Test
    @DisplayName("生产代码中不得存在 setCreatedBy/setResolvedBy(DEFAULT_USER_ID) 这类裸常量赋值")
    void noHardcodedAuditOperator() throws Exception {
        List<String> violations = new ArrayList<>();

        for (String file : productionSources()) {
            Path p = Path.of(file);
            String content = Files.readString(p, StandardCharsets.UTF_8);

            Matcher m = HARDCODED_ASSIGN.matcher(content);
            while (m.find()) {
                int line = (int) content.substring(0, m.start()).lines().count();
                violations.add(file + ":" + line + "  " + m.group().trim());
            }
        }

        assertTrue(violations.isEmpty(),
                "审计字段被写死为常量（SPEC-P118）：审计会记录一个假的操作人。\n"
                        + "应改为 currentOperatorId()。\n违规点：\n  "
                        + String.join("\n  ", violations));
    }

    @Test
    @DisplayName("守卫自身有效：构造一处裸常量赋值，规则必须能命中")
    void guardDetectsHardcodedAssignment() {
        // 反证：若规则写错，这里就抓不到，测试会假绿
        String bad = "entity.setCreatedBy(DEFAULT_USER_ID);";
        String good = "entity.setCreatedBy(currentOperatorId());";

        assertTrue(HARDCODED_ASSIGN.matcher(bad).find(),
                "规则必须能命中裸常量赋值，否则本守卫恒绿");
        assertTrue(!HARDCODED_ASSIGN.matcher(good).find(),
                "合法写法不得被判红，否则守卫会误报");
        assertTrue(REAL_USER_ASSIGN.matcher(good).find(),
                "规则必须能识别合法写法");
    }

    @Test
    @DisplayName("currentOperatorId 必须真的取登录人，且 null 时回退而非返回 null")
    void helperTrulyReadsCurrentUser() throws Exception {
        // ⚠️ 这些类是构造器注入（无无参构造），无法实例化；
        //    且本守卫要验的是「写法正确」，读源码文本比反射更直接、也更可靠
        //    —— 反射只能验行为，验不出「回退值是不是常量」。
        List<String> files = List.of(
                "src/main/java/com/huicai/sme/arap/service/impl/ArapSettlementServiceImpl.java",
                "src/main/java/com/huicai/sme/arap/service/impl/ReconciliationServiceImpl.java",
                "src/main/java/com/huicai/sme/arap/service/impl/PurchaseReturnServiceImpl.java",
                "src/main/java/com/huicai/sme/tax/service/impl/InputInvoiceImportService.java",
                "src/main/java/com/huicai/sme/tax/service/impl/SalesInvoiceImportService.java");

        for (String f : files) {
            String src = Files.readString(Path.of(f), StandardCharsets.UTF_8);

            assertTrue(src.contains("private Long currentOperatorId()"),
                    f + " 缺少 currentOperatorId()");

            int start = src.indexOf("private Long currentOperatorId()");
            int end = src.indexOf("\n    }", start);
            String body = src.substring(start, end);

            // ① 必须真的取登录人，否则 helper 只是返回常量的换皮
            assertTrue(body.contains("SecurityUtils.getCurrentUserId()"),
                    f + ".currentOperatorId() 没有取登录人，只是把常量换了个名字");

            // ② 必须判 null（getCurrentUserId 在无登录态会返 null 而非抛异常）
            assertTrue(body.contains("uid != null"),
                    f + ".currentOperatorId() 未判 uid 为 null —— "
                            + "无登录态时会把 created_by 写成 NULL 而非兜底值");

            // ③ 兜底值必须是常量而非 null
            assertTrue(body.contains("DEFAULT_USER_ID"),
                    f + ".currentOperatorId() 的 null 分支未回退到兜底值");
        }
    }

    @Test
    @DisplayName("DEFAULT_USER_ID 不得为 0：id=0 不存在任何真实用户，写它等于造假")
    void defaultUserIdIsNotZero() throws Exception {
        List<String> classes = List.of(
                "com.huicai.sme.arap.service.impl.ArapSettlementServiceImpl",
                "com.huicai.sme.arap.service.impl.ReconciliationServiceImpl",
                "com.huicai.sme.arap.service.impl.PurchaseReturnServiceImpl",
                "com.huicai.sme.arap.service.impl.PrepaymentServiceImpl",
                "com.huicai.sme.tax.service.impl.InputInvoiceImportService",
                "com.huicai.sme.tax.service.impl.SalesInvoiceImportService",
                "com.huicai.sme.arap.service.impl.AutoGenerationService");

        for (String cn : classes) {
            Field f = Class.forName(cn).getDeclaredField("DEFAULT_USER_ID");
            f.setAccessible(true);
            long v = f.getLong(null);
            assertTrue(v > 0,
                    cn + ".DEFAULT_USER_ID = " + v
                            + " —— 用户 id=0 不存在，写进审计列等于凭空造人");
        }
    }

    @Test
    @DisplayName("createdBy/updatedBy 必须仍是真实映射列（不得回退为 exist=false 幽灵字段）")
    void createdByIsNotGhostField() throws Exception {
        // P117 的核心不变量：若被改回 exist=false，制审分离立刻失效。
        for (Field f : BaseEntity.class.getDeclaredFields()) {
            if (!f.getName().equals("createdBy") && !f.getName().equals("updatedBy")) {
                continue;
            }
            var ann = f.getAnnotation(
                    com.baomidou.mybatisplus.annotation.TableField.class);
            if (ann != null) {
                assertTrue(!ann.exist(),
                        "BaseEntity." + f.getName()
                                + " 被标回 @TableField(exist=false)，已退化为幽灵字段："
                                + "MP 既不写也不读，制审分离与审计将再次静默失效（SPEC-P117）");
            }
        }
    }

    // ── 工具 ──

    private static List<String> productionSources() throws Exception {
        Path root = Path.of("src/main/java");
        try (var s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/com/huicai/"))
                    .map(Path::toString)
                    .toList();
        }
    }
}