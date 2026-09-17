package com.huicai.sme.arap.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 费用汇总报表服务（P76）
 * <p>
 * 输出：按维度（部门/费用类型/员工）× 期间区间 的报销费用聚合（单数/金额/人均/同比/环比 + 导出）。
 * 补齐"按什么维度汇总花了多少钱"的管理报表。
 * <p>
 * 口径（对齐 SPEC P76 V1.0，代码实证 2026-09-17）：
 * <ul>
 *   <li>数据源 t_expense_reimbursement，status ∈ (APPROVED, VOUCHERED)（生效单；DRAFT/SUBMITTED/REJECTED 不计）</li>
 *   <li>期间归属：approvedAt 的 yyyyMM（无则 createdAt）；区间 [periodFrom, periodTo]（含端点）</li>
 *   <li>维度：DEPT（按 deptId，名取 t_dept）/ EXPENSE_TYPE（按 expenseType 字符串）/ EMPLOYEE（按 employeeId，名取 t_employee）</li>
 *   <li>指标：count（单数）、amount（金额合计 BigDecimal）</li>
 *   <li>perCapita 仅 DEPT 维度：amount / 期末在职人数（t_employee.isActive=true 且 deptId 匹配）；其余维度返回 null</li>
 *   <li>同比 amountYoy：去年同期同区间（-12 月）同维度金额合计，无上年数据返回 null</li>
 *   <li>环比 amountMom：上一等长区间，无数据返回 null</li>
 * </ul>
 * 数据权限：t_expense_reimbursement / t_employee 非共享表，由
 * {@code EnterpriseDataPermissionInterceptor} 自动注入 enterprise_id；t_dept 为共享表（维度名查用，无敏感）。
 */
public interface ExpenseSummaryReportService {

    /**
     * 查询费用汇总
     *
     * @param periodFrom  区间起（YYYYMM，必填，含端点）
     * @param periodTo    区间止（YYYYMM，必填，含端点，≥ periodFrom）
     * @param groupBy     维度 DEPT(默认)/EXPENSE_TYPE/EMPLOYEE
     * @param includeYoy  是否出同比列（默认 true，null 按 true）
     * @param includeMom  是否出环比列（默认 true，null 按 true）
     */
    ExpenseSummaryVO getSummary(String periodFrom, String periodTo, String groupBy,
                                Boolean includeYoy, Boolean includeMom);

    /** 一行一维度值的费用汇总行 */
    record ExpenseSummaryRowVO(
        Long dimId, String dimName,
        int count, BigDecimal amount,
        BigDecimal perCapita,          // 仅 DEPT 维度输出，其余 null
        BigDecimal amountYoy,          // 同比；无上年数据 / includeYoy=false 时 null
        BigDecimal amountMom          // 环比；无上年数据 / includeMom=false 时 null
    ) {}

    /** 费用汇总 VO */
    record ExpenseSummaryVO(
        String periodFrom, String periodTo, String groupBy,
        List<ExpenseSummaryRowVO> rows,
        int totalCount, BigDecimal totalAmount
    ) {}

    /** 导出费用汇总（hutool ExcelUtil，对齐既有报表中心导出） */
    void exportSummary(String periodFrom, String periodTo, String groupBy,
                       Boolean includeYoy, Boolean includeMom,
                       jakarta.servlet.http.HttpServletResponse response);
}
