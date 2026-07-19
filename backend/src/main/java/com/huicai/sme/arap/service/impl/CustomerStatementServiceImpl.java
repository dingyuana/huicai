package com.huicai.sme.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.constant.ArapStatus;
import com.huicai.sme.arap.entity.CustomerStatementEntity;
import com.huicai.sme.arap.entity.DisputeEntity;
import com.huicai.sme.arap.entity.OutstandingItemEntity;
import com.huicai.sme.arap.mapper.CustomerStatementMapper;
import com.huicai.sme.arap.mapper.DisputeMapper;
import com.huicai.sme.arap.mapper.OutstandingItemMapper;
import com.huicai.sme.arap.service.CustomerStatementService;
import com.huicai.sme.arap.entity.BusinessDocEntity;
import com.huicai.sme.arap.mapper.BusinessDocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerStatementServiceImpl implements CustomerStatementService {

    private final CustomerStatementMapper statementMapper;
    private final OutstandingItemMapper outstandingItemMapper;
    private final DisputeMapper disputeMapper;
    private final BusinessDocMapper businessDocMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CustomerStatementEntity> generateStatements(List<Long> customerIds, String period) {
        if (customerIds == null || customerIds.isEmpty()) {
            throw new BusinessException("客户ID列表不能为空");
        }
        if (period == null || period.isBlank()) {
            throw new BusinessException("会计期间不能为空");
        }

        List<CustomerStatementEntity> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Long customerId : customerIds) {
            // 查询业务单据汇总（INVOICE_OUT / OTHER_RECEIVABLE / NOTE_RECEIVABLE）
            List<BusinessDocEntity> docs = businessDocMapper.selectList(
                    new LambdaQueryWrapper<BusinessDocEntity>()
                            .eq(BusinessDocEntity::getCustomerId, customerId)
                            .in(BusinessDocEntity::getDocType, "INVOICE_OUT", "OTHER_RECEIVABLE", "NOTE_RECEIVABLE")
                            .eq(BusinessDocEntity::getPeriod, period)
            );

            BigDecimal totalOriginal = BigDecimal.ZERO;
            BigDecimal totalSettled = BigDecimal.ZERO;
            BigDecimal totalUnsettled = BigDecimal.ZERO;

            for (BusinessDocEntity doc : docs) {
                totalOriginal = totalOriginal.add(doc.getAmount() != null ? doc.getAmount() : BigDecimal.ZERO);
                totalSettled = totalSettled.add(doc.getSettledAmount() != null ? doc.getSettledAmount() : BigDecimal.ZERO);
                totalUnsettled = totalUnsettled.add(doc.getUnsettledAmount() != null ? doc.getUnsettledAmount() : BigDecimal.ZERO);
            }

            CustomerStatementEntity entity = new CustomerStatementEntity();
            entity.setCustomerId(customerId);
            entity.setCustomerName(null); // 由前端或后续查询补全
            entity.setPeriod(period);
            entity.setStatementDate(today);
            entity.setTotalOriginal(totalOriginal);
            entity.setTotalSettled(totalSettled);
            entity.setTotalUnsettled(totalUnsettled);
            entity.setStatus("DRAFT");
            statementMapper.insert(entity);
            result.add(entity);
        }

        return result;
    }

    @Override
    public CustomerStatementEntity getById(Long id) {
        CustomerStatementEntity entity = statementMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("对账单不存在");
        }
        return entity;
    }

    @Override
    public IPage<CustomerStatementEntity> pageQuery(String status, Integer current, Integer size) {
        Page<CustomerStatementEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<CustomerStatementEntity> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(CustomerStatementEntity::getStatus, status);
        }
        wrapper.orderByDesc(CustomerStatementEntity::getCreatedAt);
        return statementMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void send(Long id) {
        CustomerStatementEntity entity = getById(id);
        if (!"DRAFT".equals(entity.getStatus()) && !"GENERATED".equals(entity.getStatus())) {
            throw new BusinessException("仅草稿或已生成状态可发送");
        }
        entity.setStatus("SENT");
        entity.setSentAt(LocalDateTime.now());
        statementMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        CustomerStatementEntity entity = getById(id);
        if (!"SENT".equals(entity.getStatus())) {
            throw new BusinessException("仅已发送状态可确认");
        }
        entity.setStatus("CONFIRMED");
        entity.setConfirmedAt(LocalDateTime.now());
        statementMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispute(Long id, DisputeRequest request) {
        CustomerStatementEntity entity = getById(id);
        if (!"SENT".equals(entity.getStatus()) && !"CONFIRMED".equals(entity.getStatus())) {
            throw new BusinessException("仅已发送或已确认状态可发起差异");
        }
        entity.setStatus("DISPUTED");
        statementMapper.updateById(entity);

        // 创建差异记录
        DisputeEntity dispute = new DisputeEntity();
        dispute.setStatementId(id);
        dispute.setCustomerId(entity.getCustomerId());
        dispute.setDocNo(request.docNo());
        dispute.setDisputeType(request.disputeType());
        dispute.setExpectedAmount(request.expectedAmount());
        dispute.setActualAmount(request.actualAmount());
        dispute.setDiffAmount(request.expectedAmount().subtract(request.actualAmount()));
        dispute.setReason(request.reason());
        disputeMapper.insert(dispute);
    }

    @Override
    public IPage<OutstandingItemVO> pageOutstandingItems(Long statementId, Long customerId,
                                                          String status, Integer current, Integer size) {
        Page<OutstandingItemEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<OutstandingItemEntity> wrapper = new LambdaQueryWrapper<>();
        if (statementId != null) {
            wrapper.eq(OutstandingItemEntity::getStatementId, statementId);
        }
        if (customerId != null) {
            wrapper.eq(OutstandingItemEntity::getCustomerId, customerId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(OutstandingItemEntity::getStatus, status);
        }
        wrapper.orderByDesc(OutstandingItemEntity::getCreatedAt);

        IPage<OutstandingItemEntity> pageResult = outstandingItemMapper.selectPage(page, wrapper);
        return pageResult.convert(e -> new OutstandingItemVO(
                e.getId(), e.getCustomerId(), e.getStatementId(),
                e.getOutstandingType(), e.getAmount(),
                e.getDescription(), e.getEvidence(),
                e.getStatus(), e.getResolvedAt(),
                e.getCreatedAt()
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveOutstandingItem(Long id) {
        OutstandingItemEntity item = outstandingItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("未达账项不存在");
        }
        if (!"PENDING".equals(item.getStatus())) {
            throw new BusinessException("仅待处理状态的未达账项可解决");
        }
        item.setStatus("RESOLVED");
        item.setResolvedAt(LocalDateTime.now());
        outstandingItemMapper.updateById(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOutstandingItem(Long id) {
        OutstandingItemEntity item = outstandingItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("未达账项不存在");
        }
        if (!"PENDING".equals(item.getStatus())) {
            throw new BusinessException("仅待处理状态的未达账项可取消");
        }
        item.setStatus("CANCELLED");
        outstandingItemMapper.updateById(item);
    }

    @Override
    public IPage<DisputeVO> pageDisputes(Long statementId, Long customerId,
                                          String disputeType, Integer current, Integer size) {
        Page<DisputeEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<DisputeEntity> wrapper = new LambdaQueryWrapper<>();
        if (statementId != null) {
            wrapper.eq(DisputeEntity::getStatementId, statementId);
        }
        if (customerId != null) {
            wrapper.eq(DisputeEntity::getCustomerId, customerId);
        }
        if (disputeType != null && !disputeType.isBlank()) {
            wrapper.eq(DisputeEntity::getDisputeType, disputeType);
        }
        wrapper.orderByDesc(DisputeEntity::getCreatedAt);

        IPage<DisputeEntity> pageResult = disputeMapper.selectPage(page, wrapper);
        return pageResult.convert(e -> new DisputeVO(
                e.getId(), e.getStatementId(), e.getCustomerId(),
                e.getDocNo(), e.getDisputeType(),
                e.getExpectedAmount(), e.getActualAmount(), e.getDiffAmount(),
                e.getReason(), e.getResolution(),
                e.getResolvedBy(), e.getResolvedAt(),
                e.getCreatedAt()
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveDispute(Long id, String resolution) {
        DisputeEntity dispute = disputeMapper.selectById(id);
        if (dispute == null) {
            throw new BusinessException("差异记录不存在");
        }
        dispute.setResolution(resolution);
        dispute.setResolvedAt(LocalDateTime.now());
        disputeMapper.updateById(dispute);
    }

    @Override
    public List<Long> getCustomerIdsWithOpenDisputes() {
        List<DisputeEntity> list = disputeMapper.selectList(
                new LambdaQueryWrapper<DisputeEntity>()
                        .isNull(DisputeEntity::getResolvedAt)
                        .select(DisputeEntity::getCustomerId)
        );
        return list.stream()
                .map(DisputeEntity::getCustomerId)
                .distinct()
                .collect(Collectors.toList());
    }
}