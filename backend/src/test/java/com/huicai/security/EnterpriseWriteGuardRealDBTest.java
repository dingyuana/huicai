package com.huicai.security;

import com.huicai.common.exception.BusinessException;
import com.huicai.common.security.EnterpriseWriteGuard;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P113 —— 非 ACTIVE 账套禁写。
 *
 * <p>守卫为 Spring AOP 切面，其行为断言<b>不能</b>用「直接 new 切面再调用」的方式做
 * —— 那样只证明方法体逻辑正确，不证明切面真被织入。§4.5 第 27 条实证过「有注解 ≠
 * 会生效」。因此本类用一个<b>真实走 Spring 代理</b>的被切方法（{@link WriteTarget}），
 * 它经容器注入，调用即走代理 ⇒ 切面必触发。</p>
 */
@DisplayName("SPEC-P113 非 ACTIVE 账套禁写")
class EnterpriseWriteGuardRealDBTest extends AbstractMapperTest {

    @Autowired
    private P113WriteTarget writeTarget;

    @Autowired
    private EnterpriseWriteGuard guard;

    /** 建一个指定状态的账套，返回 id。 */
    private Long seedEnterprise(String code, String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_enterprise (enterprise_code, enterprise_name, status) "
                        + "VALUES (?, ?, ?::varchar) RETURNING id",
                Long.class, code, code + "-name", status);
    }

    private void setStatus(Long id, String status) {
        jdbcTemplate.update("UPDATE t_enterprise SET status = ?::varchar WHERE id = ?", status, id);
    }

    private void cleanup(Long... ids) {
        for (Long id : ids) {
            jdbcTemplate.update("DELETE FROM t_enterprise WHERE id = ?", id);
        }
    }

    // ─────────── AT-113-1：ACTIVE 放行（正向） ───────────
    @Test
    @DisplayName("AT-113-1 ACTIVE 账套写端点放行")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void activeEnterpriseMayWrite() {
        Long id = seedEnterprise("P113.OK", "ACTIVE");
        try {
            useEnterprise(id);
            String r = writeTarget.doWrite("ok");
            assertEquals("written", r, "ACTIVE 账套应正常写入");
        } finally {
            EnterpriseContextHolderCleanup.clear();
            cleanup(id);
        }
    }

    // ─────────── AT-113-2/3/4：三种非 ACTIVE 一律拒绝 ───────────
    @Test
    @DisplayName("AT-113-2 SUSPENDED 账套写端点被拒")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void suspendedEnterpriseCannotWrite() {
        Long id = seedEnterprise("P113.SUSP", "SUSPENDED");
        try {
            useEnterprise(id);
            BusinessException e = assertThrows(BusinessException.class,
                    () -> writeTarget.doWrite("susp"), "SUSPENDED 账套应被拒绝写入");
            assertTrue(e.getMessage().contains("SUSPENDED"), "异常应指明当前状态，实际: " + e.getMessage());
        } finally {
            EnterpriseContextHolderCleanup.clear();
            cleanup(id);
        }
    }

    @Test
    @DisplayName("AT-113-3 PENDING 账套写端点被拒")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void pendingEnterpriseCannotWrite() {
        Long id = seedEnterprise("P113.PEND", "PENDING");
        try {
            useEnterprise(id);
            assertThrows(BusinessException.class, () -> writeTarget.doWrite("pend"),
                    "PENDING 账套应被拒绝写入");
        } finally {
            EnterpriseContextHolderCleanup.clear();
            cleanup(id);
        }
    }

    @Test
    @DisplayName("AT-113-4 TERMINATED 账套写端点被拒（终态同样禁写）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void terminatedEnterpriseCannotWrite() {
        Long id = seedEnterprise("P113.TERM", "TERMINATED");
        try {
            useEnterprise(id);
            assertThrows(BusinessException.class, () -> writeTarget.doWrite("term"),
                    "TERMINATED 账套应被拒绝写入");
        } finally {
            EnterpriseContextHolderCleanup.clear();
            cleanup(id);
        }
    }

    // ─────────── AT-113-5：无企业上下文放行（系统路径不误伤） ───────────
    @Test
    @DisplayName("AT-113-5 无企业上下文时放行（初始化/定时任务不误伤）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void noContextIsAllowedThrough() {
        withoutEnterpriseContext(() -> assertEquals("written", writeTarget.doWrite("sys"),
                "无企业上下文的系统路径应放行；被拦会让初始化/定时任务全挂"));
    }

    // ─────────── AT-113-6：账套不存在 fail-closed ───────────
    @Test
    @DisplayName("AT-113-6 账套不存在时 fail-closed 拒绝")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void missingEnterpriseFailsClosed() {
        useEnterprise(777777777L);
        try {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> writeTarget.doWrite("ghost"), "账套不存在应 fail-closed 拒绝");
            assertTrue(e.getMessage().contains("不存在") || e.getMessage().contains("不可用"),
                    "异常应指明账套不存在，实际: " + e.getMessage());
        } finally {
            EnterpriseContextHolderCleanup.clear();
        }
    }

    // ─────────── AT-113-7：读路径放行 ───────────
    @Test
    @DisplayName("AT-113-7 读路径不受守卫影响（停用账套仍可查历史）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void readPathNotGuarded() {
        Long id = seedEnterprise("P113.READ", "SUSPENDED");
        try {
            useEnterprise(id);
            // WriteTarget.doRead 无 @PostMapping ⇒ 切面不拦
            assertEquals("read", writeTarget.doRead(), "读路径必须放行，否则停用账套无法对账审计");
        } finally {
            EnterpriseContextHolderCleanup.clear();
            cleanup(id);
        }
    }

    // ─────────── AT-113-8：切面确实被织入（结构 + 动态切换状态） ───────────
    @Test
    @DisplayName("AT-113-8 切面真被织入：同一方法随账套状态改变结果")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aspectIsActuallyWeaved() {
        Long id = seedEnterprise("P113.SWITCH", "ACTIVE");
        try {
            useEnterprise(id);
            assertEquals("written", writeTarget.doWrite("a"), "ACTIVE 应放行");
            setStatus(id, "SUSPENDED");
            assertThrows(BusinessException.class, () -> writeTarget.doWrite("b"),
                    "状态改为 SUSPENDED 后同一方法应被拒 ⇒ 证明拦截来自切面而非其它分支");
            setStatus(id, "ACTIVE");
            assertEquals("written", writeTarget.doWrite("c"), "恢复 ACTIVE 后应重新放行");
        } finally {
            EnterpriseContextHolderCleanup.clear();
            cleanup(id);
        }
    }

    /** 断言切面 Bean 存在（防"类删了测试仍绿"）。 */
    @Test
    @DisplayName("AT-113-8b 守卫切面已注册为 Spring Bean")
    void guardBeanExists() {
        assertTrue(guard != null, "EnterpriseWriteGuard 应已注册为 Bean");
    }

    /** 清理上下文的本地小工具（该类未继承 BaseEntity 之外的上下文重置）。 */
    static final class EnterpriseContextHolderCleanup {
        static void clear() {
            com.huicai.common.context.EnterpriseContextHolder.clear();
        }
    }

}
