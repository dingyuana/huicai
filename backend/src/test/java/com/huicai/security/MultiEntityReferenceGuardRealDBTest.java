package com.huicai.security;

import com.huicai.agency.tenant.service.AgencyService;
import com.huicai.base.masterdata.service.CustomerService;
import com.huicai.base.masterdata.service.VendorService;
import com.huicai.base.system.service.RoleService;
import com.huicai.base.system.service.UserService;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.security.ReferenceGuard;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.cash.service.BankAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P134 批次 2 —— 其余引用型 DELETE 端点的守卫。
 *
 * <p><b>选表依据是实测的「活引用数」，不是推测</b>（真库 plpgsql 逐外键统计，
 * 见 SPEC §8.2）：{@code t_bank_account←t_bank_statement} 80 条、
 * {@code t_role←t_role_menu} 150 条、{@code t_agency} 13 条、{@code t_user} 12 条。
 * 活引用为 0 的表（如 {@code t_customer}）也一并加注解 ——
 * 「今天没人引用」不等于「明天没人引用」，且加注解零成本。</p>
 *
 * <p>🔴 <b>不按 {@code t_subject} 同型推定</b>（SPEC D-1 硬性前置）：
 * 每张表都独立核对了 Service 实现体，确认其原有的检查覆盖范围。</p>
 */
class MultiEntityReferenceGuardRealDBTest extends AbstractMapperTest {

    @Autowired private BankAccountService bankAccountService;
    @Autowired private AgencyService agencyService;
    @Autowired private UserService userService;
    @Autowired private RoleService roleService;
    @Autowired private CustomerService customerService;
    @Autowired private VendorService vendorService;

    @Autowired private JdbcTemplate jdbcTemplate;

    private Long ent() {
        return com.huicai.common.context.EnterpriseContextHolder.get();
    }

