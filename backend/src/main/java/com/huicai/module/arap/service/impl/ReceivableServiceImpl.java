package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.dto.ReceivableVO;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.CustomerMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.arap.service.ReceivableService;
import com.huicai.module.system.entity.UserEntity;
import com.huicai.module.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceivableServiceImpl implements ReceivableService {

    private final ReceivableMapper mapper;
    private final CustomerMapper customerMapper;
    private final UserMapper userMapper;

    private static final List<String> AGING_BUCKETS = List.of(
            "current", "days_0_30", "days_31_60", "days_61_90",
            "days_91_180", "days_181_365", "over_365"
    );

    @Override
    public IPage<ReceivableVO> pageQuery(Long customerId, String period, Integer current, Integer size) {
        Page<ReceivableEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<ReceivableEntity> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) {
            wrapper.eq(ReceivableEntity::getCustomerId, customerId);
        }
        if (period != null && !period.isBlank()) {
            wrapper.eq(ReceivableEntity::getPeriod, period);
        }
        wrapper.gt(ReceivableEntity::getUnsettledAmount, BigDecimal.ZERO);
        wrapper.orderByDesc(ReceivableEntity::getTxDate);
        IPage<ReceivableEntity> entityPage = mapper.selectPage(page, wrapper);

        IPage<ReceivableVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        List<ReceivableVO> vos = entityPage.getRecords().stream()
                .map(ReceivableVO::fromEntity).collect(Collectors.toList());
        populatePartyNames(vos);
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public ReceivableVO getById(Long id) {
        ReceivableEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("应收明细不存在");
        }
        ReceivableVO vo = ReceivableVO.fromEntity(entity);
        populatePartyNames(List.of(vo));
        return vo;
    }

    @Override
    public ReceivableEntity create(ReceivableEntity entity) {
        if (entity.getSettledAmount() == null) entity.setSettledAmount(BigDecimal.ZERO);
        entity.setUnsettledAmount(entity.getAmount().subtract(entity.getSettledAmount()));
        mapper.insert(entity);
        return entity;
    }

    @Override
    public List<Map<String, Object>> overdueList() {
        return mapper.overdueList();
    }

    @Override
    public Map<String, Object> agingAnalysis(Long customerId) {
        List<Map<String, Object>> rows = mapper.agingByCustomer(customerId);
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String bucket : AGING_BUCKETS) {
            amounts.put(bucket, BigDecimal.ZERO);
            counts.put(bucket, 0);
        }
        for (Map<String, Object> row : rows) {
            String bucket = (String) row.get("aging_bucket");
            Object amountObj = row.get("amount");
            Object countObj = row.get("count");
            amounts.put(bucket, amountObj == null ? BigDecimal.ZERO :
                    new BigDecimal(amountObj.toString()));
            counts.put(bucket, countObj == null ? 0 : ((Number) countObj).intValue());
        }
        result.put("buckets", amounts.keySet());
        result.put("amounts", amounts.values());
        result.put("counts", counts.values());
        BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("total", total);
        return result;
    }

    @Override
    public Map<String, Object> overallAging() {
        Map<String, Object> result = new HashMap<>();
        List<ReceivableEntity> all = mapper.selectList(new LambdaQueryWrapper<ReceivableEntity>()
                .gt(ReceivableEntity::getUnsettledAmount, BigDecimal.ZERO));
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalCount = 0;
        for (ReceivableEntity r : all) {
            totalAmount = totalAmount.add(r.getUnsettledAmount());
            totalCount++;
        }
        result.put("totalAmount", totalAmount);
        result.put("totalCount", totalCount);
        return result;
    }

    private void populatePartyNames(List<ReceivableVO> vos) {
        if (vos.isEmpty()) return;
        List<Long> customerIds = vos.stream()
                .map(ReceivableVO::getCustomerId).filter(java.util.Objects::nonNull).distinct().toList();
        if (customerIds.isEmpty()) return;
        Map<Long, String> nameMap = customerMapper.selectBatchIds(customerIds).stream()
                .collect(Collectors.toMap(com.huicai.module.arap.entity.CustomerEntity::getId, com.huicai.module.arap.entity.CustomerEntity::getName));
        for (ReceivableVO vo : vos) {
            if (vo.getCustomerId() != null) vo.setCustomerName(nameMap.get(vo.getCustomerId()));
        }
    }
}
