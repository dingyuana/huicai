package com.huicai.module.finance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.finance.entity.BankJournalEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface BankJournalService {
    IPage<BankJournalEntity> pageQuery(Long accountId, String period, String txType, Integer current, Integer size);
    BankJournalEntity getById(Long id);
    BankJournalEntity create(BankJournalEntity entity, Long userId);
    BankJournalEntity update(Long id, BankJournalEntity entity);
    void delete(Long id);
    Long generateVoucher(Long id, Long userId);
    List<Map<String, Object>> aggregate(Long accountId, String period);
    BigDecimal getAccountBalance(Long accountId);
}
