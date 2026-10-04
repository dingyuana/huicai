package com.huicai.sme.status;

import com.huicai.sme.arap.dto.ExpenseReimbursementDTO;
import com.huicai.sme.arap.dto.PrepaymentCreateDTO;
import com.huicai.sme.cash.dto.TicketDTO;
import com.huicai.sme.tax.dto.InputInvoiceCreateDTO;
import com.huicai.sme.tax.dto.OutputInvoiceCreateDTO;
import com.huicai.sme.tax.dto.TaxDeclarationCreateDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
            "rejectReason", "applicantId", "reimbNo");

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