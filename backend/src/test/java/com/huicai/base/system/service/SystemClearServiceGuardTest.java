package com.huicai.base.system.service;

import com.huicai.base.system.entity.UserEntity;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.config.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * P148 / REQ-2026-164 清库护栏守卫 —— 「确认令牌 + 按企业隔离」的 L1 结构/语义断言。
 *
 * <h3>为什么必须有这组守卫</h3>
 * 2026-10-30 实测事故：会计调 `clear-vouchers` 被 403 后，管理员在同端点一次点击
 * 清掉**企业 4 的 26 张凭证**（跨企业全表 DELETE），并在 `t_business_doc.voucher_id`
 * 上留下 26 条悬空引用。原实现没有任何“确认”环节，也没有 `WHERE enterprise_id`。
 *
 * <h3>两条不变量（本类钉死）</h3>
 * <ol>
 *   <li><b>没确认就一行都不动</b>：`confirm` 缺失/不匹配 → 400，且
 *       **JdbcTemplate 零调用**（`verifyNoInteractions`）—— 这是「校验在任何 DELETE 之前」
 *       的机器可检形态，比“抛异常”更强：抛异常但已经删了一半也算失败。</li>
 *   <li><b>不确定企业就不清</b>：上下文空且未传 enterpriseId → 400，
 *       **绝不退化为清全部企业**；上下文与请求企业冲突 → 403。</li>
 * </ol>
 *
 * <h3>反证要求（AGENTS §4.5 第 21/25 条：门禁必须能变红）</h3>
 * ①把 {@code resolveAndVerify} 的令牌校验注释掉 ⇒ AT-164-1/2 转红；
 * ②把「上下文空 → 400」改成 `return null`（等价旧行为）⇒ AT-164-5 转红；
 * ③把 SQL 里的 `WHERE enterprise_id = ?` 去掉 ⇒ AT-164-9 转红（SQL 文本断言）。
 */
class SystemClearServiceGuardTest {

    private JdbcTemplate jdbcTemplate;
    private SystemClearService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        service = new SystemClearService(jdbcTemplate);
        EnterpriseContextHolder.clear();
        // 服务内会读 SecurityUtils.getCurrentUserId() 做审计日志 —— 必须建认证态，
        // 否则抛「未登录」。范式同 PeriodControllerTest。
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("clear-guard-test");
        user.setPassword("x"); // Spring Security 的 User 构造器要求密码非空
        user.setEnterpriseId(1L);
        user.setUserType("SUPER_ADMIN");
        LoginUser loginUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("system:clear")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        // 🔴 双侧必须都清：EnterpriseContextHolder 与 SecurityContextHolder。
        // 纯 Mockito 测试不会触发 Spring TestContext 的清理监听器，
        // 残留会跨类污染（AGENTS §4.5 第 29 条，CI 曾因 AutoGenerationServiceTest 残留漏一例 403）。
        EnterpriseContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    // ==================== 护栏 1：确认令牌 ====================

    @Test
    @DisplayName("AT-164-1 confirm 缺失 ⇒ 400 且零 SQL 执行")
    void at164_1_missingConfirm_rejected() {
        EnterpriseContextHolder.set(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.clearVouchers(null, 1L));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("CLEAR_VOUCHERS"),
                "错误消息必须回显所需令牌，否则调用方无从修正: " + ex.getMessage());
        // 🔴 关键：校验在所有 DELETE 之前 —— 一行都没动
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("AT-164-2 confirm 为空串 ⇒ 400 且零 SQL 执行")
    void at164_2_blankConfirm_rejected() {
        EnterpriseContextHolder.set(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.clearVouchers("   ", 1L));

        assertEquals(400, ex.getCode());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("AT-164-3 confirm 不匹配（含大小写/他动作令牌）⇒ 400 且零 SQL 执行")
    void at164_3_wrongConfirm_rejected() {
        EnterpriseContextHolder.set(1L);

        for (String bad : new String[]{"clear_vouchers", "CLEAR_ALL", "CLEAR_BANK_STATEMENTS", "yes", "true"}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.clearVouchers(bad, 1L), "令牌 " + bad + " 不应通过");
            assertEquals(400, ex.getCode(), "令牌 " + bad + " 应被拒");
        }
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("AT-164-4 令牌按动作一一对应，不可跨动作用同一令牌")
    void at164_4_tokenIsActionScoped() {
        EnterpriseContextHolder.set(1L);

        // 用「清空全部」的令牌去清凭证 → 必须被拒
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.clearVouchers(SystemClearService.ClearAction.CLEAR_ALL.token(), 1L));
        assertEquals(400, ex.getCode());
        verifyNoInteractions(jdbcTemplate);
    }

    // ==================== 护栏 2：按企业隔离 ====================

