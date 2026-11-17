package com.huicai.agency.tenant.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.agency.tenant.dto.EnterpriseCreateDTO;
import com.huicai.agency.tenant.dto.EnterpriseVO;

public interface EnterpriseService {
    EnterpriseVO create(EnterpriseCreateDTO dto);
    EnterpriseVO update(Long id, EnterpriseCreateDTO dto);
    EnterpriseVO getById(Long id);
    IPage<EnterpriseVO> pageByAgency(Long agencyId, int page, int size);

    /**
     * 按可见企业 id 收敛的分页（SPEC-P120）。
     *
     * <p>会计/助理只能看到派工给自己的企业，故分页必须以 {@code visibleIds} 收口，
     * 不能只按 {@code agencyId} 过滤。
     *
     * @param visibleIds 调用方有权查看的企业 id；为空表示无任何可见企业
     */
    IPage<EnterpriseVO> pageByAgency(Long agencyId, int page, int size,
                                     java.util.List<Long> visibleIds);
    void delete(Long id);
    void bind(Long enterpriseId, Long agencyId);
    void unbind(Long enterpriseId, Long agencyId);

    /**
     * P142 修复：获取企业当前期间信息（含 earliestUnclosedPeriod）。
     * 必须在事务内执行——{@code EnterpriseDataPermissionInterceptor} 依赖
     * {@code TransactionSynchronizationManager.isActualTransactionActive()} 来决定
     * 是否注入 {@code app.enterprise_id} GUC；无事务时 GUC 为 null，RLS 谓词
     * {@code enterprise_id = NULLIF(current_setting(...), '')::bigint} 恒 false ⇒
     * 所有租户表查询返回 0 行，{@code earliestUnclosedPeriod} 恒 null。
     */
    com.huicai.agency.tenant.vo.CurrentPeriodVO getCurrentPeriodInfo(Long enterpriseId);
}
