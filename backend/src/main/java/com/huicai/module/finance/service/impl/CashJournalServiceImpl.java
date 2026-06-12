package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.dto.VoucherCreateDTO;
import com.huicai.module.finance.entity.CashJournalEntity;
import com.huicai.module.finance.mapper.CashJournalMapper;
import com.huicai.module.finance.service.CashJournalService;
import com.huicai.module.finance.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashJournalServiceImpl extends ServiceImpl<CashJournalMapper, CashJournalEntity>
        implements CashJournalService {

    private final CashJournalMapper cashJournalMapper;
    private final VoucherService voucherService;

    @Override
    public IPage<CashJournalEntity> pageQuery(String period, LocalDate startDate, LocalDate endDate,
                                              Integer current, Integer size) {
        Page<CashJournalEntity> page = new Page<>(current == null ? 1 : current, size == null ? 20 : size);
        LambdaQueryWrapper<CashJournalEntity> wrapper = new LambdaQueryWrapper<CashJournalEntity>()
                .eq(StrUtil.isNotBlank(period), CashJournalEntity::getPeriod, period)
                .ge(startDate != null, CashJournalEntity::getJournalDate, startDate)
                .le(endDate != null, CashJournalEntity::getJournalDate, endDate)
                .orderByDesc(CashJournalEntity::getJournalDate)
                .orderByDesc(CashJournalEntity::getId);
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public CashJournalEntity getById(Long id) {
        CashJournalEntity entity = baseMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException("日记账记录不存在");
        }
        return entity;
    }

    @Override
    @Transactional
    public CashJournalEntity create(CashJournalEntity entity, Long userId) {
        // Defaults
        if (entity.getDebit() == null) entity.setDebit(BigDecimal.ZERO);
        if (entity.getCredit() == null) entity.setCredit(BigDecimal.ZERO);
        if (entity.getSource() == null) entity.setSource("MANUAL");

        // Auto-calculate balance
        BigDecimal lastBalance = cashJournalMapper.getLastBalance(entity.getPeriod());
        if (lastBalance == null) lastBalance = BigDecimal.ZERO;
        entity.setBalance(lastBalance.add(entity.getDebit()).subtract(entity.getCredit()));

        entity.setCreatedBy(userId);
        baseMapper.insert(entity);
        log.info("创建现金日记账: id={}, period={}, amount={}/{}, balance={}",
                entity.getId(), entity.getPeriod(), entity.getDebit(), entity.getCredit(), entity.getBalance());
        return entity;
    }

    @Override
    @Transactional
    public CashJournalEntity update(Long id, CashJournalEntity entity) {
        CashJournalEntity existing = getById(id);
        if (existing.getVoucherId() != null) {
            throw new BusinessException("已生成凭证的记录不可修改");
        }
        entity.setId(id);
        entity.setUpdatedBy(entity.getUpdatedBy());
        baseMapper.updateById(entity);
        return baseMapper.selectById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        CashJournalEntity existing = getById(id);
        if (existing.getVoucherId() != null) {
            throw new BusinessException("已生成凭证的记录不可删除");
        }
        baseMapper.deleteById(id);
        log.info("删除现金日记账: id={}", id);
    }

    @Override
    @Transactional
    public Long generateVoucher(Long id, Long userId) {
        CashJournalEntity journal = getById(id);
        if (journal.getVoucherId() != null) {
            throw new BusinessException("该记录已生成凭证，不能重复生成");
        }

        // Get default voucher type for cash journal
        Long voucherTypeId = getDefaultVoucherType();

        // Build voucher DTO
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod(journal.getPeriod());
        dto.setVoucherTypeId(voucherTypeId);
        dto.setSummary(journal.getSummary());

        List<VoucherCreateDTO.EntryDTO> entries = new ArrayList<>();

        if (journal.getDebit().compareTo(BigDecimal.ZERO) > 0) {
            // Debit entry - cash subject debit, opposite subject credit
            VoucherCreateDTO.EntryDTO debitEntry = new VoucherCreateDTO.EntryDTO();
            debitEntry.setSubjectId(journal.getSubjectId());
            debitEntry.setDebit(journal.getDebit());
            debitEntry.setCredit(BigDecimal.ZERO);
            debitEntry.setSummary(journal.getSummary());
            entries.add(debitEntry);

            if (journal.getOppositeSubjectId() != null) {
                VoucherCreateDTO.EntryDTO creditEntry = new VoucherCreateDTO.EntryDTO();
                creditEntry.setSubjectId(journal.getOppositeSubjectId());
                creditEntry.setDebit(BigDecimal.ZERO);
                creditEntry.setCredit(journal.getDebit());
                creditEntry.setSummary(journal.getSummary());
                entries.add(creditEntry);
            }
        } else if (journal.getCredit().compareTo(BigDecimal.ZERO) > 0) {
            // Credit entry - cash subject credit, opposite subject debit
            if (journal.getOppositeSubjectId() != null) {
                VoucherCreateDTO.EntryDTO debitEntry = new VoucherCreateDTO.EntryDTO();
                debitEntry.setSubjectId(journal.getOppositeSubjectId());
                debitEntry.setDebit(journal.getCredit());
                debitEntry.setCredit(BigDecimal.ZERO);
                debitEntry.setSummary(journal.getSummary());
                entries.add(debitEntry);
            }

            VoucherCreateDTO.EntryDTO creditEntry = new VoucherCreateDTO.EntryDTO();
            creditEntry.setSubjectId(journal.getSubjectId());
            creditEntry.setDebit(BigDecimal.ZERO);
            creditEntry.setCredit(journal.getCredit());
            creditEntry.setSummary(journal.getSummary());
            entries.add(creditEntry);
        }

        dto.setEntries(entries);
        var voucherVO = voucherService.create(dto, userId);

        // Link back
        journal.setVoucherId(voucherVO.getId());
        baseMapper.updateById(journal);

        log.info("现金日记账生成凭证: journalId={}, voucherId={}", id, voucherVO.getId());
        return voucherVO.getId();
    }

    private Long getDefaultVoucherType() {
        // Return first active voucher type - in production, this could be configurable
        return 1L;
    }
}