package com.huicai.sme.cash.service.impl;

import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.sme.cash.mapper.BankAccountMapper;
import com.huicai.sme.cash.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    @Override
    public IPage<BankAccountEntity> pageQuery(String keyword, Integer current, Integer size) {
        Page<BankAccountEntity> page = new Page<>(current == null ? 1 : current, size == null ? 20 : size);
        LambdaQueryWrapper<BankAccountEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(BankAccountEntity::getAccountNo, keyword)
                    .or().like(BankAccountEntity::getAccountName, keyword)
                    .or().like(BankAccountEntity::getBankName, keyword));
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
