package com.huicai.base.voucher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.voucher.dto.AuxiliarySummaryRow;
import com.huicai.base.voucher.dto.LedgerEntryRowDTO;
import com.huicai.base.voucher.dto.vo.AuxiliaryLedgerRowVO;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.balance.mapper.SubjectBalanceMapper;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.service.LedgerService;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.entity.EmployeeEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.masterdata.mapper.EmployeeMapper;
import com.huicai.base.system.entity.DeptEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.base.system.service.SubjectService;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private final SubjectBalanceMapper subjectBalanceMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final SubjectService subjectService;
    private final CustomerMapper customerMapper;
    private final VendorMapper vendorMapper;
    private final DeptMapper deptMapper;
    private final EmployeeMapper employeeMapper;

    /** 核算维度类型 → assist_json 字段键映射（与 VoucherServiceImpl.validateAssistJson 一致） */
    private static final Map<String, String> DIMENSION_FIELDS = Map.of(
            "customer", "customerId",
            "vendor", "vendorId",
            "department", "deptId",
            "project", "projectId",
            "employee", "employeeId"
    );

    @Override
    public List<Map<String, Object>> subjectBalance(String period) {
        return subjectBalance(period, false, false, null);
    }

    @Override
    public List<Map<String, Object>> subjectBalance(String period, boolean includeZero, boolean includeNoMovement, String subjectCodePrefix) {
        List<SubjectBalanceEntity> balances = subjectBalanceMapper.selectList(
                new LambdaQueryWrapper<SubjectBalanceEntity>()
                        .eq(SubjectBalanceEntity::getPeriod, period)
                        .orderByAsc(SubjectBalanceEntity::getSubjectId));

        Set<Long> subjectIds = balances.stream()
                .map(SubjectBalanceEntity::getSubjectId)
                .collect(Collectors.toSet());
        Map<Long, YearTotals> yearTotals = aggregateYearTotals(subjectIds, period);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SubjectBalanceEntity b : balances) {
            Subject subject = subjectService.getById(b.getSubjectId());
            if (subject == null || !Boolean.TRUE.equals(subject.getIsLeaf())) {
                continue;
            }
            // T5 过滤：科目编码前缀
            if (subjectCodePrefix != null && !subjectCodePrefix.isBlank()
                    && !subject.getCode().startsWith(subjectCodePrefix)) {
                continue;
            }
            BigDecimal end = b.getEndBalance() == null ? BigDecimal.ZERO : b.getEndBalance();
            // T5 过滤：零余额科目
            if (!includeZero && end.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal d = b.getDebitTotal() == null ? BigDecimal.ZERO : b.getDebitTotal();
            BigDecimal c = b.getCreditTotal() == null ? BigDecimal.ZERO : b.getCreditTotal();
            // T5 过滤：无发生额科目
            if (!includeNoMovement && d.compareTo(BigDecimal.ZERO) == 0 && c.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            YearTotals yt = yearTotals.get(b.getSubjectId());
            Map<String, Object> row = new HashMap<>();
            row.put("subjectId", b.getSubjectId());
            row.put("subjectCode", subject.getCode());
            row.put("subjectName", subject.getName());
            row.put("direction", subject.getDirection());
            row.put("beginBalance", b.getBeginBalance());
            row.put("debitTotal", d);
            row.put("creditTotal", c);
            row.put("endBalance", end);
            row.put("yearBeginBalance", yt == null ? BigDecimal.ZERO : yt.beginBalance);
            row.put("yearDebitTotal", yt == null ? BigDecimal.ZERO : yt.debitTotal);
            row.put("yearCreditTotal", yt == null ? BigDecimal.ZERO : yt.creditTotal);
            rows.add(row);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> generalLedger(Long subjectId, String period) {
        Subject subject = subjectService.getById(subjectId);
        if (subject == null) {
            return new ArrayList<>();
        }
        boolean isDebit = "debit".equals(subject.getDirection());

        SubjectBalanceEntity balance = subjectBalanceMapper.selectOne(
                new LambdaQueryWrapper<SubjectBalanceEntity>()
                        .eq(SubjectBalanceEntity::getSubjectId, subjectId)
                        .eq(SubjectBalanceEntity::getPeriod, period));

        // T6: 投影查询携带 voucherNo/voucherDate；includeUnposted=true 保持现行为（T8 改为默认 false）
        List<LedgerEntryRowDTO> entries =
                voucherEntryMapper.selectSubsidiaryRows(subjectId, period, null, null, true);

        List<Map<String, Object>> rows = new ArrayList<>();

        Map<String, Object> opening = new HashMap<>();
        opening.put("type", "OPENING");
        opening.put("summary", "期初余额");
        opening.put("debit", BigDecimal.ZERO);
        opening.put("credit", BigDecimal.ZERO);
        BigDecimal running = balance == null ? BigDecimal.ZERO : balance.getBeginBalance();
        opening.put("running", running);
        rows.add(opening);

        for (LedgerEntryRowDTO e : entries) {
            BigDecimal d = e.getDebit() == null ? BigDecimal.ZERO : e.getDebit();
            BigDecimal c = e.getCredit() == null ? BigDecimal.ZERO : e.getCredit();
            if (isDebit) {
                running = running.add(d).subtract(c);
            } else {
                running = running.add(c).subtract(d);
            }
            Map<String, Object> row = new HashMap<>();
            row.put("type", "ENTRY");
            row.put("voucherId", e.getVoucherId());
            row.put("voucherNo", e.getVoucherNo());
            row.put("voucherDate", e.getVoucherDate());
            row.put("summary", e.getSummary());
            row.put("debit", d);
            row.put("credit", c);
            row.put("running", running);
            rows.add(row);
        }

        Map<String, Object> closing = new HashMap<>();
        closing.put("type", "CLOSING");
        closing.put("summary", "本期合计");
        BigDecimal totalD = BigDecimal.ZERO;
        BigDecimal totalC = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            if ("ENTRY".equals(r.get("type"))) {
                totalD = totalD.add((BigDecimal) r.get("debit"));
                totalC = totalC.add((BigDecimal) r.get("credit"));
            }
        }
        closing.put("debit", totalD);
        closing.put("credit", totalC);
        closing.put("running", running);
        rows.add(closing);

        // 本年累计行（T4）：CLOSING 之后追加 YEAR_TOTAL
        YearTotals yt = aggregateYearTotals(java.util.Set.of(subjectId), period).get(subjectId);
        BigDecimal ytBegin = yt == null ? BigDecimal.ZERO : yt.beginBalance;
        BigDecimal ytDebit = yt == null ? BigDecimal.ZERO : yt.debitTotal;
        BigDecimal ytCredit = yt == null ? BigDecimal.ZERO : yt.creditTotal;
        Map<String, Object> yearTotal = new HashMap<>();
        yearTotal.put("type", "YEAR_TOTAL");
        yearTotal.put("summary", "本年累计");
        yearTotal.put("debit", ytDebit);
        yearTotal.put("credit", ytCredit);
        yearTotal.put("running", isDebit ? ytBegin.add(ytDebit).subtract(ytCredit) : ytBegin.add(ytCredit).subtract(ytDebit));
        rows.add(yearTotal);

        return rows;
    }

    /** 本年累计聚合行（T4）：年初余额=本年度最早期间快照的 begin_balance；本年发生=period<=当前期 各期 SUM */
    private static final class YearTotals {
        BigDecimal beginBalance = BigDecimal.ZERO;
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        boolean beginSet = false;
    }

    /**
     * 聚合本年累计：period LIKE '<year>%' AND period &lt;= #{period}（YYYYMM 前缀有序）。
     * 年初余额取该科目本年度最早期间快照的 begin_balance；无快照时返回空 Map（调用方按 0 处理）。
     */
    private Map<Long, YearTotals> aggregateYearTotals(java.util.Set<Long> subjectIds, String period) {
        if (subjectIds == null || subjectIds.isEmpty() || period == null || period.length() != 6) {
            return Map.of();
        }
        String year = period.substring(0, 4);
        List<SubjectBalanceEntity> yearBalances = subjectBalanceMapper.selectList(
                new LambdaQueryWrapper<SubjectBalanceEntity>()
                        .in(SubjectBalanceEntity::getSubjectId, subjectIds)
                        .likeRight(SubjectBalanceEntity::getPeriod, year)
                        .le(SubjectBalanceEntity::getPeriod, period)
                        .orderByAsc(SubjectBalanceEntity::getPeriod));

        Map<Long, YearTotals> result = new HashMap<>();
        for (SubjectBalanceEntity b : yearBalances) {
            YearTotals yt = result.computeIfAbsent(b.getSubjectId(), k -> new YearTotals());
            yt.debitTotal = yt.debitTotal.add(b.getDebitTotal() == null ? BigDecimal.ZERO : b.getDebitTotal());
            yt.creditTotal = yt.creditTotal.add(b.getCreditTotal() == null ? BigDecimal.ZERO : b.getCreditTotal());
            if (!yt.beginSet) {
                yt.beginBalance = b.getBeginBalance() == null ? BigDecimal.ZERO : b.getBeginBalance();
                yt.beginSet = true;
            }
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> subsidiaryLedger(Long subjectId, String period,
                                                      LocalDate startDate, LocalDate endDate) {
        return subsidiaryLedger(subjectId, period, startDate, endDate, false);
    }

    @Override
    public List<Map<String, Object>> subsidiaryLedger(Long subjectId, String period,
                                                      LocalDate startDate, LocalDate endDate,
                                                      boolean includeUnposted) {
        Subject subject = subjectService.getById(subjectId);
        if (subject == null) {
            return new ArrayList<>();
        }

        SubjectBalanceEntity balance = subjectBalanceMapper.selectOne(
                new LambdaQueryWrapper<SubjectBalanceEntity>()
                        .eq(SubjectBalanceEntity::getSubjectId, subjectId)
                        .eq(SubjectBalanceEntity::getPeriod, period));

        List<LedgerEntryRowDTO> entries =
                voucherEntryMapper.selectSubsidiaryRows(subjectId, period, startDate, endDate, includeUnposted);

        boolean isDebit = "debit".equals(subject.getDirection());
        BigDecimal running = balance == null ? BigDecimal.ZERO : balance.getBeginBalance();

        List<Map<String, Object>> rows = new ArrayList<>();

        Map<String, Object> opening = new HashMap<>();
        opening.put("type", "OPENING");
        opening.put("summary", "期初余额");
        opening.put("debit", BigDecimal.ZERO);
        opening.put("credit", BigDecimal.ZERO);
        opening.put("running", running);
        rows.add(opening);

        for (LedgerEntryRowDTO e : entries) {
            BigDecimal d = e.getDebit() == null ? BigDecimal.ZERO : e.getDebit();
            BigDecimal c = e.getCredit() == null ? BigDecimal.ZERO : e.getCredit();
            if (isDebit) {
                running = running.add(d).subtract(c);
            } else {
                running = running.add(c).subtract(d);
            }
            Map<String, Object> row = new HashMap<>();
            row.put("type", "ENTRY");
            row.put("voucherId", e.getVoucherId());
            row.put("voucherNo", e.getVoucherNo());
            row.put("voucherDate", e.getVoucherDate());
            row.put("subjectId", subjectId);
            row.put("subjectCode", subject.getCode());
            row.put("subjectName", subject.getName());
            row.put("summary", e.getSummary());
            row.put("debit", d);
            row.put("credit", c);
            row.put("running", running);
            rows.add(row);
        }
        return rows;
    }

    @Override
    public List<AuxiliaryLedgerRowVO> auxiliaryLedger(String dimensionType, String period, Long dimensionValue) {
        String dimensionField = DIMENSION_FIELDS.get(dimensionType);
        if (dimensionField == null) {
            throw BusinessException.badRequest("不支持的辅助核算维度类型: " + dimensionType);
        }
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }

        List<AuxiliarySummaryRow> movement = voucherEntryMapper.selectAuxiliaryMovement(dimensionField, dimensionValue, period);
        List<AuxiliarySummaryRow> opening = voucherEntryMapper.selectAuxiliaryOpening(dimensionField, dimensionValue, period);

        // 期初聚合：按 subjectId + dimensionValue 建立索引，用于推算期初余额
        Map<String, AuxiliarySummaryRow> openingIndex = opening.stream()
                .collect(Collectors.toMap(
                        r -> rowKey(r.getSubjectId(), r.getDimensionValue()),
                        r -> r,
                        (a, b) -> a));

        Set<Long> subjectIds = movement.stream()
                .map(AuxiliarySummaryRow::getSubjectId)
                .collect(Collectors.toSet());
        Map<Long, Subject> subjectMap = subjectIds.isEmpty() ? Map.of()
                : subjectService.listByIds(subjectIds).stream()
                        .collect(Collectors.toMap(Subject::getId, s -> s));

        Map<String, String> dimensionNameMap = resolveDimensionNames(dimensionType, movement, opening);

        List<AuxiliaryLedgerRowVO> rows = new ArrayList<>();
        for (AuxiliarySummaryRow m : movement) {
            Subject subject = subjectMap.get(m.getSubjectId());
            if (subject == null || !Boolean.TRUE.equals(subject.getIsLeaf())) {
                continue;
            }
            AuxiliarySummaryRow op = openingIndex.get(rowKey(m.getSubjectId(), m.getDimensionValue()));
            boolean isDebit = "debit".equals(subject.getDirection());

            BigDecimal begin = BigDecimal.ZERO;
            if (op != null) {
                BigDecimal opDebit = op.getDebitTotal() == null ? BigDecimal.ZERO : op.getDebitTotal();
                BigDecimal opCredit = op.getCreditTotal() == null ? BigDecimal.ZERO : op.getCreditTotal();
                begin = isDebit ? opDebit.subtract(opCredit) : opCredit.subtract(opDebit);
            }

            BigDecimal d = m.getDebitTotal() == null ? BigDecimal.ZERO : m.getDebitTotal();
            BigDecimal c = m.getCreditTotal() == null ? BigDecimal.ZERO : m.getCreditTotal();
            BigDecimal end = isDebit ? begin.add(d).subtract(c) : begin.add(c).subtract(d);

            AuxiliaryLedgerRowVO vo = new AuxiliaryLedgerRowVO();
            vo.setDimensionType(dimensionType);
            vo.setDimensionValue(parseDimValue(m.getDimensionValue()));
            vo.setDimensionName(dimensionNameMap.get(m.getDimensionValue()));
            vo.setSubjectId(subject.getId());
            vo.setSubjectCode(subject.getCode());
            vo.setSubjectName(subject.getName());
            vo.setDirection(subject.getDirection());
            vo.setBeginBalance(begin);
            vo.setDebitTotal(d);
            vo.setCreditTotal(c);
            vo.setEndBalance(end);
            rows.add(vo);
        }
        return rows;
    }

    private String rowKey(Long subjectId, String dimensionValue) {
        return subjectId + "#" + dimensionValue;
    }

    private Long parseDimValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, String> resolveDimensionNames(String dimensionType, List<AuxiliarySummaryRow> movement, List<AuxiliarySummaryRow> opening) {
        Set<String> dimValues = new HashSet<>();
        for (AuxiliarySummaryRow r : movement) {
            if (r.getDimensionValue() != null) {
                dimValues.add(r.getDimensionValue());
            }
        }
        for (AuxiliarySummaryRow r : opening) {
            if (r.getDimensionValue() != null) {
                dimValues.add(r.getDimensionValue());
            }
        }
        if (dimValues.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = dimValues.stream()
                .map(this::parseDimValue)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        switch (dimensionType) {
            case "customer" -> {
                for (CustomerEntity e : customerMapper.selectBatchIds(ids)) {
                    result.put(String.valueOf(e.getId()), e.getName());
                }
            }
            case "vendor" -> {
                for (VendorEntity e : vendorMapper.selectBatchIds(ids)) {
                    result.put(String.valueOf(e.getId()), e.getName());
                }
            }
            case "department" -> {
                for (DeptEntity e : deptMapper.selectBatchIds(ids)) {
                    result.put(String.valueOf(e.getId()), e.getName());
                }
            }
            case "employee" -> {
                for (EmployeeEntity e : employeeMapper.selectBatchIds(ids)) {
                    result.put(String.valueOf(e.getId()), e.getName());
                }
            }
            default -> {
                // project 维度当前无 Project 实体，名称置空
            }
        }
        return result;
    }
}
