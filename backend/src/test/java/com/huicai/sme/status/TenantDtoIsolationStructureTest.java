package com.huicai.sme.status;

import com.huicai.base.system.controller.DeptController;
import com.huicai.base.system.controller.PeriodController;
import com.huicai.base.system.controller.RoleController;
import com.huicai.base.system.controller.UserController;
import com.huicai.sme.arap.controller.ExpenseReimbursementController;
import com.huicai.sme.arap.controller.PrepaymentController;
import com.huicai.sme.arap.dto.ExpenseReimbursementDTO;
import com.huicai.sme.arap.dto.PrepaymentCreateDTO;
import com.huicai.sme.asset.controller.AssetCardController;
import com.huicai.sme.asset.controller.AssetDisposalController;
import com.huicai.sme.asset.dto.AssetCardSaveDTO;
import com.huicai.sme.asset.dto.AssetDisposalCreateDTO;
import com.huicai.sme.budget.controller.BudgetController;
import com.huicai.sme.budget.dto.BudgetAdjustmentCreateDTO;
import com.huicai.sme.cash.controller.TicketController;
import com.huicai.sme.cash.dto.TicketDTO;
import com.huicai.sme.tax.controller.TaxController;
import com.huicai.sme.tax.dto.InputInvoiceCreateDTO;
import com.huicai.sme.tax.dto.OutputInvoiceCreateDTO;
import com.huicai.sme.tax.dto.TaxDeclarationCreateDTO;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DTO 隔离的**结构性**回归锁（REQ-2026-129 / P102 §7 批次①）。
 *
 * <p><b>为什么不靠行为测试</b>：各端点「客户端传 status 会不会被采纳」已由真库用例
 * 覆盖（如 {@code InputInvoiceCreateStatusRealDBTest}）。但那些用例锁的是
 * <b>Service 的兜底逻辑</b>，删掉 DTO 之后它们仍然绿 —— 因为 Service 侧还有
 * 无条件 setStatus。本类锁的是<b>另一件事</b>：<b>DTO 本身不得声明这些字段</b>，
 * 一旦有人把 {@code status} 加回 DTO，「让越权值无法绑定」就失效了。
 *
 * <p>这类断言用反射即可，成本极低但能挡住最典型的回退方式。
 *
 * <p><b>清单本身也要被实测校正</b>：初版把 {@code declaredDate} 也列进禁用字段，
 * 结果 {@code TaxDeclarationCreateDTO} 报红 —— 查证后确认 {@code t_tax_declaration.declared_date}
 * 是「申报日期」属<b>业务输入</b>（NOT NULL 且无默认值，创建时必须由客户提供），
 * 与 {@code declaredStatus}/{@code declaredPeriod}（流程派生）不是一回事。
 * ⇒ <b>禁用清单里的每一项都要有 DB/流程依据，不能凭字段名相似度归类</b>
 * （与 AGENTS §4.2 第 9 条「同名字段在两张表的允许集不同」同源）。
 */
@DisplayName("P102 DTO 隔离批次①：入参 DTO 不得声明服务端托管字段")
class TenantDtoIsolationStructureTest {

    /** 服务端托管字段：客户端传了也不该被采纳（铁律 #1/#13） */
    private static final Set<String> FORBIDDEN = Set.of(
            // 人工流程状态
            "status", "certificationStatus", "certifiedDate", "declaredStatus",
            "declaredPeriod", "processedBy", "processStatus",
            // 业务单据 / 凭证关联（由流程后写）
            "voucherId", "voucherNo", "docId", "docNo", "reversedFrom",
            "originalVoucherNo", "originalInvoiceNo", "reverseReason",
            // 金额派生字段（Service 计算）
            "taxAmount", "totalAmount", "settledAmount", "unsettledAmount",
            "deductionAmount", "amountExTax",
            // 主键 / 租户 / 逻辑删除 / 审计
            "id", "tenantId", "enterpriseId", "deleted", "version",
            "createdBy", "updatedBy", "createdAt", "updatedAt",
            "auditedBy", "auditedAt", "submittedAt", "approvedAt", "approvedBy",
            "rejectReason", "applicantId", "reimbNo",
            // 批次③ 新增：折旧 / 处置 / 开账 / 登录态派生（均经查证由 Service 产生）
            "accumulatedDepreciation", "netValue", "lastDepreciationPeriod",
            "gainLoss", "openingStatus", "openedAt", "openedBy", "openedByName",
            "lastLoginIp", "lastLoginAt", "deptName", "permissionCodes",
            "children", "periodCode");

    /** 本批次已 DTO 化的控制器：结构性锁「不得再出现 @RequestBody Entity」 */
    private static final List<Class<?>> DTOIZED_CONTROLLERS = Arrays.asList(
            TaxController.class, PrepaymentController.class,
            ExpenseReimbursementController.class, TicketController.class,
            UserController.class, RoleController.class, PeriodController.class,
            DeptController.class, BudgetController.class,
            AssetCardController.class, AssetDisposalController.class);

