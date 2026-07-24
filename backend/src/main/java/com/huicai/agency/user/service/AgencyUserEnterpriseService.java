package com.huicai.agency.user.service;

import com.huicai.agency.user.dto.AssignmentCreateDTO;
import com.huicai.agency.user.dto.AssignmentVO;

import java.util.List;

public interface AgencyUserEnterpriseService {

    void assign(AssignmentCreateDTO dto);

    void unassign(Long assignmentId);

    List<AssignmentVO> listByAgencyUserId(Long agencyUserId);

    List<Long> getEnterpriseIdsByAgencyUserId(Long agencyUserId);
}
