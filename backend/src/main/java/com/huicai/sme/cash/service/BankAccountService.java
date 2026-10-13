package com.huicai.sme.cash.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.cash.entity.BankAccountEntity;

import java.util.List;

public interface BankAccountService {
    IPage<BankAccountEntity> pageQuery(String keyword, Integer current, Integer size);
    List<BankAccountEntity> listActive();
    BankAccountEntity getById(Long id);
    BankAccountEntity create(BankAccountEntity entity);
    BankAccountEntity update(Long id, BankAccountEntity entity);
    void delete(Long id);

    /**
     * 系统管理员专用：列出全部企业的银行账户。
     *
     * <p>t_bank_account 开启 FORCE RLS，跨企业读取被按设计挡住，
     * 故实现须<b>逐企业建立上下文后聚合</b>（每段查询仍在该企业 RLS 约束下执行）。
     *
     * <p>⚠️ 调用方必须校验 SUPER_ADMIN。
     */
    List<BankAccountEntity> listAllEnterprisesForSuperAdmin();
}
