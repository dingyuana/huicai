package com.huicai.sme.cash.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.cash.entity.BankAccountEntity;

import java.util.List;

public interface BankAccountService {
    IPage<BankAccountEntity> pageQuery(String keyword, Integer current, Integer size);

    /**
     * SPEC-P129 §1③：按企业过滤的分页查询。
     *
     * <p>{@code enterpriseId} 为 {@code null} 时行为与原方法<b>完全一致</b>（AT-129-9 回归锁）。
     *
     * <p>🔴 本方法<b>不做</b>可见集校验 —— 那是 Controller 的职责：校验必须落在入口，
     * 否则被内部调用绕过就等于没校验。
     */
    IPage<BankAccountEntity> pageQuery(String keyword, Integer current, Integer size,
                                       Long enterpriseId);

    /**
     * SPEC-P129 §1③：<b>跨企业</b>分页查询（会逐企业建立上下文）。
     *
     * <p>🔴 与上一方法的区别不是「多传了个 id」，而是<b>能不能真的查到别的企业的数据</b>：
     * {@code t_bank_account} 开了 FORCE RLS，只加 {@code eq(enterpriseId, X)} 时，
     * X ≠ 当前企业会被滤成空列表且<b>不报错</b>。
     *
     * <p>⚠️ 调用方<b>必须先做可见集校验</b> —— 本方法只负责让 RLS 放行，
     * 不负责判断「该不该给这个人看」。
     */
    IPage<BankAccountEntity> pageQueryAcrossEnterprise(String keyword, Integer current,
                                                       Integer size, Long enterpriseId);
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
