package com.huicai.agency.client.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.agency.client.dto.ContractCreateDTO;
import com.huicai.agency.client.dto.ContractVO;
import com.huicai.agency.client.dto.RenewalReminderVO;
import com.huicai.agency.client.entity.ContractEntity;
import com.huicai.agency.client.mapper.ContractMapper;
import com.huicai.agency.client.service.ContractService;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractMapper contractMapper;
    private final EnterpriseMapper enterpriseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVO create(ContractCreateDTO dto) {
        ContractEntity exist = contractMapper.selectOne(
                new LambdaQueryWrapper<ContractEntity>()
                        .eq(ContractEntity::getContractNo, dto.getContractNo()));
        if (exist != null) {
            throw BusinessException.conflict("合同编号已存在: " + dto.getContractNo());
        }

        ContractEntity entity = new ContractEntity();
        entity.setEnterpriseId(dto.getEnterpriseId());
        entity.setAgencyId(dto.getAgencyId());
        entity.setContractNo(dto.getContractNo());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setContractType(dto.getContractType());
        entity.setAmount(dto.getAmount());
        entity.setStatus("ACTIVE");
        entity.setRenewalNoticeSent(false);
        contractMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public ContractVO getById(Long id) {
        ContractEntity entity = contractMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("合同不存在");
        }
        return toVO(entity);
    }

    @Override
    public IPage<ContractVO> page(int page, int size) {
        Page<ContractEntity> p = new Page<>(page, size);
        IPage<ContractEntity> result = contractMapper.selectPage(p,
                new LambdaQueryWrapper<ContractEntity>().orderByDesc(ContractEntity::getCreatedAt));
        return result.convert(this::toVO);
    }

    @Override
    public List<RenewalReminderVO> getRenewalReminders() {
        List<ContractEntity> contracts = contractMapper.findRenewalReminders();
        List<RenewalReminderVO> reminders = new ArrayList<>();
        for (ContractEntity c : contracts) {
            EnterpriseEntity ent = enterpriseMapper.selectById(c.getEnterpriseId());
            long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate());
            reminders.add(new RenewalReminderVO(
                    c.getId(), c.getContractNo(), c.getEnterpriseId(),
                    ent != null ? ent.getEnterpriseName() : "未知",
                    c.getEndDate(), c.getAmount(), daysUntil));
        }
        return reminders;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVO renew(Long id) {
        ContractEntity entity = contractMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("合同不存在");
        }
        // 续约：延长一年
        entity.setEndDate(entity.getEndDate().plusYears(1));
        entity.setRenewalNoticeSent(false);
        entity.setStatus("ACTIVE");
        contractMapper.updateById(entity);
        return toVO(entity);
    }

    private ContractVO toVO(ContractEntity e) {
        return new ContractVO(e.getId(), e.getEnterpriseId(), e.getAgencyId(),
                e.getContractNo(), e.getStartDate(), e.getEndDate(),
                e.getContractType(), e.getAmount(), e.getStatus(),
                e.getRenewalNoticeSent(), e.getCreatedAt());
    }
}
