package com.huicai.agency.tenant.service;

import com.huicai.agency.tenant.entity.EnterpriseEntity;

public interface EnterpriseStateMachineService {

    /**
     * 激活企业：PENDING → ACTIVE，触发种子数据克隆
     */
    EnterpriseEntity activateEnterprise(Long id);

    /**
     * 暂停企业：ACTIVE → SUSPENDED
     */
    EnterpriseEntity suspendEnterprise(Long id, String reason);

    /**
     * 重新激活：SUSPENDED → ACTIVE
     */
    EnterpriseEntity reactivateEnterprise(Long id);

    /**
     * 终止企业：SUSPENDED → TERMINATED（终态）
     */
    EnterpriseEntity terminateEnterprise(Long id);
}
