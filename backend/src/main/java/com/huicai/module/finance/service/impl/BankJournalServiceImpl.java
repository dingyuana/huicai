package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.BankAccountEntity;
import com.huicai.module.finance.entity.BankJournalEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.BankAccountMapper;
import com.huicai.module.finance.mapper.BankJournalMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.BankJournalService;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.service.PeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankJournalServiceImpl implements BankJournalService {

    private final BankJournalMapper journalMapper;
    private final BankAccountMapper accountMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final PeriodService periodService;
    private final SubjectMapper subjectMapper;

    @Override
    public IPage<BankJournalEntity> pageQuery(Long accountId, String period, String txType, Integer current, Integer size) {
        Page<BankJournalEntity> page = new Page<>(current == null ? 1 : current, size == null ? 20 : size);
        LambdaQueryWrapper<BankJournalEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(accountId != null, BankJournalEntity::getAccountId, accountId)
                .eq(StrUtil.isNotBlank(period), BankJournalEntity::getPeriod, period)
                .eq(StrUtil.isNotBlank(txType), BankJournalEntity::getTxType, txType)
                .orderByDesc(BankJournalEntity::getTxDate)
                .orderByDesc(BankJournalEntity::getId);
        return journalMapper.selectPage(page, wrapper);
    }

    @Override
    public BankJournalEntity getById(Long id) {
        BankJournalEntity e = journalMapper.selectById(id);
        if (e == null) throw BusinessException.notFound("日记账分录不存在");
        return e;
    }

    @Override
    @Transactional
    public BankJournalEntity create(BankJournalEntity entity, Long userId) {
        BankAccountEntity account = accountMapper.selectById(entity.getAccountId());
        if (account == null) throw BusinessException.badRequest("银行账户不存在");
        validatePeriodOpen(entity.getPeriod());
        if (entity.getAmount() == null || entity.getAmount().signum() <= 0) {
            throw BusinessException.badRequest("金额必须大于0");
        }
        if (!List.of("INCOME", "EXPENSE", "TRANSFER_IN", "TRANSFER_OUT").contains(entity.getTxType())) {
            throw BusinessException.badRequest("交易类型不合法");
        }
        entity.setCreatedBy(userId);
        entity.setIsReconciled(false);
        journalMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public BankJournalEntity update(Long id, BankJournalEntity entity) {
        BankJournalEntity existing = getById(id);
        if (Boolean.TRUE.equals(existing.getIsReconciled())) {
            throw BusinessException.badRequest("已对账的分录不可修改");
        }
        if (existing.getVoucherId() != null) {
            throw BusinessException.badRequest("已生成凭证的分录不可修改");
        }
        validatePeriodOpen(existing.getPeriod());
        existing.setTxType(entity.getTxType());
        existing.setCounterAccount(entity.getCounterAccount());
        existing.setAmount(entity.getAmount());
        existing.setSummary(entity.getSummary());
        existing.setTxDate(entity.getTxDate());
        journalMapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        BankJournalEntity e = getById(id);
        if (Boolean.TRUE.equals(e.getIsReconciled())) {
            throw BusinessException.badRequest("已对账的分录不可删除");
        }
        if (e.getVoucherId() != null) {
            throw BusinessException.badRequest("已生成凭证的分录不可删除");
        }
        journalMapper.deleteById(id);
    }

    @Override
    @Transactional
    public Long generateVoucher(Long id, Long userId) {
        BankJournalEntity journal = getById(id);
        if (journal.getVoucherId() != null) {
            throw BusinessException.badRequest("该日记账分录已生成凭证");
        }
        BankAccountEntity account = accountMapper.selectById(journal.getAccountId());
        if (account == null || account.getSubjectId() == null) {
            throw BusinessException.badRequest("账户未关联科目, 无法生成凭证");
        }
        Subject bankSubject = subjectMapper.selectById(account.getSubjectId());
        if (bankSubject == null) throw BusinessException.badRequest("账户关联科目不存在");

        // 简化的对方科目策略: 收款用"其他应付款", 付款用"其他应收款", 可后续扩展
        Subject counterSubj = null;
        switch (journal.getTxType()) {
            case "INCOME" -> {
                counterSubj = findSubjectByCode("1002.01", "1002", "1001");
                if (counterSubj == null) {
                    throw BusinessException.badRequest("无法确定收款对方科目, 请手工设置银行科目并提供对方科目模板");
                }
            }
            case "EXPENSE" -> {
                counterSubj = findFirstLeafByCodePrefix("5501");
                if (counterSubj == null) {
                    throw BusinessException.badRequest("无法找到费用类对方科目(5501)");
                }
            }
            default -> throw BusinessException.badRequest("暂不支持该交易类型自动生成凭证: " + journal.getTxType());
        }

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNoService.generateNextNo(journal.getPeriod(), 1L));
        voucher.setPeriod(journal.getPeriod());
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(journal.getSummary() != null ? journal.getSummary() : ("银行日记账: " + account.getAccountName()));
        voucher.setTotalDebit(journal.getAmount());
        voucher.setTotalCredit(journal.getAmount());
        voucher.setCreatedBy(userId);
        voucherMapper.insert(voucher);

        VoucherEntryEntity e1 = new VoucherEntryEntity();
        e1.setVoucherId(voucher.getId());
        e1.setSortOrder(1);
        VoucherEntryEntity e2 = new VoucherEntryEntity();
        e2.setVoucherId(voucher.getId());
        e2.setSortOrder(2);

        boolean isIncome = "INCOME".equals(journal.getTxType());
        if (isIncome) {
            // 借:银行存款, 贷:其他应付款
            e1.setSubjectId(bankSubject.getId());
            e1.setDebit(journal.getAmount());
            e1.setCredit(BigDecimal.ZERO);
            e2.setSubjectId(counterSubj.getId());
            e2.setDebit(BigDecimal.ZERO);
            e2.setCredit(journal.getAmount());
        } else {
            // 借:管理费用, 贷:银行存款
            e1.setSubjectId(counterSubj.getId());
            e1.setDebit(journal.getAmount());
            e1.setCredit(BigDecimal.ZERO);
            e2.setSubjectId(bankSubject.getId());
            e2.setDebit(BigDecimal.ZERO);
            e2.setCredit(journal.getAmount());
        }
        voucherEntryMapper.insert(e1);
        voucherEntryMapper.insert(e2);

        journalMapper.updateVoucherId(id, voucher.getId());
        log.info("日记账生成凭证: journalId={}, voucherId={}", id, voucher.getId());
        return voucher.getId();
    }

    @Override
    public List<Map<String, Object>> aggregate(Long accountId, String period) {
        return journalMapper.aggregateByAccountPeriod(accountId, period);
    }

    @Override
    public BigDecimal getAccountBalance(Long accountId) {
        return journalMapper.sumAmountByAccount(accountId);
    }

    private void validatePeriodOpen(String period) {
        PeriodEntity p = periodService.lambdaQuery().eq(PeriodEntity::getPeriodCode, period).one();
        if (p == null) throw BusinessException.badRequest("会计期间不存在: " + period);
        if ("closed".equals(p.getStatus()) || "locked".equals(p.getStatus())) {
            throw BusinessException.badRequest("会计期间不可操作: " + period);
        }
    }

    private Subject findSubjectByCode(String... codes) {
        for (String c : codes) {
            Subject s = subjectMapper.selectOne(
                    new LambdaQueryWrapper<Subject>().eq(Subject::getCode, c));
            if (s != null && Boolean.TRUE.equals(s.getIsLeaf())) return s;
        }
        return null;
    }

    private Subject findFirstLeafByCodePrefix(String prefix) {
        List<Subject> all = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>()
                        .likeRight(Subject::getCode, prefix)
                        .eq(Subject::getIsLeaf, true));
        return all.isEmpty() ? null : all.get(0);
    }
}
