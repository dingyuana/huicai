package com.huicai.security;

import com.huicai.common.security.ReferenceGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P134 批次 4 —— 结构守卫 G7：防「引用型 DELETE 端点漏加守卫」复发。
 *
 * <p>🔴 <b>本守卫只依赖两个可靠真值源</b>：<b>Spring 运行时上下文</b>（方法与注解
 * 真实存在于 JVM）与<b>数据库</b>（表与外键真实存在）。
 * SPEC §8.7 记录了本项目<b>静态扫描器连续三次产出错误结论</b>，
 * 因此本守卫<b>刻意不做</b>「Controller → Service 方法」的解析
 * ——三次失败里有两次正是卡在这一步（实现类分散 9 个包、有接口没有 Impl 类）。
 *
 * <p>🔴 <b>必须继承 {@code AbstractMapperTest}</b>：裸 {@code @SpringBootTest} 拿到的是
 * <b>H2 内存库</b>，而 G7-3 要查 {@code pg_constraint}/{@code pg_class}
 * ⇒ 直接报 {@code Table "PG_CONSTRAINT" not found (this database is empty)}。
 * 报错信息<b>完全指错方向</b>（看起来像「SQL 写错了」，实则是「连错数据库了」）。
 * <b>判据：报错来自「某个系统表/方言不存在」时，先确认连的是不是目标数据库。</b>
 *
 * <p><b>刻意登记的局限（不掩盖）</b>：本守卫<b>不</b>能自动回答
 * 「某个新加的删除端点，其目标表是否需要守卫」——
 * {@link #REQUIRED_GUARDED_SERVICES} 是<b>按实测证据人工维护</b>的清单。
 * 能自动检出的部分是：端点漏登记、守卫指向不存在的表、守卫被摘掉、清单被悄悄改动。</p>
 */
class ReferenceGuardStructureRealDBTest extends com.huicai.common.test.AbstractMapperTest {

    /**
     * 全部 DELETE 端点（控制器简名 → 路径标识），实测 35 个。
     *
     * <p>⚠️ 本表是<b>人工核实的快照</b>。新增删除端点时<b>必须</b>在此登记，
     * 否则 {@link #allDeleteEndpointsAreRegistered} 因数量不符而红 ——
     * 这正是本守卫存在的意义：让「漏登记」成为可见的红灯。
     */
    private static final List<String> ALL_DELETE_ENDPOINTS = List.of(
            "AgencyController", "AiFeedbackLogController", "ArapSettlementController",
            "AssetCardController", "AssetCategoryController", "AssetDisposalController",
            "AssetInventoryController", "AssignmentController", "AttachmentController",
            "BadDebtController", "BankAccountController", "BankJournalController",
            "BankStatementController", "BusinessDocController", "CashJournalController",
            "ClassificationRuleController", "CustomerController", "DeptController",
            "EmployeeController", "EnterpriseController", "MenuController",
            "PeriodController", "ReconciliationController", "RoleController",
            "SubjectController", "SummaryLibController", "SysConfigController",
            "TaxController#taxType", "TaxController#outputInvoice", "TicketController",
            "UserController", "VendorController", "VoucherController",
            "VoucherTemplateController", "VoucherTypeController");

    /**
     * 已实测「删了会静默丢数据」的表 ⇒ 其 Service 实现类<b>必须</b>带 {@code @ReferenceGuard}。
     *
     * <p>🔴 只收<b>有实测证据</b>的条目（SPEC §8.2 真库活引用统计 + 逐个读实现体），
     * <b>不按「像」推定</b>。新增条目必须先取证。
     */
    private static final List<String> REQUIRED_GUARDED_SERVICES = List.of(
            "SubjectServiceImpl",            // t_subject        ← 凭证分录 90
            "BankAccountServiceImpl",        // t_bank_account   ← 流水 80
            "AgencyServiceImpl",             // t_agency         ← 13
            "UserServiceImpl",               // t_user           ← 12
            "RoleServiceImpl",               // t_role           ← t_role_menu 150
            "MenuServiceImpl",               // t_menu           ← t_role_menu 150（硬删）
            "EnterpriseServiceImpl",         // t_enterprise
            "VoucherTypeServiceImpl",        // t_voucher_type   ← 凭证 45
            "CustomerServiceImpl",           // t_customer
            "VendorServiceImpl");            // t_vendor

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("G7-1 全部 DELETE 端点都已登记（新增端点而未登记 ⇒ 红）")
    void allDeleteEndpointsAreRegistered() {
        long actual = countRealDeleteEndpoints();
        assertEquals(ALL_DELETE_ENDPOINTS.size(), actual,
                "实际 DELETE 端点数=" + actual + "，与登记表 " + ALL_DELETE_ENDPOINTS.size()
                        + " 不符：有端点未登记到 ALL_DELETE_ENDPOINTS，或登记表有已删除的条目。"
                        + "实际清单请用 RequestMappingHandlerMapping 打印后逐个核对。");
    }

    @Test
    @DisplayName("G7-2 必须守卫的 10 个 Service 实现类都带 @ReferenceGuard（任一被摘即红）")
    void requiredServicesAreGuarded() {
        List<String> missing = new ArrayList<>();
        for (String simpleName : REQUIRED_GUARDED_SERVICES) {
            Class<?> impl = loadImpl(simpleName);
            boolean guarded = false;
            for (Method m : impl.getMethods()) {
                if (m.getName().startsWith("delete") || m.getName().startsWith("removeById")) {
                    if (m.getAnnotation(ReferenceGuard.class) != null) {
                        guarded = true;
                        break;
                    }
                }
            }
            if (!guarded) {
                missing.add(simpleName);
            }
        }
        assertTrue(missing.isEmpty(), "以下 Service 的删除方法缺少 @ReferenceGuard：" + missing);
    }

    @Test
    @DisplayName("G7-3 守卫的 parent 必须是真实存在且有入向外键的表（防拼错表名）")
    void everyGuardParentReallyHasIncomingForeignKeys() {
        List<String> bogus = new ArrayList<>();
        for (String parent : allGuardedParents()) {
            Integer fk = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_constraint c "
                            + "JOIN pg_class p ON p.oid = c.confrelid "
                            + "JOIN pg_namespace n ON n.oid = p.relnamespace "
                            + "WHERE c.contype = 'f' AND p.relname = ? AND n.nspname = current_schema()",
                    Integer.class, parent);
            if (fk == null || fk == 0) {
                bogus.add(parent + "（该表无入向外键，守卫是纯开销；更糟的是拼错表名会让每次查询都失败）");
            }
        }
        assertTrue(bogus.isEmpty(), "以下守卫的 parent 无入向外键：" + bogus);
    }

    @Test
    @DisplayName("G7-4 规模棘轮：登记表与必需清单被改动时必须显式更新")
    void registryRatchet() {
        assertEquals(35, ALL_DELETE_ENDPOINTS.size(), "DELETE 端点登记表规模已变，须复核后更新棘轮");
        assertEquals(10, REQUIRED_GUARDED_SERVICES.size(),
                "必须守卫的 Service 清单规模已变，须复核后更新棘轮");
    }

    // ---------- 运行时 / 数据库 真值源 ----------

    private long countRealDeleteEndpoints() {
        var mappings = ctx.getBeansOfType(
                org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
        if (mappings.isEmpty()) {
            throw new IllegalStateException(
                    "拿不到 RequestMappingHandlerMapping —— 守卫无法取真值源，拒绝以默认值放行");
        }
        var handlerMapping = mappings.values().iterator().next();
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(e -> e.getKey().getMethodsCondition() != null
                        && e.getKey().getMethodsCondition().getMethods().stream()
                        .anyMatch(rm -> "DELETE".equals(rm.name())))
                .count();
    }

    private List<String> allGuardedParents() {
        List<String> out = new ArrayList<>();
        for (String simpleName : REQUIRED_GUARDED_SERVICES) {
            Class<?> impl = loadImpl(simpleName);
            for (Method m : impl.getMethods()) {
                ReferenceGuard g = m.getAnnotation(ReferenceGuard.class);
                if (g != null) {
                    out.add(g.parent());
                }
            }
        }
        return out;
    }

    /**
     * 从 Spring 上下文按<b>实现类简名</b>取目标类。
     *
     * <p>🔴 三种更直觉的写法都踩过坑：
     * <ul>
     *   <li>{@code Class.forName("XxxServiceImpl")} —— 缺包名；实现类分散 9 个包，
     *       漏了时报 {@code ClassNotFoundException}，看起来像「类不存在」，
     *       实则是「我没找对地方」，<b>错误信息指错方向</b>。</li>
     *   <li>{@code ctx.getBean("AgencyService")} —— 字符串是<b>bean 名</b>
     *       （实际是 {@code agencyServiceImpl}），不是类型名。</li>
     *   <li>硬编码 9 个包名逐一 {@code forName} —— 每加一个 Service 就要改一次。</li>
     * </ul>
     * 正解：<b>遍历上下文里所有 bean，按目标类简名匹配</b>。
     * 判据：定位「某个类」时优先用<b>已经在运行的容器</b>作索引，而不是靠名字反推坐标。
     */
    private Class<?> loadImpl(String simpleName) {
        for (Object bean : ctx.getBeansOfType(Object.class).values()) {
            Class<?> target = AopUtils.getTargetClass(bean);
            if (target != null && target.getSimpleName().equals(simpleName)) {
                return target;
            }
        }
        throw new IllegalStateException("Spring 上下文里找不到实现类 " + simpleName
                + "（已遍历 " + ctx.getBeansOfType(Object.class).size() + " 个 bean）");
    }
}