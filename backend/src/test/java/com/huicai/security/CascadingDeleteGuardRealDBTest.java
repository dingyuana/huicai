package com.huicai.security;

import com.huicai.agency.tenant.service.EnterpriseService;
import com.huicai.base.system.service.MenuService;
import com.huicai.base.system.service.VoucherTypeService;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.security.ReferenceGuard;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P134 批次 3 —— 级联硬删与其余引用型 DELETE 端点。
 *
 * <p>🔴 本批次的**首要对象是 {@code t_menu}**：实测 {@code t_role_menu} 有 <b>150 条活引用</b>
 * 且<b>没有 {@code deleted} 列</b> ⇒ {@code MenuServiceImpl#delete} 里的
 * {@code roleMenuMapper.delete(...)} 是<b>硬删</b>，会无声抹掉所有角色对整棵子树的授权 ——
 * 这是<b>即时数据损失</b>，与「静默孤儿」性质不同（后者还能靠查询发现）。</p>
 *
 * <p>⚠️ <b>反面对照（刻意不拦的）</b>：{@code t_voucher_template_line} 实测<b>有</b>
 * {@code deleted} 列，且模板明细是<b>从属关系</b>（composition）⇒
 * {@code VoucherTemplateServiceImpl#delete} 的级联<b>软删是正确的设计，不加守卫</b>。
 * <b>判据：授权是「别的实体引用你」⇒ 该拦；明细是「你拥有它」⇒ 不该拦。</b></p>
 */
class CascadingDeleteGuardRealDBTest extends AbstractMapperTest {

    @Autowired private MenuService menuService;
    @Autowired private VoucherTypeService voucherTypeService;
    @Autowired private EnterpriseService enterpriseService;

    @Autowired private JdbcTemplate jdbcTemplate;

    private Long ent() {
        return com.huicai.common.context.EnterpriseContextHolder.get();
    }

    // ⚠️ 菜单**用基类 createMenu()**：它走 Mapper 路径并正确落 menu_code；
    // 裸 SQL 插 t_menu 是 §4.5 第 12 条明令禁止的（种子 id 序列 + 必填列双重坑）。

    @Test
    @DisplayName("AT-134-1 菜单：被角色授权时拒绝删除，且授权行数不变（负向断言）")
    void menuWithGrantsCannotBeDeleted() {
        Long menuId = createMenu("P134MENU");
        Long roleId = createRole("M3R");
        jdbcTemplate.update("INSERT INTO t_role_menu (role_id, menu_id) VALUES (?, ?)",
                roleId, menuId);
        int grantsBefore = grants(menuId);

        BusinessException ex = assertThrows(BusinessException.class, () -> menuService.delete(menuId));
        assertTrue(ex.getMessage().contains("授权"),
                "须说明是授权冲突，实际：" + ex.getMessage());

        assertEquals(grantsBefore, grants(menuId), "角色授权行数不得变化（硬删不可逆）");
        assertEquals(0, deletedFlag("t_menu", menuId), "菜单不得被删除");
    }

    @Test
    @DisplayName("正控：未被授权的菜单仍可删除（连同子树）")
    void unusedMenuCanBeDeleted() {
        Long menuId = createMenu("P134MENUFREE");
        menuService.delete(menuId);
        assertEquals(1, deletedFlag("t_menu", menuId), "无授权菜单应能删除");
    }

    @Test
    @DisplayName("子树：子菜单有授权时，父菜单也须拒绝（守卫只覆盖传入 id 的风险点）")
    void parentMenuWithGrantedChildCannotBeDeleted() {
        Long parent = createMenu("P134PARENT");
        Long child = createMenu("P134CHILD");
        jdbcTemplate.update("UPDATE t_menu SET parent_id = ? WHERE id = ?", parent, child);
        Long roleId = createRole("M3RC");
        jdbcTemplate.update("INSERT INTO t_role_menu (role_id, menu_id) VALUES (?, ?)",
                roleId, child);

        BusinessException ex = assertThrows(BusinessException.class, () -> menuService.delete(parent));
        assertTrue(ex.getMessage().contains("子菜单"),
                "须指出问题出在子菜单，实际：" + ex.getMessage());
        assertEquals(0, deletedFlag("t_menu", parent), "父菜单不得被删除");
        assertEquals(0, deletedFlag("t_menu", child), "子菜单不得被删除");
    }

    @Test
    @DisplayName("凭证类型：被凭证引用时拒绝删除（实测 45 条活引用，原实现走继承的 removeById）")
    void voucherTypeInUseCannotBeRemoved() {
        Long typeId = createVoucherType();
        jdbcTemplate.update(
                "INSERT INTO t_voucher (voucher_no, period, voucher_type_id, status, total_debit, "
                        + "total_credit, enterprise_id, deleted) "
                        + "VALUES ('P134-VT', '202601', ?, 'DRAFT', 1, 1, ?, 0)", typeId, ent());

        assertThrows(BusinessException.class, () -> voucherTypeService.removeById((Serializable) typeId));
        assertEquals(0, deletedFlag("t_voucher_type", typeId), "凭证类型不得被删除");
    }

    @Test
    @DisplayName("正控：未被引用的凭证类型仍可删除")
    void unusedVoucherTypeCanBeRemoved() {
        Long typeId = createVoucherType();
        voucherTypeService.removeById((Serializable) typeId);
        assertEquals(1, deletedFlag("t_voucher_type", typeId), "无引用类型应能删除");
    }

    @Test
    @DisplayName("客户企业：被合同引用时拒绝删除（负向断言：合同数不变）")
    void enterpriseWithContractCannotBeDeleted() {
        Long entId = createEnterprise();
        jdbcTemplate.update(
                "INSERT INTO t_contract (contract_no, enterprise_id, agency_id, start_date, "
                        + "end_date, deleted) VALUES (?, ?, 1, CURRENT_DATE, "
                        + "CURRENT_DATE + 30, 0)", "P134-CT-" + entId, entId);
        int contracts = count("t_contract", "enterprise_id", entId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseService.delete(entId));
        assertTrue(ex.getMessage().contains("被以下数据引用"),
                "须是引用冲突，实际：" + ex.getMessage());
        assertEquals(0, deletedFlag("t_enterprise", entId), "企业不得被删除");
        assertEquals(contracts, count("t_contract", "enterprise_id", entId), "合同数不得变化");
    }

    @Test
    @DisplayName("正控：未被引用的客户企业仍可删除")
    void unreferencedEnterpriseCanBeDeleted() {
        Long entId = createEnterprise();
        enterpriseService.delete(entId);
        assertEquals(1, deletedFlag("t_enterprise", entId), "无引用企业应能删除");
    }

    @Test
    @DisplayName("守卫自证：三个服务的删除方法都须带 @ReferenceGuard")
    void allThreeAreGuarded() throws Exception {
        assertTrue(guardOn(menuService, "delete", Long.class) != null, "MenuService#delete 须有守卫");
        assertTrue(guardOn(menuService, "delete", Long.class).children().length == 1,
                "菜单守卫须声明 t_role_menu");
        assertTrue(guardOn(voucherTypeService, "removeById", Serializable.class) != null,
                "VoucherTypeService#removeById 须有守卫（它是覆写来的，注解极易漏）");
        assertTrue(guardOn(enterpriseService, "delete", Long.class) != null,
                "EnterpriseService#delete 须有守卫");
    }

    private ReferenceGuard guardOn(Object bean, String name, Class<?>... args) throws Exception {
        Method m = org.springframework.aop.support.AopUtils.getTargetClass(bean)
                .getMethod(name, args);
        return m.getAnnotation(ReferenceGuard.class);
    }

    private Long createVoucherType() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_voucher_type (name, code, enterprise_id, deleted) "
                        + "VALUES (?, ?, ?, 0) RETURNING id",
                Long.class, "P134VT", "P134VT", ent());
    }

    // ⚠️ t_enterprise **没有 name 列**（是 enterprise_name），且**没有 enterprise_id 列**
    private Long createEnterprise() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_enterprise (enterprise_code, enterprise_name, status, deleted) "
                        + "VALUES (?, ?, 'ACTIVE', 0) RETURNING id",
                Long.class, "P134ENT" + System.nanoTime() % 100000, "P134企业");
    }

    private int count(String table, String col, Long id) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + col + " = ?", Integer.class, id);
        return n == null ? 0 : n;
    }

    private int grants(Long menuId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_role_menu WHERE menu_id = ?", Integer.class, menuId);
        return n == null ? 0 : n;
    }

    private int deletedFlag(String table, Long id) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT deleted FROM " + table + " WHERE id = ?", Integer.class, id);
        return n == null ? -1 : n;
    }
}