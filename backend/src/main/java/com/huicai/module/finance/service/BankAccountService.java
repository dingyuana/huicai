package com.huicai.module.finance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.finance.entity.BankAccountEntity;

import java.util.List;

public interface BankAccountService {
    IPage<BankAccountEntity> pageQuery(String keyword, Integer current, Integer size);
    List<BankAccountEntity> listActive();
    BankAccountEntity getById(Long id);
    BankAccountEntity create(BankAccountEntity entity);
    BankAccountEntity update(Long id, BankAccountEntity entity);
    void delete(Long id);
}
