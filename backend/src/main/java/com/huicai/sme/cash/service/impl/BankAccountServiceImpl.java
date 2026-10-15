package com.huicai.sme.cash.service.impl;

import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
