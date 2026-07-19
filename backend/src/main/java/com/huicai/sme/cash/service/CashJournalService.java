package com.huicai.sme.cash.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.cash.entity.CashJournalEntity;

import java.time.LocalDate;

public interface CashJournalService {

    IPage<CashJournalEntity> pageQuery(String period, LocalDate startDate, LocalDate endDate,
                                       Integer current, Integer size);

    CashJournalEntity getById(Long id);

    CashJournalEntity create(CashJournalEntity entity, Long userId);

    CashJournalEntity update(Long id, CashJournalEntity entity);

    void delete(Long id);

    Long generateVoucher(Long id, Long userId);
}