    @Test
    @DisplayName("AT-134-2 银行账户：被流水引用时拒绝删除，且数据原封不动")
    void bankAccountWithStatementsCannotBeDeleted() {
        Long accId = createBankAccount("B2ACC");
        createBankStatement(accId, "B2ST");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bankAccountService.delete(accId));
        assertTrue(ex.getMessage().contains("被以下数据引用"),
                "须是引用冲突，实际：" + ex.getMessage());
        assertEquals(0, deletedFlag("t_bank_account", accId), "账户不得被删除");
        assertEquals(1, count("t_bank_statement", "account_id", accId), "流水数不得变化");
    }

    @Test
    @DisplayName("批次2 正控：无任何引用的银行账户仍可删除（防止守卫把功能全打挂）")
    void unusedBankAccountCanStillBeDeleted() {
        Long accId = createBankAccount("B2FREE");
        bankAccountService.delete(accId);
        assertEquals(1, deletedFlag("t_bank_account", accId), "无引用账户应能删除");
    }

    @Test
    @DisplayName("角色：t_role_menu 有授权时也须拒绝（此前只查了 t_user_role，漏了 150 条授权）")
    void roleWithMenuGrantsCannotBeDeleted() {
        Long roleId = createRole("B2R");
        createSysUser("B2U");
        jdbcTemplate.update("INSERT INTO t_user_role (user_id, role_id) "
                + "SELECT id, ? FROM t_user WHERE username LIKE 'B2U%' LIMIT 1", roleId);
        jdbcTemplate.update("INSERT INTO t_role_menu (role_id, menu_id) "
                + "SELECT ?, id FROM t_menu WHERE deleted = 0 LIMIT 1", roleId);

        assertThrows(BusinessException.class, () -> roleService.delete(roleId));
        assertEquals(0, deletedFlag("t_role", roleId), "角色不得被删除");
    }

    @Test
    @DisplayName("代理公司：被客户企业引用时拒绝删除")
    void agencyWithEnterprisesCannotBeDeleted() {
        Long agencyId = createAgency("B2AG");
        jdbcTemplate.update("UPDATE t_enterprise SET agency_id = ? WHERE id = ("
                + "SELECT min(id) FROM t_enterprise WHERE deleted = 0)", agencyId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> agencyService.delete(agencyId));
        assertTrue(ex.getMessage().contains("被以下数据引用"),
                "须是引用冲突，实际：" + ex.getMessage());
        assertEquals(0, deletedFlag("t_agency", agencyId), "代理公司不得被删除");
    }

    @Test
    @DisplayName("用户：被角色/代理关系引用时拒绝删除（此前完全无检查）")
    void userWithRolesCannotBeDeleted() {
        Long uid = createSysUser("B2UX");
        Long roleId = createRole("B2RX");
        jdbcTemplate.update("INSERT INTO t_user_role (user_id, role_id) VALUES (?, ?)", uid, roleId);

        assertThrows(BusinessException.class, () -> userService.delete(uid));
        assertEquals(0, deletedFlag("t_user", uid), "用户不得被删除");
    }

    @Test
    @DisplayName("客户：被对账单引用时拒绝删除（实测该外键活引用为 0，故须自己造引用）")
    void customerWithStatementCannotBeDeleted() {
        Long cid = createParty("t_customer", "B2C");
        jdbcTemplate.update(
                "INSERT INTO t_customer_statement (statement_no, customer_id, period_start, "
                        + "period_end, period, enterprise_id, deleted) "
                        + "VALUES (?, ?, CURRENT_DATE, CURRENT_DATE, '202601', ?, 0)",
                "P134-ST-" + cid, cid, ent());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> customerService.delete(cid));
        assertTrue(ex.getMessage().contains("被以下数据引用"),
                "须是引用冲突，实际：" + ex.getMessage());
        assertEquals(0, deletedFlag("t_customer", cid), "客户不得被删除");
    }

    @Test
    @DisplayName("供应商：被进项发票引用时拒绝删除")
    void vendorWithInputInvoiceCannotBeDeleted() {
        Long vid = createParty("t_vendor", "B2V");
        jdbcTemplate.update(
                "INSERT INTO t_input_invoice (invoice_no, vendor_id, invoice_date, period, "
                        + "amount, tax_rate, tax_amount, total_amount, invoice_type, "
                        + "enterprise_id, deleted) "
                        + "VALUES (?, ?, CURRENT_DATE, '202601', 100, 0.13, 13, 113, "
                        + "'SPECIAL', ?, 0)", "P134-INV-" + vid, vid, ent());

        assertThrows(BusinessException.class, () -> vendorService.delete(vid));
        assertEquals(0, deletedFlag("t_vendor", vid), "供应商不得被删除");
    }

    // ---------- 夹具：全部走裸 SQL，故列名/必填项以 information_schema 为准 ----------

    private Long createBankAccount(String suffix) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_bank_account (account_no, account_name, enterprise_id, deleted) "
                        + "VALUES (?, ?, ?, 0) RETURNING id",
                Long.class, "P134" + suffix, "ACC" + suffix, ent());
    }

    private void createBankStatement(Long accountId, String suffix) {
        jdbcTemplate.update(
                "INSERT INTO t_bank_statement (account_id, tx_date, tx_type, amount, "
                        + "enterprise_id, deleted) VALUES (?, CURRENT_DATE, 'INCOME', 100, ?, 0)",
                accountId, ent());
    }

    private Long createAgency(String suffix) {
        // ⚠️ t_agency **没有 enterprise_id 列**（实测 information_schema）—— 别凭印象加
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_agency (agency_code, agency_name, deleted) "
                        + "VALUES (?, ?, 0) RETURNING id",
                Long.class, "P134" + suffix, "P134" + suffix);
    }

    private Long createParty(String table, String suffix) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO " + table + " (code, name, enterprise_id, deleted) "
                        + "VALUES (?, ?, ?, 0) RETURNING id",
                Long.class, "P134" + suffix, "P134" + suffix, ent());
    }

    private int count(String table, String col, Long id) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + col + " = ?", Integer.class, id);
        return n == null ? 0 : n;
    }

    private int deletedFlag(String table, Long id) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT deleted FROM " + table + " WHERE id = ?", Integer.class, id);
        return n == null ? -1 : n;
    }

    @Test
    @DisplayName("守卫自证：6 个服务的 delete 都必须带 @ReferenceGuard（任一被摘即红）")
    void allSixServicesAreGuarded() throws Exception {
        Object[][] targets = {
                {bankAccountService, "银行账户"},
                {agencyService, "代理公司"},
                {userService, "用户"},
                {roleService, "角色"},
                {customerService, "客户"},
                {vendorService, "供应商"},
        };
        for (Object[] t : targets) {
            Method m = targetClass(t[0]).getMethod("delete", Long.class);
            assertTrue(m.getAnnotation(ReferenceGuard.class) != null,
                    t[1] + " 的 delete 必须标注 @ReferenceGuard");
        }
    }

    /**
     * 取 Spring AOP 的**目标类**。
     *
     * <p>🔴 不能用 {@code bean.getClass()}：Service 是按接口注入的，实际 bean 是
     * <b>JDK 动态代理</b>，{@code getClass()} 返回代理类，代理类的方法上**看不到**
     * 实现类里的 {@code @ReferenceGuard} ⇒ 自证会假红。
     * 用 {@code AopUtils.getTargetClass} 走 Spring AOP 的 TargetClassAware 语义。
     */
    private static Class<?> targetClass(Object bean) {
        return org.springframework.aop.support.AopUtils.getTargetClass(bean);
    }
}