    private static boolean isEntity(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            if (k.getSimpleName().endsWith("Entity")) {
                return true;
            }
        }
        return false;
    }

    private static List<String> fieldNames(Class<?> dto) {
        return Arrays.stream(dto.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toList());
    }

    private static void assertNoForbiddenFields(Class<?> dto) {
        List<String> leaked = fieldNames(dto).stream()
                .filter(FORBIDDEN::contains)
                .collect(Collectors.toList());
        assertTrue(leaked.isEmpty(),
                dto.getSimpleName() + " 声明了服务端托管字段 " + leaked
                        + " ⇒ 客户端可借它提交越权值，DTO 隔离失效");
    }

    @Test
    @DisplayName("批次① 四个新 DTO 均不含服务端托管字段")
    void batchOneDtosAreClean() {
        assertNoForbiddenFields(InputInvoiceCreateDTO.class);
        assertNoForbiddenFields(PrepaymentCreateDTO.class);
        assertNoForbiddenFields(ExpenseReimbursementDTO.class);
        assertNoForbiddenFields(TicketDTO.class);
    }

    @Test
    @DisplayName("既有的销项 / 纳税申报 DTO 同样不含（防止回退）")
    void existingDtosStayClean() {
        assertNoForbiddenFields(OutputInvoiceCreateDTO.class);
        assertNoForbiddenFields(TaxDeclarationCreateDTO.class);
    }

    @Test
    @DisplayName("批次③ 七个新 DTO 均不含服务端托管字段")
    void batchThreeDtosAreClean() {
        assertNoForbiddenFields(com.huicai.base.system.dto.UserSaveDTO.class);
        assertNoForbiddenFields(com.huicai.base.system.dto.RoleSaveDTO.class);
        assertNoForbiddenFields(com.huicai.base.system.dto.PeriodSaveDTO.class);
        assertNoForbiddenFields(com.huicai.base.system.dto.DeptSaveDTO.class);
        assertNoForbiddenFields(BudgetAdjustmentCreateDTO.class);
        assertNoForbiddenFields(AssetCardSaveDTO.class);
        assertNoForbiddenFields(AssetDisposalCreateDTO.class);
    }

    @Test
    @DisplayName("已 DTO 化的 11 个控制器不再出现 @RequestBody Entity（挡住最典型的回退）")
    void noControllerTakesEntityRequestBody() {
        List<String> violations = new ArrayList<>();
        for (Class<?> ctrl : DTOIZED_CONTROLLERS) {
            for (Method m : ctrl.getDeclaredMethods()) {
                boolean writeEndpoint = m.isAnnotationPresent(PostMapping.class)
                        || m.isAnnotationPresent(PutMapping.class);
                if (!writeEndpoint) {
                    continue;
                }
                for (Parameter p : m.getParameters()) {
                    if (p.isAnnotationPresent(RequestBody.class) && isEntity(p.getType())) {
                        violations.add(ctrl.getSimpleName() + "#" + m.getName()
                                + " 直收 Entity " + p.getType().getSimpleName());
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "以下写端点仍直收 Entity（铁律 #13）：" + violations);
    }

    /**
     * <b>全仓零容忍：不得再出现 {@code @RequestBody Entity} 的写端点</b>。
     *
     * <p><b>为什么曾经需要棘轮基线</b>：字段级门禁
     * {@code scripts/check_entity_status_massassignment.py} 只按
     * 「含 status 字段的 Entity 被直收」判定，而 {@code CustomerEntity}、
     * {@code MenuEntity}、{@code TaxTypeEntity} 等<b>根本没有 status 字段</b>
     * ⇒ 它报「未发现违规」时，全仓按<b>参数类型</b>扫描仍有 **26 处**违规。
     * 于是 2026-10-05 先把 26 处钉成棘轮基线（{@code size==26}），
     * 2026-10-05 批次④ 全部清零后<b>基线已删除</b>，改为零容忍断言。
     *
     * <p>依据 AGENTS §4.5 第 31 条：扫描型守卫的判定依据必须覆盖违规的<b>全部形态</b>。
     */
    private static final Set<String> FORBIDDEN_BODY_CONTROLLERS = Set.of();

    @Test
    @DisplayName("全仓扫描：写端点不得再直收 Entity（铁律 #13 零容忍）")
    void noEntityRequestBodyAnywhere() throws Exception {
        Set<String> actual = scanAllControllersForEntityBodies();
        Set<String> extra = new java.util.TreeSet<>(actual);
        extra.removeAll(FORBIDDEN_BODY_CONTROLLERS);
        assertTrue(extra.isEmpty(),
                "以下写端点直收 Entity（铁律 #13）：" + extra
                        + "；请改用 DTO 入参（参考已完成的 14 个 Save/Create DTO）");
        assertTrue(FORBIDDEN_BODY_CONTROLLERS.isEmpty(),
                "例外清单非空 ⇒ 铁律 #13 已出现开口，先评估再豁免：" + FORBIDDEN_BODY_CONTROLLERS);
    }

    private static Set<String> scanAllControllersForEntityBodies() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> hits = new java.util.TreeSet<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.huicai")) {
            Class<?> ctrl = Class.forName(bd.getBeanClassName());
            for (Method m : ctrl.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(PostMapping.class)
                        && !m.isAnnotationPresent(PutMapping.class)) {
                    continue;
                }
                for (Parameter p : m.getParameters()) {
                    if (p.isAnnotationPresent(RequestBody.class) && isEntity(p.getType())) {
                        hits.add(ctrl.getSimpleName() + "#" + m.getName());
                    }
                }
            }
        }
        return hits;
    }

    @Test
    @DisplayName("反向自检：守卫本身有效（拿一个必含 status 的 Entity 试，应被判违规）")
    void guardDetectsForbiddenField() {
        List<String> entityFields = fieldNames(com.huicai.sme.cash.entity.TicketEntity.class);
        assertTrue(entityFields.contains("status"), "前提失效：TicketEntity 应含 status 字段");
        List<String> leaked = entityFields.stream().filter(FORBIDDEN::contains)
                .collect(Collectors.toList());
        assertFalse(leaked.isEmpty(),
                "守卫失效：Entity 上的 " + leaked + " 未被识别（否则上面的断言都是假绿）");
    }
}