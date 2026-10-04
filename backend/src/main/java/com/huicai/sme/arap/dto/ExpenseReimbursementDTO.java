package com.huicai.sme.arap.dto;

import com.huicai.sme.arap.entity.ExpenseReimbursementEntity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 费用报销单创建/修改入参（REQ-2026-129 / P102 DTO 隔离 批次①）。
 *
 * <p><b>为何要有这个 DTO</b>：原端点直接 {@code @RequestBody ExpenseReimbursementEntity}，
 * 违反铁律 #13；客户端可指定 {@code status=DRAFT/SUBMITTED/APPROVED/...} 直接创建
 * 「已审批通过」的单据，跳过 {@code submit()} 与审批两次人工动作（铁律 #1）。
 *
 * <p>同样剔除 Service 负责生成的字段：{@code reimbNo}（{@code generateNo()}）、
 * {@code applicantId}（= employeeId）、{@code status}（DRAFT）、
 * 以及 {@code docId/voucherId/bankStmtId/submittedAt/approvedAt/approvedBy/rejectReason}
 * 等流程字段。
 */
@Data
public class ExpenseReimbursementDTO {

    @NotNull(message = "员工ID不能为空")
    private Long employeeId;

    private Long deptId;

    @NotBlank(message = "费用类型不能为空")
    @Size(max = 64, message = "费用类型长度不能超过 64")
    private String expenseType;

    @NotNull(message = "报销金额不能为空")
    @DecimalMin(value = "0.01", message = "报销金额必须大于 0")
    private BigDecimal amount;

    @Size(max = 200, message = "摘要过长")
    private String summary;

    @Size(max = 1000, message = "附件ID串过长")
    private String attachmentIds;

    public ExpenseReimbursementEntity toEntity() {
        ExpenseReimbursementEntity e = new ExpenseReimbursementEntity();
        e.setEmployeeId(employeeId);
        e.setDeptId(deptId);
        e.setExpenseType(expenseType);
        e.setAmount(amount);
        e.setSummary(summary);
        e.setAttachmentIds(attachmentIds);
        return e;
    }
}