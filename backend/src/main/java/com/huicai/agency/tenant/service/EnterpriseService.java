package com.huicai.agency.tenant.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.agency.tenant.dto.EnterpriseCreateDTO;
import com.huicai.agency.tenant.dto.EnterpriseVO;

public interface EnterpriseService {
    EnterpriseVO create(EnterpriseCreateDTO dto);
    EnterpriseVO update(Long id, EnterpriseCreateDTO dto);
    EnterpriseVO getById(Long id);
    IPage<EnterpriseVO> pageByAgency(Long agencyId, int page, int size);
    void delete(Long id);
    void bind(Long enterpriseId, Long agencyId);
    void unbind(Long enterpriseId, Long agencyId);
}
