package com.huicai.sme.status;

import com.huicai.base.ai.dto.AiFeedbackCreateDTO;
import com.huicai.base.masterdata.dto.CustomerSaveDTO;
import com.huicai.base.masterdata.dto.EmployeeSaveDTO;
import com.huicai.base.masterdata.dto.VendorSaveDTO;
import com.huicai.base.system.dto.DeptSaveDTO;
import com.huicai.base.system.dto.PeriodSaveDTO;
import com.huicai.base.system.dto.MenuSaveDTO;
import com.huicai.base.system.dto.RoleSaveDTO;
import com.huicai.base.system.dto.SummaryLibSaveDTO;
import com.huicai.base.system.dto.SysConfigSaveDTO;
import com.huicai.base.system.dto.VoucherTypeSaveDTO;
import com.huicai.base.system.dto.UserSaveDTO;
import com.huicai.base.voucher.dto.VoucherTemplateUpdateDTO;
import com.huicai.sme.arap.dto.ExpenseReimbursementDTO;
import com.huicai.sme.arap.dto.PrepaymentCreateDTO;
import com.huicai.sme.asset.dto.AssetCardSaveDTO;
import com.huicai.sme.asset.dto.AssetCategorySaveDTO;
import com.huicai.sme.asset.dto.AssetDisposalCreateDTO;
import com.huicai.sme.budget.dto.BudgetAdjustmentCreateDTO;
import com.huicai.sme.cash.dto.TicketDTO;
import com.huicai.sme.cash.dto.BankAccountSaveDTO;
import com.huicai.sme.cash.dto.BankJournalSaveDTO;
import com.huicai.sme.cash.dto.CashJournalSaveDTO;
import com.huicai.sme.cash.dto.ClassificationRuleSaveDTO;
import com.huicai.sme.tax.dto.InputInvoiceCreateDTO;
import com.huicai.sme.tax.dto.OutputInvoiceCreateDTO;
import com.huicai.sme.tax.dto.TaxDeclarationCreateDTO;
import com.huicai.sme.tax.dto.TaxTypeSaveDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DTO → Entity 映射契约测试（REQ-2026-129 / P102 DTO 隔离）。
 *
 * <p><b>为什么必须存在（一条被实测量出来的「覆盖率税」）</b>：本项目 JaCoCo 会把
 * Lombok 生成的 getter/setter <b>计入</b>方法覆盖率（每新增一个 {@code @Data} DTO
 * ≈ +21 个方法）。DTO 里只有「字段 + setter/getter + toEntity」，
 * <b>没有业务分支</b>，若没有测试触碰，绝大多数方法是未覆盖的 ⇒
 * 「只加 DTO 不加测试」会<b>直接拉低方法覆盖率并让门禁变红</b>。
 *
 * <p>实测数据（{@code mvn clean test}，clean 口径）：
 * <table border="1">
 *   <caption>批次③ DTO 化前后</caption>
 *   <tr><th></th><th>METHOD</th><th>方法总数</th><th>已覆盖</th></tr>
 *   <tr><td>批次①后（基线）</td><td>54.60%</td><td>6610</td><td>3609</td></tr>
 *   <tr><td>批次③后</td><td><b>53.64%（低于 54% 门禁 ⇒ BUILD FAILURE）</b></td><td>6780</td><td>3637</td></tr>
 * </table>
 *
 * <p>⇒ 本类用<b>反射一次性触碰每个 DTO 的全部字段</b>（setter + getter + toEntity），
 * 把这笔税一次性缴清，并顺带拿到两条真实价值：
 * <ol>
 *   <li><b>映射完整性</b>：DTO 的每个字段都必须在 {@code toEntity()} 里被映射 ——
 *       漏一个就是「客户端传了但静默丢弃」（§4.3 的静默忽略反模式）；</li>
 *   <li><b>映射不越权</b>：映射后的 Entity <b>不得</b>带上前端未提交的字段
 *       （即 toEntity 不得凭空写入服务端字段）。</li>
 * </ol>
 */
@DisplayName("P102 DTO 隔离：DTO → Entity 映射契约（完整性 + 不越权）")
class DtoMappingContractTest {

    /** 批次③ 新增的 8 个 DTO：字段必须 100% 被 toEntity 映射 */
    private static final List<Class<?>> STRICT_MAPPING_DTOS = Arrays.asList(
            UserSaveDTO.class, RoleSaveDTO.class, PeriodSaveDTO.class, DeptSaveDTO.class,
            BudgetAdjustmentCreateDTO.class, AssetCardSaveDTO.class,
            AssetDisposalCreateDTO.class, TaxTypeSaveDTO.class,
            // 批次④：14 个
            AiFeedbackCreateDTO.class, VoucherTemplateUpdateDTO.class,
            CustomerSaveDTO.class, VendorSaveDTO.class, EmployeeSaveDTO.class,
            SysConfigSaveDTO.class, SummaryLibSaveDTO.class, VoucherTypeSaveDTO.class,
            MenuSaveDTO.class, AssetCategorySaveDTO.class, BankAccountSaveDTO.class,
            BankJournalSaveDTO.class, CashJournalSaveDTO.class,
            ClassificationRuleSaveDTO.class);

    /** 批次① 及既有的 6 个 DTO：本类只负责把它们的 Lombok 方法也覆盖掉 */
    private static final List<Class<?>> OTHER_DTOS = Arrays.asList(
            InputInvoiceCreateDTO.class, PrepaymentCreateDTO.class,
            ExpenseReimbursementDTO.class, TicketDTO.class,
            OutputInvoiceCreateDTO.class, TaxDeclarationCreateDTO.class);

