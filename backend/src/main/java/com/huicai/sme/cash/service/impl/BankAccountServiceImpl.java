package com.huicai.sme.cash.service.impl;

import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.security.Ref;
import com.huicai.common.security.ReferenceGuard;
import com.huicai.common.exception.BusinessException;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.sme.cash.mapper.BankAccountMapper;
import com.huicai.sme.cash.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
public class BankAccountServiceImpl implements BankAccountService {

    private final BankAccountMapper mapper;
    private final EnterpriseMapper enterpriseMapper;

    /**
     * 系统管理员专用：列出<b>全部企业</b>的银行账户。
     *
     * <p><b>为什么不能一条 SQL 直出</b>：{@code t_bank_account} 开着
     * <b>FORCE RLS</b>，谓词为 {@code enterprise_id = current_setting('app.enterprise_id')}，
     * 实测无 GUC 时恒返 <b>0 行</b> —— 跨企业读取被 RLS 按设计挡住。
     *
     * <p><b>因此逐企业建立上下文再聚合</b>：每段查询都在该企业自己的 RLS 约束下执行，
     * 与它被单独访问时的可见集合<b>完全一致</b>，不需要任何特权连接或绕过 RLS。
     *
     * <p>必须在一个事务内：企业 GUC 用的是 {@code SET LOCAL}，仅在事务块内有效。
     *
     * <p>⚠️ 调用方必须校验 SUPER_ADMIN —— ��是一个跨企业读取入口。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<BankAccountEntity> listAllEnterprisesForSuperAdmin() {
        List<Long> enterpriseIds = enterpriseMapper
                .selectList(new LambdaQueryWrapper<EnterpriseEntity>()
                        .eq(EnterpriseEntity::getDeleted, 0))
                .stream().map(EnterpriseEntity::getId).toList();

        List<BankAccountEntity> all = new ArrayList<>();
        Long original = EnterpriseContextHolder.get();
        try {
            for (Long enterpriseId : enterpriseIds) {
                EnterpriseContextHolder.set(enterpriseId);
                // 每家企业单独建 wrapper，避免条件与上轮状态串味
                all.addAll(mapper.selectList(new LambdaQueryWrapper<BankAccountEntity>()
                        .eq(BankAccountEntity::getDeleted, 0)
                        .orderByDesc(BankAccountEntity::getId)));
            }
        } finally {
            if (original != null) {
                EnterpriseContextHolder.set(original);
            } else {
                EnterpriseContextHolder.clear();
            }
        }
        return all;
    }

    /**
     * 系统管理员跨企业读取银行账户，可按企业收敛。
     *
     * <p>{@code enterpriseId} 为 {@code null} 时行为与改动前完全一致（遍历全部企业）。
     *
     * <p>🔴 <b>为什么不靠前端过滤</b>：{@code BankAccountVO} 明确「租户 id 不外露」，
     * 且有 {@code MasterDataVoContractTest#internalFieldsNeverLeak} 钉住 —— 若为了前端筛选
     * 把 {@code enterpriseId} 塞进 VO，等于为一个便利性需求<b>放宽既有安全守卫</b>。
     * 而 {@code /all-enterprises} 本就是 SUPER_ADMIN 入口，收敛在服务端做，
     * <b>响应结构一字不改</b>，守卫不用动。
     *
     * <p>⚠️ 必须在一个事务内：企业 GUC 用 {@code SET LOCAL}，仅在事务块内有效。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<BankAccountEntity> listAccountsForSuperAdmin(Long enterpriseId) {
        if (enterpriseId != null) {
            Long original = EnterpriseContextHolder.get();
            try {
                EnterpriseContextHolder.set(enterpriseId);
                // 每家企业单独建 wrapper，避免条件与上轮状态串味
                return new ArrayList<>(mapper.selectList(new LambdaQueryWrapper<BankAccountEntity>()
                        .eq(BankAccountEntity::getDeleted, 0)
                        .orderByDesc(BankAccountEntity::getId)));
            } finally {
                if (original != null) {
                    EnterpriseContextHolder.set(original);
                } else {
                    EnterpriseContextHolder.clear();
                }
            }
        }
        return listAllEnterprisesForSuperAdmin();
    }

    @Override
    public IPage<BankAccountEntity> pageQuery(String keyword, Integer current, Integer size) {
        return pageQuery(keyword, current, size, null);
    }

    @Override
    public IPage<BankAccountEntity> pageQuery(String keyword, Integer current, Integer size,
                                             Long enterpriseId) {
        Page<BankAccountEntity> page = new Page<>(current == null ? 1 : current, size == null ? 20 : size);
        LambdaQueryWrapper<BankAccountEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(BankAccountEntity::getAccountNo, keyword)
                    .or().like(BankAccountEntity::getAccountName, keyword)
                    .or().like(BankAccountEntity::getBankName, keyword));
        }
        // SPEC-P129 §1③：按企业收敛。enterpriseId 为 null 时不加条件，
        // 行为与改动前逐字一致（AT-129-9 回归锁）。
        if (enterpriseId != null) {
            wrapper.eq(BankAccountEntity::getEnterpriseId, enterpriseId);
        }
        wrapper.orderByDesc(BankAccountEntity::getId);
        return mapper.selectPage(page, wrapper);
    }

    /**
     * SPEC-P129 §1③：跨企业分页查询。
     *
     * <p>🔴 <b>为什么不能只加一个 {@code eq(enterpriseId, X)}</b>：{@code t_bank_account}
     * 开了 <b>FORCE RLS</b>，谓词是 {@code enterprise_id = app.enterprise_id}，而该 GUC
     * 来自<b>当前上下文</b>。于是当 X ≠ 当前企业时，行会被 RLS 过滤成空 ——
     * <b>接口返 200 + 空列表且不报任何错</b>（实测：企业 2 明明有账号，传 enterpriseId=2 返 0）。
     * 这是「读路径静默失效」，比抛错危险得多：用户只会以为「这家没账号」。
     *
     * <p>解法与 {@link #listAllEnterprisesForSuperAdmin()} 同一套：<b>逐企业建立上下文</b>后查询，
     * 查完复原 —— <b>不需要特权连接、也不绕过 RLS</b>。
     *
     * <p>⚠️ <b>必须在事务内</b>：企业 GUC 用 {@code SET LOCAL} 写入，仅在事务块内有效；
     * 事务外切换会被下一次查询立刻丢弃（本项目 P122/P127 两次栽在这条边界上）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public IPage<BankAccountEntity> pageQueryAcrossEnterprise(String keyword, Integer current,
                                                               Integer size, Long enterpriseId) {
        Long original = EnterpriseContextHolder.get();
        if (enterpriseId == null || enterpriseId.equals(original)) {
            // 当前企业（或未指定）：不切上下文，行为与改动前完全一致
            return pageQuery(keyword, current, size, enterpriseId);
        }
        try {
            EnterpriseContextHolder.set(enterpriseId);
            return pageQuery(keyword, current, size, enterpriseId);
        } finally {
            if (original != null) {
                EnterpriseContextHolder.set(original);
            } else {
                EnterpriseContextHolder.clear();
            }
        }
    }

    @Override
    public List<BankAccountEntity> listActive() {
        return mapper.selectList(new LambdaQueryWrapper<BankAccountEntity>()
                .eq(BankAccountEntity::getIsActive, true)
                .orderByAsc(BankAccountEntity::getAccountName));
    }

    @Override
    public BankAccountEntity getById(Long id) {
        BankAccountEntity e = mapper.selectById(id);
        if (e == null) throw BusinessException.notFound("银行账户不存在");
        return e;
    }

    @Override
    public BankAccountEntity create(BankAccountEntity entity) {
        validateUnique(entity.getAccountNo(), null);
        if (entity.getCurrency() == null) entity.setCurrency("CNY");
        if (entity.getBalance() == null) entity.setBalance(java.math.BigDecimal.ZERO);
        if (entity.getIsActive() == null) entity.setIsActive(true);
        mapper.insert(entity);
        return entity;
    }

    @Override
    public BankAccountEntity update(Long id, BankAccountEntity entity) {
        BankAccountEntity existing = getById(id);
        validateUnique(entity.getAccountNo(), id);
        existing.setAccountNo(entity.getAccountNo());
        existing.setAccountName(entity.getAccountName());
        existing.setBankName(entity.getBankName());
        existing.setCurrency(entity.getCurrency());
        existing.setSubjectId(entity.getSubjectId());
        existing.setIsActive(entity.getIsActive());
        existing.setRemark(entity.getRemark());
        mapper.updateById(existing);
        return existing;
    }

    @Override
    @ReferenceGuard(
            parent = "t_bank_account",
            label = "银行账户",
            children = {
                    @Ref(table = "t_bank_statement", column = "account_id", label = "银行流水"),
                    @Ref(table = "t_bank_journal", column = "account_id", label = "银行日记账"),
                    @Ref(table = "t_reconciliation_exception", column = "account_id", label = "对账异常")
            })
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private void validateUnique(String accountNo, Long excludeId) {
        if (StrUtil.isBlank(accountNo)) return;
        LambdaQueryWrapper<BankAccountEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BankAccountEntity::getAccountNo, accountNo);
        if (excludeId != null) wrapper.ne(BankAccountEntity::getId, excludeId);
        if (mapper.selectCount(wrapper) > 0) {
            throw BusinessException.conflict("账号已存在: " + accountNo);
        }
    }
}
