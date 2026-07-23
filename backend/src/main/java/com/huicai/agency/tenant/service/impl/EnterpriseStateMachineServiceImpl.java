package com.huicai.agency.tenant.service.impl;

import com.huicai.agency.tenant.constant.EnterpriseStatus;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.tenant.service.EnterpriseStateMachineService;
import com.huicai.agency.tenant.service.SeedDataService;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseStateMachineServiceImpl implements EnterpriseStateMachineService {

    private final EnterpriseMapper enterpriseMapper;
    private final SeedDataService seedDataService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnterpriseEntity activateEnterprise(Long id) {
        EnterpriseEntity entity = enterpriseMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("企业不存在");
        }
        if (!"PENDING".equals(entity.getStatus())) {
            throw BusinessException.badRequest("只有待激活状态的企业才能激活，当前状态：" + entity.getStatus());
        }

        entity.setStatus(EnterpriseStatus.ACTIVE.getCode());
        enterpriseMapper.updateById(entity);

        // 触发种子数据克隆
        seedDataService.cloneSeedData(id);

        log.info("Enterprise {} activated, seed data cloned", id);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnterpriseEntity suspendEnterprise(Long id, String reason) {
        EnterpriseEntity entity = enterpriseMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("企业不存在");
        }
        if (!"ACTIVE".equals(entity.getStatus())) {
            throw BusinessException.badRequest("只有已激活状态的企业才能暂停，当前状态：" + entity.getStatus());
        }

        entity.setStatus(EnterpriseStatus.SUSPENDED.getCode());
        enterpriseMapper.updateById(entity);

        log.info("Enterprise {} suspended, reason: {}", id, reason);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnterpriseEntity reactivateEnterprise(Long id) {
        EnterpriseEntity entity = enterpriseMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("企业不存在");
        }
        if (!"SUSPENDED".equals(entity.getStatus())) {
            throw BusinessException.badRequest("只有已暂停状态的企业才能重新激活，当前状态：" + entity.getStatus());
        }

        entity.setStatus(EnterpriseStatus.ACTIVE.getCode());
        enterpriseMapper.updateById(entity);

        log.info("Enterprise {} reactivated", id);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnterpriseEntity terminateEnterprise(Long id) {
        EnterpriseEntity entity = enterpriseMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("企业不存在");
        }
        if (!"SUSPENDED".equals(entity.getStatus())) {
            throw BusinessException.badRequest("只有已暂停状态的企业才能终止，当前状态：" + entity.getStatus());
        }

        entity.setStatus(EnterpriseStatus.TERMINATED.getCode());
        enterpriseMapper.updateById(entity);

        log.info("Enterprise {} terminated", id);
        return entity;
    }
}
