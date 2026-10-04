package com.huicai.security;

import com.huicai.base.system.service.SubjectService;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P102 / M5b 第三层防线的真实 DB 回归。
 *
 * <p><b>缺陷背景</b>（2026-10-03 实测）：{@code TenantRlsInitializer} 与事务 advice
 * 顺序不确定时，{@code SET LOCAL app.enterprise_id} 落在事务外的自动提交连接上，
 * 语句结束即被数据库丢弃 ⇒ {@code pg_stat_activity} 里该值恒为 NULL ⇒
 * 应用切到非超级用户后<b>全部租户表读 0 行</b>（实测 /subjects/tree 0 个科目、
 * /vouchers/page 0 条 total 0，而库里实有 37 条凭证）。
 *
 * <p><b>本类锁两件事</b>：① 事务内能读到 GUC（advice 顺序）；② 事务外读不到
 * （必须是 {@code SET LOCAL} 而非 {@code SET}，否则连接池复用会跨租户串数据）。
 *
 * <p>类上用 {@code NOT_SUPPORTED} 关闭基类自带的事务（AGENTS §4.3 第 13 条）：
 * 基类默认给每个测试方法套事务，那样「事务内可见」与「事务外不可见」都无从验证。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TenantRlsGucRealDBTest extends AbstractMapperTest {

    private static final Long ENT_A = 9001L;

    @Autowired
    private SubjectService subjectService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("AT-102-8：切面在事务内设置 app.enterprise_id（顺序错则本用例红）")
    void gucIsVisibleInsideTransaction() {
        useEnterprise(ENT_A);

        String gucInTx = transactionTemplate.execute(status -> {
            // 必须调用**被 Spring 代理的 @Transactional service 方法**才能触发切面；
            // 直接调 mapper 既不经过事务也不经过切面，会让本用例变成假绿
            subjectService.getTree();
            return jdbcTemplate.queryForObject(
                    "select current_setting('app.enterprise_id', true)", String.class);
        });

        assertNotNull(gucInTx,
                "事务内读不到 app.enterprise_id ⇒ 切面落在事务外，SET LOCAL 被自动提交丢弃");
        assertEquals(String.valueOf(ENT_A), gucInTx, "事务内 GUC 与上下文企业不一致");
    }

    @Test
    @DisplayName("AT-102-9：事务外不得残留 app.enterprise_id（必须 SET LOCAL，禁 SET）")
    void gucDoesNotLeakOutsideTransaction() {
        useEnterprise(ENT_A);
        transactionTemplate.executeWithoutResult(status -> subjectService.getTree());

        String gucOutside = jdbcTemplate.execute((ConnectionCallback<String>) conn -> {
            try (var st = conn.createStatement();
                 var rs = st.executeQuery("select current_setting('app.enterprise_id', true)")) {
                return rs.next() ? rs.getString(1) : null;
            }
        });

        // 注意：PostgreSQL 在「曾经 SET LOCAL 过的事务」结束后，该变量读回来是**空串**
        // 而不是 NULL（全新会话才是 NULL）—— 故此处断言「空或 NULL」，
        // 关键是**不能是任何企业号**（会话级 SET 才会残留 9001）
        assertTrue(isUnset(gucOutside),
                "事务外仍能读到 app.enterprise_id=" + gucOutside
                        + " ⇒ 用的是 SET（会话级）而非 SET LOCAL，连接池复用会跨租户串数据");
    }

    @Test
    @DisplayName("AT-102-10：无上下文（定时任务/初始化）时不得设假值")
    void gucStaysNullWithoutContext() {
        EnterpriseContextHolderAccessor.clear();

        String guc = transactionTemplate.execute(status -> {
            subjectService.getTree();
            return jdbcTemplate.queryForObject(
                    "select current_setting('app.enterprise_id', true)", String.class);
        });

        assertTrue(isUnset(guc),
                "无企业上下文时不应设置 app.enterprise_id（实际=" + guc + "），否则会写错租户");
    }

    /** 「未设置」的两种表现：NULL（全新会话）或空串（曾 SET LOCAL 过的事务结束后）。 */
    private static boolean isUnset(String guc) {
        return guc == null || guc.isEmpty();
    }

    /** 基类的 {@code useEnterprise} 写入 ThreadLocal；此处显式清理，避免用例间串味。 */
    private static final class EnterpriseContextHolderAccessor {
        static void clear() {
            com.huicai.common.context.EnterpriseContextHolder.clear();
        }
    }
}