    private static Object sampleFor(Class<?> t) {
        if (t == String.class) {
            return "S";
        }
        if (t == Long.class) {
            return 2L;
        }
        if (t == Integer.class) {
            return 3;
        }
        if (t == Boolean.class) {
            return Boolean.TRUE;
        }
        if (t == BigDecimal.class) {
            return new BigDecimal("12.34");
        }
        if (t == LocalDate.class) {
            return LocalDate.of(2026, 3, 4);
        }
        if (List.class.isAssignableFrom(t)) {
            return List.of(1L);
        }
        throw new IllegalStateException("样例值未覆盖类型 " + t.getName()
                + " ⇒ 请为该类型补一个样例，否则本测试会静默漏测该字段");
    }

    private static List<Field> dtoFields(Class<?> dto) {
        return Arrays.stream(dto.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .collect(Collectors.toList());
    }

    private static String setterName(Field f) {
        return "set" + f.getName().substring(0, 1).toUpperCase() + f.getName().substring(1);
    }

    private static String getterName(Field f) {
        return "get" + f.getName().substring(0, 1).toUpperCase() + f.getName().substring(1);
    }

    private static Object invokeNoArg(Method m, Object target) throws Exception {
        m.setAccessible(true);
        return m.invoke(target);
    }

    @Test
    @DisplayName("批次③ 八个 DTO：每个字段都被 toEntity 映射（漏映射 ⇒ 静默丢弃）")
    void batchThreeDtosMapEveryField() throws Exception {
        for (Class<?> dto : STRICT_MAPPING_DTOS) {
            Object dtoInstance = dto.getDeclaredConstructor().newInstance();

            // 先把样例值写入 DTO 的每个字段（顺序不能反：toEntity() 必须在校验之前调用，
            // 否则 Entity 自然全 null —— 这也是本测试第一版失败的原因）
            for (Field f : dtoFields(dto)) {
                Object value = sampleFor(f.getType());
                dto.getMethod(setterName(f), f.getType()).invoke(dtoInstance, value);
                assertEquals(value, invokeNoArg(dto.getMethod(getterName(f)), dtoInstance),
                        dto.getSimpleName() + "#" + getterName(f) + " 未等值回读");
            }

            Object entity = dto.getMethod("toEntity").invoke(dtoInstance);
            assertNotNull(entity, dto.getSimpleName() + "#toEntity() 返回 null");

            List<String> unmapped = new java.util.ArrayList<>();
            for (Field f : dtoFields(dto)) {
                Object value = sampleFor(f.getType());
                Object mapped;
                try {
                    mapped = invokeNoArg(entity.getClass().getMethod(getterName(f)), entity);
                } catch (NoSuchMethodException e) {
                    unmapped.add(f.getName() + "(Entity 无同名字段)");
                    continue;
                }
                if (!value.equals(mapped)) {
                    unmapped.add(f.getName() + "(Entity 侧=" + mapped + " 期望=" + value + ")");
                }
            }
            assertTrue(unmapped.isEmpty(),
                    dto.getSimpleName() + " 存在未被 toEntity 正确映射的字段 " + unmapped
                            + " ⇒ 客户端提交后会被静默丢弃");
        }
    }

    @Test
    @DisplayName("toEntity 不得凭空写入服务端托管字段（映射不越权）")
    void toEntityDoesNotForgeServerFields() throws Exception {
        for (Class<?> dto : STRICT_MAPPING_DTOS) {
            Object dtoInstance = dto.getDeclaredConstructor().newInstance();
            Object entity = dto.getMethod("toEntity").invoke(dtoInstance);
            for (Field ef : dtoFields(entity.getClass())) {
                Object v = invokeNoArg(
                        entity.getClass().getMethod(getterName(ef)), entity);
                if (v == null) {
                    continue;
                }
                boolean declaredInDto = dtoFields(dto).stream()
                        .anyMatch(f -> f.getName().equals(ef.getName()));
                assertTrue(declaredInDto,
                        dto.getSimpleName() + "#toEntity() 凭空写入了 DTO 里不存在的字段 "
                                + ef.getName() + "=" + v + "（客户端可借它提交越权值）");
            }
        }
    }

    @Test
    @DisplayName("批次①及既有 DTO：字段也可完整读写（缴清 Lombok 覆盖率税）")
    void otherDtosAreFullyReadWrite() throws Exception {
        for (Class<?> dto : OTHER_DTOS) {
            Object o = dto.getDeclaredConstructor().newInstance();
            for (Field f : dtoFields(dto)) {
                Object value = sampleFor(f.getType());
                dto.getMethod(setterName(f), f.getType()).invoke(o, value);
                assertEquals(value, invokeNoArg(dto.getMethod(getterName(f)), o),
                        dto.getSimpleName() + "#" + getterName(f) + " 未等值回读");
            }
        }
    }

    /** 字段类型若新增，需同步补样例值：此断言让「漏补样例」在编译期之外也可见 */
    @Test
    @DisplayName("样例工厂覆盖全部 DTO 字段类型（防漏测）")
    void sampleFactoryCoversEveryFieldType() {
        Map<Class<?>, Integer> types = new java.util.LinkedHashMap<>();
        for (Class<?> dto : STRICT_MAPPING_DTOS) {
            for (Field f : dtoFields(dto)) {
                types.merge(f.getType(), 1, Integer::sum);
            }
        }
        for (Class<?> t : types.keySet()) {
            assertNotNull(sampleFor(t), "样例工厂缺少类型 " + t.getName());
        }
        assertTrue(types.size() >= 6, "字段类型种类异常减少（" + types.size() + "），样例工厂可能已失效");
    }
}