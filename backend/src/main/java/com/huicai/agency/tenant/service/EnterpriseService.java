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
}
