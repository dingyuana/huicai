package com.huicai.agency.tenant.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.agency.tenant.dto.AgencyCreateDTO;
import com.huicai.agency.tenant.dto.AgencyUpdateDTO;
import com.huicai.agency.tenant.dto.AgencyVO;

public interface AgencyService {
    AgencyVO create(AgencyCreateDTO dto);
    AgencyVO update(Long id, AgencyUpdateDTO dto);
    AgencyVO getById(Long id);
    IPage<AgencyVO> page(int page, int size);
    void delete(Long id);
}