    @Test
    @DisplayName("AT-164-5 上下文空且未传 enterpriseId ⇒ 400，不退化为清全部")
    void at164_5_noContextNoParam_rejectedInsteadOfClearAll() {
        // 上下文为空（模拟 P127/P139 反复踩到的 GUC NULL 形态）
        assertThrows(BusinessException.class,
                () -> service.clearVouchers(SystemClearService.ClearAction.CLEAR_VOUCHERS.token(), null));
        // 🔴 绝不退化为「清全部」：零 SQL
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("AT-164-6 上下文企业与请求企业不一致 ⇒ 403 且零 SQL")
    void at164_6_crossEnterprise_forbidden() {
        EnterpriseContextHolder.set(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.clearVouchers(SystemClearService.ClearAction.CLEAR_VOUCHERS.token(), 4L));

        assertEquals(403, ex.getCode());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("AT-164-7 上下文为空但显式传 enterpriseId ⇒ 放行（运维场景）")
    void at164_7_explicitEnterpriseAllowedWhenNoContext() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        String msg = service.clearVouchers(SystemClearService.ClearAction.CLEAR_VOUCHERS.token(), 4L);

        assertTrue(msg.contains("企业 4"), "摘要应指明目标企业: " + msg);
        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("AT-164-8 上下文与请求一致 ⇒ 放行")
    void at164_8_matchingContextAllowed() {
        EnterpriseContextHolder.set(7L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        String msg = service.clearVouchers(SystemClearService.ClearAction.CLEAR_VOUCHERS.token(), 7L);

        assertTrue(msg.contains("企业 7"), msg);
    }

    @Test
    @DisplayName("AT-164-9 🔴 每条 DELETE 都必须带 enterprise_id 条件（反证：去掉 WHERE 即转红）")
    void at164_9_everyStatementScopedByEnterprise() {
        EnterpriseContextHolder.set(1L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        // 逐一走遍 9 个动作，收集它们实际发给 JDBC 的 SQL
        SystemClearService.ClearAction[] actions = SystemClearService.ClearAction.values();
        StringBuilder allSql = new StringBuilder();
        for (SystemClearService.ClearAction action : actions) {
            Mockito.clearInvocations(jdbcTemplate);
            runAction(action);
            for (var inv : Mockito.mockingDetails(jdbcTemplate).getInvocations()) {
                Object[] args = inv.getArguments();
                if (args.length > 0 && args[0] instanceof String sql) {
                    allSql.append(sql).append('\n');
                }
            }
        }

        String sqls = allSql.toString();
        assertFalse(sqls.isBlank(), "应至少产生一条 SQL");

        // 断言一：没有任何无 WHERE 的全表 DELETE
        for (String line : sqls.split("\n")) {
            String s = line.trim();
            if (!s.toUpperCase().startsWith("DELETE FROM")) {
                continue;
            }
            assertTrue(s.toUpperCase().contains("WHERE"),
                    "发现无 WHERE 的全表 DELETE（跨企业清库）：" + s);
            assertTrue(s.contains("enterprise_id"),
                    "DELETE 必须按企业收敛（缺失 enterprise_id）：" + s);
        }

        // 断言二：UPDATE 若解绑引用，也必须按企业收敛
        for (String line : sqls.split("\n")) {
            String s = line.trim();
            if (!s.toUpperCase().startsWith("UPDATE")) {
                continue;
            }
            assertTrue(s.contains("enterprise_id") || s.contains("business_doc_id IN"),
                    "UPDATE 必须按企业收敛或经子查询收敛：" + s);
        }
    }

    /** 用动作的**合法令牌**执行一次（供 AT-164-9 收集 SQL）。 */
    private void runAction(SystemClearService.ClearAction action) {
        runActionWith(action, action.token());
    }

    /**
     * 用指定令牌执行动作 —— 传 {@code null} 即可验证「缺令牌必拒」。
     *
     * <p>switch 是<b>穷尽式</b>：新增动作却不在此登记 ⇒ default 分支 fail，
     * 防「新增清库动作漏了确认令牌校验」这一类缺陷静默复发。</p>
     */
    private void runActionWith(SystemClearService.ClearAction action, String confirm) {
        switch (action) {
            case CLEAR_VOUCHERS -> service.clearVouchers(confirm, 1L);
            case CLEAR_BANK_STATEMENTS -> service.clearBankStatements(confirm, 1L);
            case CLEAR_INVOICE_RECORDS -> service.clearInvoiceRecords(confirm, 1L);
            case CLEAR_REPORT_DATA -> service.clearReportData(confirm, 1L);
            case CLEAR_BUSINESS_DOCS -> service.clearBusinessDocs(confirm, 1L);
            case CLEAR_RECEIVABLES -> service.clearReceivables(confirm, 1L);
            case CLEAR_PAYABLES -> service.clearPayables(confirm, 1L);
            case CLEAR_SETTLEMENTS -> service.clearSettlements(confirm, 1L);
            case CLEAR_ALL -> service.clearAll(confirm, 1L);
            default -> fail("存在未覆盖的清库动作: " + action + "（新增动作必须同步本守卫）");
        }
    }

    @Test
    @DisplayName("AT-164-10 所有动作都会被令牌校验覆盖（防新增动作漏校验）")
    void at164_10_allActionsGuarded() {
        // 每个动作在缺 confirm 时都必须 400 + 零 SQL
        for (SystemClearService.ClearAction action : SystemClearService.ClearAction.values()) {
            EnterpriseContextHolder.set(1L);
            Mockito.clearInvocations(jdbcTemplate);
            assertThrows(BusinessException.class, () -> runActionWith(action, null),
                    "动作 " + action + " 缺确认令牌却未拒绝");
            verifyNoInteractions(jdbcTemplate);
        }
    }
}
