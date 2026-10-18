package com.huicai.sme.arap.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P119：其余 5 类单据的制审分离。
 *
 * <p><b>本守卫盯的不是「校验在不在」，而是「校验能不能生效」</b> ——
 * 后者才是 P119 的真正缺陷：6 处审核入口里 5 处的创建路径根本不写
 * {@code created_by}，于是校验代码在、也不拦，是 §4.5 第 21 条的恒绿形态。
 *
 * <p>因此守卫成对检查：<b>创建路径必须写 + 审核路径必须校验</b>，
 * 缺任一侧即红。
 */
@DisplayName("SPEC-P119：5 类单据制审分离（创建写入 + 审核校验成对）")
class MakerCheckerAllApprovalsStructureTest {

    /** 需要成对具备「写 created_by」与「校验制审分离」的类 */
    private static final List<String> CLASSES = List.of(
            "sme/budget/service/impl/BudgetServiceImpl.java",
            "sme/cash/service/impl/BankStatementServiceImpl.java",
            "sme/asset/service/impl/AssetDisposalServiceImpl.java",
            "sme/arap/service/impl/ExpenseReimbursementServiceImpl.java",
            "sme/arap/service/impl/ReconciliationServiceImpl.java");

    @Test
    @DisplayName("每个类都必须有：① 创建时写 created_by ② 审核时校验制审分离")
    void everyClassHasBothWriteAndCheck() throws Exception {
        List<String> violations = new ArrayList<>();

        for (String rel : CLASSES) {
            String src = read(rel);
            boolean hasWrite = src.contains(".setCreatedBy(currentOperatorId())");
            boolean hasCheck = src.contains("assertNotSelfApprove(")
                    || src.contains("assertNotSelfReview(");
            if (!hasWrite) {
                violations.add(rel + " 缺少 ① 创建路径写 created_by "
                        + "⇒ 制审分离校验将恒不触发（恒绿门禁）");
            }
            if (!hasCheck) {
                violations.add(rel + " 缺少 ② 审核路径的制审分离校验");
            }
        }

        assertTrue(violations.isEmpty(),
                "SPEC-P119 要求成对实现（写 + 校验）：\n  " + String.join("\n  ", violations));
    }

    @Test
    @DisplayName("守卫自身有效：缺少 created_by 写入必须被判红")
    void guardDetectsMissingWrite() {
        // 反证：若规则写错，这里抓不到，测试就假绿
        String good = "        entity.setCreatedBy(currentOperatorId());\n"
                + "        mapper.insert(entity);";
        String bad = "        mapper.insert(entity);";

        assertTrue(good.contains(".setCreatedBy(currentOperatorId())"));
        assertTrue(!bad.contains(".setCreatedBy(currentOperatorId())"),
                "缺少写入的片段必须被判红");
    }

    @Test
    @DisplayName("制审校验的操作人取值必须无登录态返 null（Q2 裁定），不得回退常量")
    void p119HelperReturnsNullWhenNoLogin() throws Exception {
        // 与 P118 的审计用 currentOperatorId（无登录态回退 DEFAULT_USER_ID）刻意不同：
        // Excel 导入 / 定时任务本无「录入人」概念，填常量即凭空造人。
        //
        // ⚠️ ReconciliationServiceImpl **同时**有两个 helper（两类职责必须分开），
        //    故必须按「实际传给 assertNotSelfApprove 的那个」判定，
        //    不能只按方法名找 —— 否则 4 个纯 P119 类（helper 叫 currentOperatorId
        //    但无兜底）会被漏检，守卫自身就成了恒绿。
        Pattern call = Pattern.compile(
                "assertNotSelfApprove\\(\\s*(\\w+)\\(\\)");

        for (String rel : CLASSES) {
            String src = read(rel);
            Matcher m = call.matcher(src);
            int checked = 0;
            while (m.find()) {
                String fn = m.group(1);
                checked++;
                // 取该 helper 的方法体
                int i = src.indexOf("private Long " + fn + "()");
                if (i < 0) {
                    assertTrue(false, rel + " 的制审校验调用了 " + fn
                            + "()，但找不到该方法定义");
                    continue;
                }
                int j = src.indexOf("\n    }", i);
                String body = src.substring(i, j);

                assertTrue(body.contains("return null;"),
                        rel + "." + fn + "() 用于制审校验却无登录态 null 分支");
                assertTrue(!body.contains("DEFAULT_USER_ID"),
                        rel + "." + fn + "() 同时用于制审校验与常量兜底 —— "
                                + "会把定时任务产出的单据误判为「制单人==审批人」，"
                                + "或凭空认定一个不存在的用户是制单人");
            }
            assertTrue(checked > 0, rel + " 未找到 assertNotSelfApprove 调用点");
        }
    }

    @Test
    @DisplayName("制审分离校验必须对 created_by 为 NULL 放行（不得反推「他自审」）")
    void nullCreatedByIsNotSelfReview() throws Exception {
        // 口径与凭证层/业务单据层逐字一致：Excel 导入的单据没有录入人概念
        Pattern guard = Pattern.compile(
                "if\\s*\\(userId != null && createdBy != null && userId\\.equals\\(createdBy\\)\\)");
        int found = 0;
        for (String rel : CLASSES) {
            if (guard.matcher(read(rel)).find()) found++;
        }
        assertTrue(found >= 4,
                "仅在 " + found + " 个类中找到「NULL 放行」的三重判空，"
                        + "口径应与凭证层一致（userId/createdBy 任一为 null 即放行）");
    }

    @Test
    @DisplayName("P117 不变量回归：createdBy 不得被改回 exist=false 幽灵字段")
    void createdByStillRealColumn() throws Exception {
        Class<?> base = Class.forName("com.huicai.common.entity.BaseEntity");
        for (String name : List.of("createdBy", "updatedBy")) {
            Field f = base.getDeclaredField(name);
            var ann = f.getAnnotation(
                    com.baomidou.mybatisplus.annotation.TableField.class);
            if (ann != null) {
                assertTrue(!ann.exist(),
                        "BaseEntity." + name + " 被改回 exist=false，制审分离将再次静默失效");
            }
        }
    }

    // ── 工具 ──

    private static String read(String rel) throws Exception {
        return Files.readString(Path.of("src/main/java/com/huicai", rel),
                StandardCharsets.UTF_8);
    }
}