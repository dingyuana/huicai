package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.TicketEntity;
import com.huicai.module.finance.entity.TicketTransactionEntity;
import com.huicai.module.finance.mapper.TicketMapper;
import com.huicai.module.finance.mapper.TicketTransactionMapper;
import com.huicai.module.finance.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, TicketEntity>
        implements TicketService {

    private final TicketMapper ticketMapper;
    private final TicketTransactionMapper transactionMapper;

    @Override
    public IPage<TicketEntity> pageQuery(String ticketType, String status,
                                         Integer current, Integer size) {
        Page<TicketEntity> page = new Page<>(current == null ? 1 : current, size == null ? 20 : size);
        LambdaQueryWrapper<TicketEntity> wrapper = new LambdaQueryWrapper<TicketEntity>()
                .eq(StrUtil.isNotBlank(ticketType), TicketEntity::getTicketType, ticketType)
                .eq(StrUtil.isNotBlank(status), TicketEntity::getStatus, status)
                .orderByDesc(TicketEntity::getIssueDate)
                .orderByDesc(TicketEntity::getId);
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public TicketEntity getById(Long id) {
        TicketEntity entity = baseMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException("票据不存在");
        }
        return entity;
    }

    @Override
    @Transactional
    public TicketEntity create(TicketEntity entity, Long userId) {
        if (entity.getAmount() == null || entity.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("票据金额必须大于0");
        }
        entity.setStatus("IN_STOCK");
        entity.setCreatedBy(userId);
        baseMapper.insert(entity);
        log.info("创建票据: id={}, no={}, type={}, amount={}",
                entity.getId(), entity.getTicketNo(), entity.getTicketType(), entity.getAmount());
        return entity;
    }

    @Override
    @Transactional
    public TicketEntity update(Long id, TicketEntity entity) {
        TicketEntity existing = getById(id);
        if (!"IN_STOCK".equals(existing.getStatus())) {
            throw new BusinessException("仅待入库状态的票据可修改");
        }
        entity.setId(id);
        baseMapper.updateById(entity);
        return baseMapper.selectById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        TicketEntity existing = getById(id);
        if (!"IN_STOCK".equals(existing.getStatus())) {
            throw new BusinessException("仅待入库状态的票据可删除");
        }
        baseMapper.deleteById(id);
        log.info("删除票据: id={}", id);
    }

    @Override
    @Transactional
    public TicketEntity issue(Long id, Long userId) {
        TicketEntity entity = getById(id);
        if (!"IN_STOCK".equals(entity.getStatus())) {
            throw new BusinessException("票据状态不正确，无法领用");
        }
        entity.setStatus("ISSUED");
        baseMapper.updateById(entity);

        TicketTransactionEntity tx = new TicketTransactionEntity();
        tx.setTicketId(id);
        tx.setTransType("ISSUE");
        tx.setTransDate(LocalDate.now());
        tx.setRecipient(entity.getPayee());
        tx.setAmount(entity.getAmount());
        tx.setOperatorId(userId);
        transactionMapper.insert(tx);

        log.info("票据领用: ticketId={}, transId={}", id, tx.getId());
        return entity;
    }

    @Override
    @Transactional
    public TicketEntity cash(Long id, Long userId) {
        TicketEntity entity = getById(id);
        if (!"ISSUED".equals(entity.getStatus()) && !"ENDORSED".equals(entity.getStatus())) {
            throw new BusinessException("票据状态不正确，无法兑现");
        }
        entity.setStatus("CASHED");
        baseMapper.updateById(entity);

        TicketTransactionEntity tx = new TicketTransactionEntity();
        tx.setTicketId(id);
        tx.setTransType("CASH");
        tx.setTransDate(LocalDate.now());
        tx.setAmount(entity.getAmount());
        tx.setOperatorId(userId);
        transactionMapper.insert(tx);

        log.info("票据兑现: ticketId={}, transId={}", id, tx.getId());
        return entity;
    }

    @Override
    @Transactional
    public TicketEntity voidTicket(Long id, Long userId) {
        TicketEntity entity = getById(id);
        if ("CASHED".equals(entity.getStatus()) || "VOIDED".equals(entity.getStatus())) {
            throw new BusinessException("已兑现或已作废的票据不可重复操作");
        }
        entity.setStatus("VOIDED");
        baseMapper.updateById(entity);

        TicketTransactionEntity tx = new TicketTransactionEntity();
        tx.setTicketId(id);
        tx.setTransType("VOID");
        tx.setTransDate(LocalDate.now());
        tx.setAmount(entity.getAmount());
        tx.setOperatorId(userId);
        transactionMapper.insert(tx);

        log.info("票据作废: ticketId={}, transId={}", id, tx.getId());
        return entity;
    }

    @Override
    public List<TicketTransactionEntity> getTransactions(Long ticketId) {
        return transactionMapper.selectList(
                new LambdaQueryWrapper<TicketTransactionEntity>()
                        .eq(TicketTransactionEntity::getTicketId, ticketId)
                        .orderByAsc(TicketTransactionEntity::getCreatedAt));
    }
}