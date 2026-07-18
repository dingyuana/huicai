package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.module.arap.entity.AgingAlertEntity;
import com.huicai.module.arap.entity.CustomerEntity;
import com.huicai.module.arap.entity.PrepaymentEntity;
import com.huicai.module.arap.entity.VendorEntity;
import com.huicai.module.arap.mapper.AgingAlertMapper;
import com.huicai.module.arap.mapper.CustomerMapper;
import com.huicai.module.arap.mapper.PrepaymentMapper;
import com.huicai.module.arap.mapper.VendorMapper;
import com.huicai.module.arap.service.AgingAnalysisService;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.arap.mapper.PrepaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgingAnalysisServiceImpl implements AgingAnalysisService {

    private final BusinessDocMapper businessDocMapper;
    private final PrepaymentMapper prepaymentMapper;
    private final AgingAlertMapper alertMapper;
    private final CustomerMapper customerMapper;
    private final VendorMapper vendorMapper;
    // ========== 账龄引擎 ==========

    private AgingResult calcAging(LocalDate refDate, LocalDate dueDate) {
        if (dueDate == null) return new AgingResult("current", "信用期内", 0);
        long days = refDate.toEpochDay() - dueDate.toEpochDay();
        if (days <= 0) return new AgingResult("current", "信用期内", 0);
        if (days <= 30) return new AgingResult("days_1_30", "1-30天", (int) days);
        if (days <= 60) return new AgingResult("days_31_60", "31-60天", (int) days);
        if (days <= 90) return new AgingResult("days_61_90", "61-90天", (int) days);
        if (days <= 180) return new AgingResult("days_91_180", "91-180天", (int) days);
        if (days <= 365) return new AgingResult("days_181_365", "181-365天", (int) days);
        return new AgingResult("over_365", "365天以上", (int) days);
    }

    private String bucketLabel(String bucket) {
        return switch (bucket) {
            case "current" -> "信用期内";
            case "days_1_30" -> "1-30天";
            case "days_31_60" -> "31-60天";
            case "days_61_90" -> "61-90天";
            case "days_91_180" -> "91-180天";
            case "days_181_365" -> "181-365天";
            case "over_365" -> "365天以上";
            default -> bucket;
        };
    }

    private record AgingResult(String bucket, String label, int days) {}

    private record AgingSourceRow(
        String sourceType, Long sourceId, String sourceNo,
        Long partyId, String partyName,
        LocalDate dueDate, BigDecimal originalAmount, BigDecimal unsettledAmount
    ) {}

    /**
     * 加载所有未清应收数据（4 数据源）
     */
    private List<AgingSourceRow> loadAllUnsettled(String period) {
        List<AgingSourceRow> rows = new ArrayList<>();

        // 1. BusinessDoc: INVOICE_OUT / OTHER_RECEIVABLE / NOTE_RECEIVABLE
        businessDocMapper.selectList(
            new LambdaQueryWrapper<BusinessDocEntity>()
                .in(BusinessDocEntity::getDocType, List.of("INVOICE_OUT", "OTHER_RECEIVABLE", "NOTE_RECEIVABLE"))
                .eq(BusinessDocEntity::getPeriod, period)
                .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
                .eq(BusinessDocEntity::getDeleted, 0)
        ).forEach(doc -> {
            String name = lookupCustomerName(doc.getCustomerId());
            rows.add(new AgingSourceRow(
                doc.getDocType(), doc.getId(), doc.getDocNo(),
                doc.getCustomerId(), name,
                doc.getDueDate(), doc.getAmount(), doc.getUnsettledAmount()
            ));
        });

        // 2. PREPAYMENT
        prepaymentMapper.selectList(
            new LambdaQueryWrapper<PrepaymentEntity>()
                .eq(PrepaymentEntity::getPeriod, period)
                .gt(PrepaymentEntity::getUnsettledAmount, BigDecimal.ZERO)
                .eq(PrepaymentEntity::getDeleted, 0)
        ).forEach(pre -> {
            Long partyId = pre.getCustomerId() != null ? pre.getCustomerId() : pre.getVendorId();
            String name = pre.getCustomerId() != null ? lookupCustomerName(pre.getCustomerId()) : null;
            rows.add(new AgingSourceRow(
                "PREPAYMENT", pre.getId(), null,
                partyId, name,
                null, pre.getAmount(), pre.getUnsettledAmount()
            ));
        });

        return rows;
    }

    /**
     * 根据期间加载未清数据（用于到期债权表，按 date 推算期间）
     */
    private List<AgingSourceRow> loadAllUnsettledByDate(LocalDate date) {
        String period = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
        return loadAllUnsettled(period);
    }

    private final Map<Long, String> customerNameCache = new HashMap<>();
    private final Map<Long, String> vendorNameCache = new HashMap<>();

    private String lookupCustomerName(Long customerId) {
        if (customerId == null) return null;
        return customerNameCache.computeIfAbsent(customerId, id -> {
            CustomerEntity c = customerMapper.selectById(id);
            return c != null ? c.getName() : null;
        });
    }

    private String lookupVendorName(Long vendorId) {
        if (vendorId == null) return null;
        return vendorNameCache.computeIfAbsent(vendorId, id -> {
            VendorEntity v = vendorMapper.selectById(id);
            return v != null ? v.getName() : null;
        });
    }

    // ========== API 实现 ==========

    @Override
    public AgingSummaryVO getAgingSummary(String period, Long customerId) {
        LocalDate now = LocalDate.now();
        List<AgingSourceRow> all = loadAllUnsettled(period);
        if (customerId != null) {
            all = all.stream().filter(r -> customerId.equals(r.partyId())).toList();
        }

        List<String> bucketOrder = List.of("current", "days_1_30", "days_31_60", "days_61_90",
            "days_91_180", "days_181_365", "over_365");
        Map<String, List<AgingSourceRow>> grouped = new LinkedHashMap<>();
        bucketOrder.forEach(b -> grouped.put(b, new ArrayList<>()));

        BigDecimal totalUnsettled = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;

        for (AgingSourceRow row : all) {
            AgingResult ar = calcAging(now, row.dueDate());
            grouped.get(ar.bucket()).add(row);
            totalUnsettled = totalUnsettled.add(row.unsettledAmount());
            if (!"current".equals(ar.bucket())) {
                totalOverdue = totalOverdue.add(row.unsettledAmount());
            }
        }

        List<AgingBucket> buckets = new ArrayList<>();
        for (String bucket : bucketOrder) {
            List<AgingSourceRow> rows = grouped.get(bucket);
            if (rows.isEmpty() && !"current".equals(bucket)) continue;
            BigDecimal amt = rows.stream()
                .map(AgingSourceRow::unsettledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            String pct = totalUnsettled.compareTo(BigDecimal.ZERO) > 0
                ? amt.multiply(BigDecimal.valueOf(100)).divide(totalUnsettled, 2, RoundingMode.HALF_UP) + "%"
                : "0%";
            buckets.add(new AgingBucket(bucketLabel(bucket), amt, rows.size(), pct));
        }

        String overdueRate = totalUnsettled.compareTo(BigDecimal.ZERO) > 0
            ? totalOverdue.multiply(BigDecimal.valueOf(100)).divide(totalUnsettled, 2, RoundingMode.HALF_UP) + "%"
            : "0%";

        return new AgingSummaryVO(now, period,
            new AgingSummary(totalUnsettled, totalOverdue, overdueRate), buckets);
    }

    @Override
    public List<AgingByCustomerVO> getAgingByCustomer(String period) {
        LocalDate now = LocalDate.now();
        List<AgingSourceRow> all = loadAllUnsettled(period);

        Map<Long, List<AgingSourceRow>> byCustomer = all.stream()
            .filter(r -> r.partyId() != null)
            .collect(Collectors.groupingBy(AgingSourceRow::partyId));

        List<AgingByCustomerVO> result = new ArrayList<>();
        for (var entry : byCustomer.entrySet()) {
            List<AgingSourceRow> rows = entry.getValue();
            Map<String, BigDecimal> buckets = new LinkedHashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            for (AgingSourceRow row : rows) {
                AgingResult ar = calcAging(now, row.dueDate());
                buckets.merge(ar.bucket(), row.unsettledAmount(), BigDecimal::add);
                total = total.add(row.unsettledAmount());
            }
            String name = rows.stream().map(AgingSourceRow::partyName)
                .filter(Objects::nonNull).findFirst().orElse("");
            result.add(new AgingByCustomerVO(entry.getKey(), name, total, buckets));
        }
        result.sort((a, b) -> b.totalUnsettled().compareTo(a.totalUnsettled()));
        return result;
    }

    @Override
    public DueReceivablesVO getDueReceivables(LocalDate reportDate, Long customerId) {
        List<AgingSourceRow> all = loadAllUnsettledByDate(reportDate);
        if (customerId != null) {
            all = all.stream().filter(r -> customerId.equals(r.partyId())).toList();
        }

        List<DueItem> items = all.stream()
            .filter(r -> r.dueDate() != null && r.dueDate().isBefore(reportDate))
            .map(r -> {
                AgingResult ar = calcAging(reportDate, r.dueDate());
                return new DueItem(
                    r.partyName() != null ? r.partyName() : "",
                    r.sourceNo(), null, r.dueDate(),
                    r.originalAmount(), r.unsettledAmount(),
                    ar.days(), ar.bucket(), null, null
                );
            })
            .sorted((a, b) -> Integer.compare(b.overdueDays(), a.overdueDays()))
            .toList();

        BigDecimal totalDue = items.stream()
            .map(DueItem::unsettledAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DueReceivablesVO(reportDate, totalDue, items.size(), items);
    }

    // ========== 逾期预警 ==========

    @Override
    @Transactional
    public int generateAlerts(String period) {
        LocalDate now = LocalDate.now();
        List<AgingSourceRow> all = loadAllUnsettled(period);

        int count = 0;
        for (AgingSourceRow row : all) {
            if (row.dueDate() == null || !row.dueDate().isBefore(now)) continue;
            AgingResult ar = calcAging(now, row.dueDate());
            if ("current".equals(ar.bucket())) continue;

            Long existing = alertMapper.selectCount(
                new LambdaQueryWrapper<AgingAlertEntity>()
                    .eq(AgingAlertEntity::getDocId, row.sourceId())
                    .eq(AgingAlertEntity::getStatus, "ACTIVE")
            );
            if (existing > 0) continue;

            String level = alertLevel(ar.days());
            AgingAlertEntity alert = new AgingAlertEntity();
            alert.setCustomerId(row.partyId());
            alert.setDocId(row.sourceId());
            alert.setDocNo(row.sourceNo());
            alert.setUnsettledAmount(row.unsettledAmount());
            alert.setDueDate(row.dueDate());
            alert.setOverdueDays(ar.days());
            alert.setAlertLevel(level);
            alert.setStatus("ACTIVE");
            alertMapper.insert(alert);
            count++;
        }

        log.info("逾期预警扫描完成: period={}, 新增预警={}", period, count);
        return count;
    }

    private String alertLevel(int overdueDays) {
        if (overdueDays <= 30) return "MILD";
        if (overdueDays <= 60) return "MODERATE";
        if (overdueDays <= 90) return "SEVERE";
        return "CRITICAL";
    }

    @Override
    public List<AgingAlertVO> getAlerts(String alertLevel, String status, Long customerId) {
        var wrapper = new LambdaQueryWrapper<AgingAlertEntity>();
        if (alertLevel != null && !alertLevel.isBlank()) {
            wrapper.eq(AgingAlertEntity::getAlertLevel, alertLevel);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(AgingAlertEntity::getStatus, status);
        } else {
            wrapper.eq(AgingAlertEntity::getStatus, "ACTIVE");
        }
        if (customerId != null) {
            wrapper.eq(AgingAlertEntity::getCustomerId, customerId);
        }
        wrapper.orderByDesc(AgingAlertEntity::getCreatedAt);

        return alertMapper.selectList(wrapper).stream()
            .map(a -> new AgingAlertVO(
                a.getId(), a.getCustomerId(),
                lookupCustomerName(a.getCustomerId()),
                a.getDocNo(), a.getUnsettledAmount(), a.getDueDate(),
                a.getOverdueDays(), a.getAlertLevel(), a.getStatus(),
                a.getNotifiedAt(), a.getDismissedAt(), a.getCreatedAt()
            ))
            .toList();
    }

    @Override
    @Transactional
    public void dismissAlert(Long id) {
        AgingAlertEntity alert = alertMapper.selectById(id);
        if (alert != null) {
            alert.setStatus("DISMISSED");
            alert.setDismissedAt(LocalDateTime.now());
            alertMapper.updateById(alert);
        }
    }

    @Override
    @Transactional
    public void resolveAlert(Long id) {
        AgingAlertEntity alert = alertMapper.selectById(id);
        if (alert != null) {
            alert.setStatus("RESOLVED");
            alert.setDismissedAt(LocalDateTime.now());
            alertMapper.updateById(alert);
        }
    }

    // ========== 应付账龄分析 API（P53） ==========

    private List<AgingSourceRow> loadAllPayableUnsettled(String period) {
        List<AgingSourceRow> rows = new ArrayList<>();
        businessDocMapper.selectList(
            new LambdaQueryWrapper<BusinessDocEntity>()
                .in(BusinessDocEntity::getDocType, List.of("INVOICE_IN", "PAYMENT", "OTHER_PAYABLE"))
                .eq(BusinessDocEntity::getPeriod, period)
                .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
                .eq(BusinessDocEntity::getDeleted, 0)
        ).forEach(doc -> {
            String name = lookupVendorName(doc.getSupplierId());
            rows.add(new AgingSourceRow(
                doc.getDocType(), doc.getId(), doc.getDocNo(),
                doc.getSupplierId(), name,
                doc.getDueDate(), doc.getAmount(), doc.getUnsettledAmount()
            ));
        });
        return rows;
    }

    @Override
    public AgingSummaryVO getPayableAgingSummary(String period, Long vendorId) {
        LocalDate now = LocalDate.now();
        List<AgingSourceRow> all = loadAllPayableUnsettled(period);
        if (vendorId != null) {
            all = all.stream().filter(r -> vendorId.equals(r.partyId())).toList();
        }
        return computeAgingSummary(now, period, all);
    }

    @Override
    public List<AgingByVendorVO> getPayableAgingByVendor(String period) {
        LocalDate now = LocalDate.now();
        List<AgingSourceRow> all = loadAllPayableUnsettled(period);
        Map<Long, List<AgingSourceRow>> byVendor = all.stream()
            .filter(r -> r.partyId() != null)
            .collect(Collectors.groupingBy(AgingSourceRow::partyId));
        List<AgingByVendorVO> result = new ArrayList<>();
        for (var entry : byVendor.entrySet()) {
            List<AgingSourceRow> rows = entry.getValue();
            Map<String, BigDecimal> buckets = new LinkedHashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            for (AgingSourceRow row : rows) {
                AgingResult ar = calcAging(now, row.dueDate());
                buckets.merge(ar.bucket(), row.unsettledAmount(), BigDecimal::add);
                total = total.add(row.unsettledAmount());
            }
            String name = rows.stream().map(AgingSourceRow::partyName)
                .filter(Objects::nonNull).findFirst().orElse("");
            result.add(new AgingByVendorVO(entry.getKey(), name, total, buckets));
        }
        result.sort((a, b) -> b.totalUnsettled().compareTo(a.totalUnsettled()));
        return result;
    }

    @Override
    public DuePayablesVO getDuePayables(LocalDate reportDate, Long vendorId) {
        List<AgingSourceRow> all = loadAllPayableUnsettled(reportDate.format(DateTimeFormatter.ofPattern("yyyyMM")));
        if (vendorId != null) {
            all = all.stream().filter(r -> vendorId.equals(r.partyId())).toList();
        }
        List<DuePayableItem> items = all.stream()
            .filter(r -> r.dueDate() != null && r.dueDate().isBefore(reportDate))
            .map(r -> {
                AgingResult ar = calcAging(reportDate, r.dueDate());
                return new DuePayableItem(
                    r.partyName() != null ? r.partyName() : "",
                    r.sourceNo(), r.dueDate(),
                    r.originalAmount(), r.unsettledAmount(),
                    ar.days(), ar.bucket()
                );
            })
            .sorted((a, b) -> Integer.compare(b.overdueDays(), a.overdueDays()))
            .toList();
        BigDecimal totalDue = items.stream()
            .map(DuePayableItem::unsettledAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DuePayablesVO(reportDate, totalDue, items.size(), items);
    }

    private AgingSummaryVO computeAgingSummary(LocalDate now, String period, List<AgingSourceRow> all) {
        List<String> bucketOrder = List.of("current", "days_1_30", "days_31_60", "days_61_90",
            "days_91_180", "days_181_365", "over_365");
        Map<String, List<AgingSourceRow>> grouped = new LinkedHashMap<>();
        bucketOrder.forEach(b -> grouped.put(b, new ArrayList<>()));
        BigDecimal totalUnsettled = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;
        for (AgingSourceRow row : all) {
            AgingResult ar = calcAging(now, row.dueDate());
            grouped.get(ar.bucket()).add(row);
            totalUnsettled = totalUnsettled.add(row.unsettledAmount());
            if (!"current".equals(ar.bucket())) {
                totalOverdue = totalOverdue.add(row.unsettledAmount());
            }
        }
        List<AgingBucket> buckets = new ArrayList<>();
        for (String bucket : bucketOrder) {
            List<AgingSourceRow> rows = grouped.get(bucket);
            if (rows.isEmpty() && !"current".equals(bucket)) continue;
            BigDecimal amt = rows.stream()
                .map(AgingSourceRow::unsettledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            String pct = totalUnsettled.compareTo(BigDecimal.ZERO) > 0
                ? amt.multiply(BigDecimal.valueOf(100)).divide(totalUnsettled, 2, RoundingMode.HALF_UP) + "%"
                : "0%";
            buckets.add(new AgingBucket(bucketLabel(bucket), amt, rows.size(), pct));
        }
        String overdueRate = totalUnsettled.compareTo(BigDecimal.ZERO) > 0
            ? totalOverdue.multiply(BigDecimal.valueOf(100)).divide(totalUnsettled, 2, RoundingMode.HALF_UP) + "%"
            : "0%";
        return new AgingSummaryVO(now, period,
            new AgingSummary(totalUnsettled, totalOverdue, overdueRate), buckets);
    }
}
