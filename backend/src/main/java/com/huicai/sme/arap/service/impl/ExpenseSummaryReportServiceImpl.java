package com.huicai.sme.arap.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.masterdata.entity.EmployeeEntity;
import com.huicai.base.masterdata.mapper.EmployeeMapper;
import com.huicai.base.system.entity.DeptEntity;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.entity.ExpenseReimbursementEntity;
import com.huicai.sme.arap.mapper.ExpenseReimbursementMapper;
import com.huicai.sme.arap.service.ExpenseSummaryReportService;
import com.huicai.sme.arap.service.ExpenseSummaryReportService.ExpenseSummaryRowVO;
import com.huicai.sme.arap.service.ExpenseSummaryReportService.ExpenseSummaryVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 费用汇总报表实现（P76）。
 * <p>
 * 口径（对齐 SPEC P76 V1.0，代码实证 2026-09-17）：
 * <ul>
 *   <li>数据源 t_expense_reimbursement，status ∈ (APPROVED, VOUCHERED)（生效单；DRAFT/SUBMITTED/REJECTED 不计）</li>
 *   <li>期间归属：approvedAt 的 yyyyMM（无则 createdAt）；区间 [periodFrom, periodTo] 含端点</li>
 *   <li>维度 DEPT/EXPENSE_TYPE/EMPLOYEE；perCapita 仅 DEPT（分母=当期在职员工数）</li>
 *   <li>同比=去年同期(-12 月)同维度金额合计；环比=上一等长区间；缺数据/未启用均返回 null</li>
 * </ul>
 * 数据权限：t_expense_reimbursement / t_employee 非共享表，由拦截器自动注入 enterprise_id；
 * t_dept 为共享表（维度名查用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseSummaryReportServiceImpl implements ExpenseSummaryReportService {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    /** 生效报销单状态（计入汇总） */
    private static final List<String> VALID_STATUSES = List.of("APPROVED", "VOUCHERED");

    public static final String GROUP_DEPT = "DEPT";
    public static final String GROUP_EXPENSE_TYPE = "EXPENSE_TYPE";
    public static final String GROUP_EMPLOYEE = "EMPLOYEE";

    private final ExpenseReimbursementMapper expenseMapper;
    private final DeptMapper deptMapper;
    private final EmployeeMapper employeeMapper;

    @Override
    public ExpenseSummaryVO getSummary(String periodFrom, String periodTo, String groupBy,
                                       Boolean includeYoy, Boolean includeMom) {
        requirePeriodRange(periodFrom, periodTo);
        String dim = normalizeGroupBy(groupBy);
        boolean yoy = includeYoy == null || includeYoy;
        boolean mom = includeMom == null || includeMom;

        List<ExpenseReimbursementEntity> docs = queryDocs(periodFrom, periodTo);
        int span = periodSpan(periodFrom, periodTo);
        List<ExpenseReimbursementEntity> yoyDocs = yoy
                ? queryDocs(shiftPeriod(periodFrom, -12), shiftPeriod(periodTo, -12))
                : List.of();
        List<ExpenseReimbursementEntity> momDocs = mom
                ? queryDocs(shiftPeriod(periodFrom, -span), shiftPeriod(periodTo, -span))
                : List.of();

        Map<String, List<ExpenseReimbursementEntity>> mainByDim = groupByDim(docs, dim);
        Map<String, List<ExpenseReimbursementEntity>> yoyByDim = groupByDim(yoyDocs, dim);
        Map<String, List<ExpenseReimbursementEntity>> momByDim = groupByDim(momDocs, dim);

        // 在职员工数（perCapita 分母，仅 DEPT 维度需要；counting() 返回 Long）
        Map<Long, Long> activeByDept = new HashMap<>();
        if (GROUP_DEPT.equals(dim)) {
            activeByDept = employeeMapper.selectList(
                            new LambdaQueryWrapper<EmployeeEntity>().eq(EmployeeEntity::getIsActive, true))
                    .stream().filter(e -> e.getDeptId() != null)
                    .collect(Collectors.groupingBy(EmployeeEntity::getDeptId, Collectors.counting()));
        }
        // 维度名
        Map<Long, String> names = loadDimNames(mainByDim.keySet(), dim);

        List<ExpenseSummaryRowVO> rows = new ArrayList<>();
        for (Map.Entry<String, List<ExpenseReimbursementEntity>> e : mainByDim.entrySet()) {
            String key = e.getKey();
            List<ExpenseReimbursementEntity> g = e.getValue();
            int count = g.size();
            BigDecimal amount = g.stream()
                    .map(ExpenseReimbursementEntity::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Long dimId = (GROUP_EXPENSE_TYPE.equals(dim)) ? null : parseKey(key);
            String dimName = GROUP_EXPENSE_TYPE.equals(dim) ? key : names.get(dimId);

            BigDecimal perCapita = null;
            if (GROUP_DEPT.equals(dim) && dimId != null) {
                long denom = activeByDept.getOrDefault(dimId, 0L);
                perCapita = denom > 0
                        ? amount.divide(BigDecimal.valueOf(denom), 2, RoundingMode.HALF_UP)
                        : null;
            }
            BigDecimal yoyAmt = yoyByDim.containsKey(key) ? sumAmount(yoyByDim.get(key)) : null;
            BigDecimal momAmt = momByDim.containsKey(key) ? sumAmount(momByDim.get(key)) : null;

            rows.add(new ExpenseSummaryRowVO(dimId, dimName, count, amount, perCapita, yoyAmt, momAmt));
        }
        rows.sort(Comparator
                .comparing((ExpenseSummaryRowVO r) -> r.dimId() == null ? -1L : r.dimId())
                .thenComparing(r -> r.dimName() == null ? "" : r.dimName()));

        int totalCount = rows.stream().mapToInt(ExpenseSummaryRowVO::count).sum();
        BigDecimal totalAmount = rows.stream()
                .map(ExpenseSummaryRowVO::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("费用汇总: {}~{} groupBy={} 行={} 合计单数={} 金额={}",
                periodFrom, periodTo, dim, rows.size(), totalCount, totalAmount);
        return new ExpenseSummaryVO(periodFrom, periodTo, dim, rows, totalCount, totalAmount);
    }

    @Override
    public void exportSummary(String periodFrom, String periodTo, String groupBy,
                              Boolean includeYoy, Boolean includeMom, HttpServletResponse response) {
        ExpenseSummaryVO vo = getSummary(periodFrom, periodTo, groupBy, includeYoy, includeMom);
        String dimLabel = switch (vo.groupBy()) {
            case GROUP_DEPT -> "部门";
            case GROUP_EMPLOYEE -> "员工";
            default -> "费用类型";
        };
        String[] headers = {dimLabel, "维度ID", "单据数", "金额", "人均", "同比金额", "环比金额"};
        List<List<Object>> rows = new ArrayList<>();
        for (ExpenseSummaryRowVO r : vo.rows()) {
            rows.add(java.util.Arrays.asList(r.dimName(), r.dimId(), r.count(), r.amount(),
                    r.perCapita(), r.amountYoy(), r.amountMom()));
        }
        // 合计行
        rows.add(java.util.Arrays.asList("合计", "", vo.totalCount(), vo.totalAmount(),
                null, null, null));
        writeExcel(response, "费用汇总_" + vo.groupBy() + "_" + vo.periodFrom() + "-" + vo.periodTo(),
                headers, rows);
    }

    /** 写出 xlsx（照 ReportServiceImpl.writeCellValue 模式；null 单元格写空串避免 NPE） */
    private void writeExcel(HttpServletResponse response, String fileName,
                            String[] headers, List<List<Object>> rows) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment;filename=" + URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8));
            ExcelWriter writer = ExcelUtil.getWriter(true);
            for (int i = 0; i < headers.length; i++) {
                writer.writeCellValue(i, 0, headers[i]);
            }
            for (int i = 0; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                for (int j = 0; j < row.size(); j++) {
                    Object v = row.get(j);
                    writer.writeCellValue(j, i + 1, v == null ? "" : v);
                }
            }
            writer.flush(response.getOutputStream());
            response.getOutputStream().flush();
            writer.close();
        } catch (IOException e) {
            throw new BusinessException("费用汇总导出失败: " + e.getMessage());
        }
    }

    // ===== 期间算术 =====

    private static int toTotalMonth(String ym) {
        return Integer.parseInt(ym.substring(0, 4)) * 12 + (Integer.parseInt(ym.substring(4, 6)) - 1);
    }

    private static String fromTotalMonth(int total) {
        return String.format("%04d%02d", total / 12, total % 12 + 1);
    }

    private static String shiftPeriod(String ym, int deltaMonths) {
        return fromTotalMonth(toTotalMonth(ym) + deltaMonths);
    }

    private static int periodSpan(String from, String to) {
        return toTotalMonth(to) - toTotalMonth(from) + 1;
    }

    private boolean inRange(String p, String from, String to) {
        if (p == null) return false;
        int v = Integer.parseInt(p), f = Integer.parseInt(from), t = Integer.parseInt(to);
        return v >= f && v <= t;
    }

    // ===== 数据查询 =====

    /** 查询生效报销单，按有效期间（approvedAt 否则 createdAt）过滤区间。
     *  status 在 wrapper 与 Java 侧双重过滤（wrapper 在 DB 层生效，Java 侧二次校验防御 + mock 可测，对齐 P78 双防线风格） */
    private List<ExpenseReimbursementEntity> queryDocs(String from, String to) {
        List<ExpenseReimbursementEntity> all = expenseMapper.selectList(
                new LambdaQueryWrapper<ExpenseReimbursementEntity>()
                        .in(ExpenseReimbursementEntity::getStatus, VALID_STATUSES));
        return all.stream()
                .filter(d -> VALID_STATUSES.contains(d.getStatus()))
                .filter(d -> inRange(effectivePeriod(d), from, to))
                .toList();
    }

    /** 有效期间：approvedAt 的 yyyyMM，无则 createdAt，均无则 null */
    private String effectivePeriod(ExpenseReimbursementEntity d) {
        LocalDateTime ts = d.getApprovedAt() != null ? d.getApprovedAt() : d.getCreatedAt();
        return ts == null ? null : ts.format(YYYYMM);
    }

    private Map<String, List<ExpenseReimbursementEntity>> groupByDim(
            List<ExpenseReimbursementEntity> docs, String dim) {
        return docs.stream().collect(Collectors.groupingBy(d -> dimKey(d, dim)));
    }

    private String dimKey(ExpenseReimbursementEntity d, String dim) {
        return switch (dim) {
            case GROUP_DEPT -> String.valueOf(d.getDeptId());
            case GROUP_EMPLOYEE -> String.valueOf(d.getEmployeeId());
            default -> StrUtil.nullToEmpty(d.getExpenseType());
        };
    }

    private Map<Long, String> loadDimNames(Collection<String> keys, String dim) {
        Map<Long, String> names = new HashMap<>();
        if (GROUP_EXPENSE_TYPE.equals(dim)) return names;
        Set<Long> ids = keys.stream().filter(Objects::nonNull).map(this::parseKey)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) return names;
        if (GROUP_DEPT.equals(dim)) {
            deptMapper.selectBatchIds(ids).forEach(d -> names.put(d.getId(), d.getName()));
        } else {
            employeeMapper.selectBatchIds(ids).forEach(e -> names.put(e.getId(), e.getName()));
        }
        return names;
    }

    private Long parseKey(String key) {
        if (key == null || key.isEmpty()) return null;
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal sumAmount(List<ExpenseReimbursementEntity> docs) {
        return docs.stream().map(ExpenseReimbursementEntity::getAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ===== 参数守卫 =====

    private void requirePeriodRange(String from, String to) {
        if (from == null || !from.matches("\\d{6}") || to == null || !to.matches("\\d{6}")) {
            throw BusinessException.badRequest("期间区间必填且必须为6位数字(YYYYMM)");
        }
        if (toTotalMonth(from) > toTotalMonth(to)) {
            throw BusinessException.badRequest("期间区间非法: period_from 不得大于 period_to");
        }
    }

    private String normalizeGroupBy(String groupBy) {
        String g = StrUtil.blankToDefault(groupBy, GROUP_DEPT);
        if (!GROUP_DEPT.equals(g) && !GROUP_EXPENSE_TYPE.equals(g) && !GROUP_EMPLOYEE.equals(g)) {
            throw BusinessException.badRequest("group_by 非法: " + groupBy + "（允许 DEPT/EXPENSE_TYPE/EMPLOYEE）");
        }
        return g;
    }
}
