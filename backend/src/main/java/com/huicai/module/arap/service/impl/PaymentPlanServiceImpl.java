package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.module.arap.entity.VendorEntity;
import com.huicai.module.arap.mapper.VendorMapper;
import com.huicai.module.arap.service.PaymentPlanService;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPlanServiceImpl implements PaymentPlanService {

    private final BusinessDocMapper businessDocMapper;
    private final VendorMapper vendorMapper;

    @Override
    public List<PaymentPlanGroupVO> generatePaymentPlan(String period, Long vendorId) {
        // 1. 查询所有未清应付单
        var wrapper = new LambdaQueryWrapper<BusinessDocEntity>()
            .in(BusinessDocEntity::getDocType, List.of("INVOICE_IN", "OTHER_PAYABLE"))
            .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
            .eq(BusinessDocEntity::getDeleted, 0);
        if (period != null && !period.isBlank()) {
            wrapper.eq(BusinessDocEntity::getPeriod, period);
        }
        if (vendorId != null) {
            wrapper.eq(BusinessDocEntity::getSupplierId, vendorId);
        }
        wrapper.orderByAsc(BusinessDocEntity::getDueDate);

        List<BusinessDocEntity> docs = businessDocMapper.selectList(wrapper);
        if (docs.isEmpty()) return List.of();

        // 2. 按供应商分组
        Map<Long, List<BusinessDocEntity>> byVendor = docs.stream()
            .filter(d -> d.getSupplierId() != null)
            .collect(Collectors.groupingBy(BusinessDocEntity::getSupplierId));

        // 3. 缓存供应商名称
        Map<Long, String> vendorNameCache = new HashMap<>();

        List<PaymentPlanGroupVO> result = new ArrayList<>();
        LocalDate now = LocalDate.now();

        for (var entry : byVendor.entrySet()) {
            Long vId = entry.getKey();
            List<BusinessDocEntity> items = entry.getValue();

            String vName = vendorNameCache.computeIfAbsent(vId, id -> {
                VendorEntity v = vendorMapper.selectById(id);
                return v != null ? v.getName() : null;
            });

            List<PaymentPlanItemVO> planItems = items.stream()
                .map(doc -> {
                    int overdueDays = calcOverdueDays(now, doc.getDueDate());
                    LocalDate suggestedPayDate = calcSuggestedPayDate(doc.getDueDate());
                    return new PaymentPlanItemVO(
                        doc.getDocNo(), doc.getDocType(),
                        doc.getDueDate(), doc.getUnsettledAmount(),
                        overdueDays, suggestedPayDate,
                        calcPriority(overdueDays, doc.getDueDate(), now)
                    );
                })
                .toList();

            BigDecimal totalDue = items.stream()
                .map(BusinessDocEntity::getUnsettledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.add(new PaymentPlanGroupVO(vId, vName, totalDue, planItems.size(), planItems));
        }

        // 4. 按最紧急的项排序
        result.sort((a, b) -> {
            String pa = a.items().stream()
                .map(PaymentPlanItemVO::priority)
                .min(Comparator.comparingInt(PaymentPlanServiceImpl::priorityOrder))
                .orElse("LOW");
            String pb = b.items().stream()
                .map(PaymentPlanItemVO::priority)
                .min(Comparator.comparingInt(PaymentPlanServiceImpl::priorityOrder))
                .orElse("LOW");
            return Integer.compare(priorityOrder(pa), priorityOrder(pb));
        });

        return result;
    }

    private static int calcOverdueDays(LocalDate now, LocalDate dueDate) {
        if (dueDate == null) return 0;
        long days = now.toEpochDay() - dueDate.toEpochDay();
        return (int) Math.max(0, days);
    }

    private static LocalDate calcSuggestedPayDate(LocalDate dueDate) {
        if (dueDate == null) return LocalDate.now();
        return dueDate.minusDays(3); // 到期日前 3 个工作日（简化）
    }

    private static String calcPriority(int overdueDays, LocalDate dueDate, LocalDate now) {
        if (overdueDays >= 90) return "CRITICAL";
        if (overdueDays >= 31) return "HIGH";
        if (overdueDays >= 1) return "MEDIUM";
        if (dueDate != null && dueDate.toEpochDay() - now.toEpochDay() <= 7) return "NORMAL";
        return "LOW";
    }

    private static int priorityOrder(String priority) {
        return switch (priority) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            case "NORMAL" -> 3;
            default -> 4;
        };
    }
}