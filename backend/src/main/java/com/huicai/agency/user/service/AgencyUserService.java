package com.huicai.agency.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.agency.user.dto.AgencyUserCreateDTO;
import com.huicai.agency.user.dto.AgencyUserVO;

import java.util.List;

public interface AgencyUserService {

    IPage<AgencyUserVO> page(int page, int size, Long agencyId);

    List<AgencyUserVO> list(Long agencyId);

    AgencyUserVO create(AgencyUserCreateDTO dto);

    AgencyUserVO getById(Long id);

    void suspend(Long id);

    void reactivate(Long id);

    void terminate(Long id);
}
