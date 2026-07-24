package com.huicai.agency.user.service.impl;

import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.user.dto.AssignmentCreateDTO;
import com.huicai.agency.user.dto.AssignmentVO;
import com.huicai.agency.user.entity.AgencyUserEnterpriseEntity;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.agency.user.service.AgencyUserEnterpriseService;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyUserEnterpriseServiceImpl implements AgencyUserEnterpriseService {

    private final AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;
    private final AgencyUserMapper agencyUserMapper;
    private final EnterpriseMapper enterpriseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(AssignmentCreateDTO dto) {
        // 校验操作者权限
        String currentRole = SecurityUtils.getCurrentAgencyRole();
        if (!"AGENCY_ADMIN".equals(currentRole) && !"SUPER_ADMIN".equals(SecurityUtils.getCurrentUserType())) {
            throw BusinessException.forbidden("无权分配客户");
        }

        // 校验目标用户存在且角色为 ACCOUNTANT 或 ASSISTANT
        AgencyUserEntity agencyUser = agencyUserMapper.selectById(dto.getAgencyUserId());
        if (agencyUser == null || agencyUser.getDeleted() == 1) {
            throw BusinessException.notFound("代理用户不存在");
        }
        if (!"ACCOUNTANT".equals(agencyUser.getAgencyRole()) && !"ASSISTANT".equals(agencyUser.getAgencyRole())) {
            throw BusinessException.badRequest("只能为会计或助理分配客户");
        }

        // 校验目标企业存在
        EnterpriseEntity enterprise = enterpriseMapper.selectById(dto.getEnterpriseId());
        if (enterprise == null || enterprise.getDeleted() == 1) {
            throw BusinessException.notFound("企业不存在");
        }

        // 校验跨代理公司分配
        if (!agencyUser.getAgencyId().equals(enterprise.getAgencyId())) {
            throw BusinessException.badRequest("不能跨代理公司分配客户");
        }

        // 校验是否已分配
        List<Long> existing = agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(dto.getAgencyUserId());
        if (existing.contains(dto.getEnterpriseId())) {
            throw BusinessException.badRequest("该客户已分配给此用户");
        }

        // 创建分配记录
        AgencyUserEnterpriseEntity assignment = new AgencyUserEnterpriseEntity();
        assignment.setAgencyUserId(dto.getAgencyUserId());
        assignment.setEnterpriseId(dto.getEnterpriseId());
        assignment.setAssignedBy(SecurityUtils.getCurrentUserId());
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setDeleted(0);
        agencyUserEnterpriseMapper.insert(assignment);

        log.info("Enterprise assigned: agencyUserId={}, enterpriseId={}, assignedBy={}",
                dto.getAgencyUserId(), dto.getEnterpriseId(), SecurityUtils.getCurrentUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unassign(Long assignmentId) {
        String currentRole = SecurityUtils.getCurrentAgencyRole();
        if (!"AGENCY_ADMIN".equals(currentRole) && !"SUPER_ADMIN".equals(SecurityUtils.getCurrentUserType())) {
            throw BusinessException.forbidden("无权取消分配");
        }

        AgencyUserEnterpriseEntity assignment = agencyUserEnterpriseMapper.selectById(assignmentId);
        if (assignment == null || assignment.getDeleted() == 1) {
            throw BusinessException.notFound("分配记录不存在");
        }

        assignment.setDeleted(1);
        assignment.setUnassignedBy(SecurityUtils.getCurrentUserId());
        assignment.setUnassignedAt(LocalDateTime.now());
        agencyUserEnterpriseMapper.updateById(assignment);

        log.info("Enterprise unassigned: assignmentId={}, unassignedBy={}",
                assignmentId, SecurityUtils.getCurrentUserId());
    }

    @Override
    public List<AssignmentVO> listByAgencyUserId(Long agencyUserId) {
        List<Long> enterpriseIds = agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(agencyUserId);
        List<AssignmentVO> result = new ArrayList<>();

        for (Long eid : enterpriseIds) {
            EnterpriseEntity enterprise = enterpriseMapper.selectById(eid);
            if (enterprise == null) continue;

            // 查找分配记录
            List<AgencyUserEnterpriseEntity> records = agencyUserEnterpriseMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgencyUserEnterpriseEntity>()
                            .eq(AgencyUserEnterpriseEntity::getAgencyUserId, agencyUserId)
                            .eq(AgencyUserEnterpriseEntity::getEnterpriseId, eid)
                            .eq(AgencyUserEnterpriseEntity::getDeleted, 0));
            if (records.isEmpty()) continue;

            AgencyUserEnterpriseEntity record = records.get(0);
            AssignmentVO vo = new AssignmentVO();
            vo.setId(record.getId());
            vo.setAgencyUserId(record.getAgencyUserId());
            vo.setEnterpriseId(record.getEnterpriseId());
            vo.setEnterpriseName(enterprise.getEnterpriseName());
            vo.setTaxId(enterprise.getTaxId());
            vo.setAssignedBy(record.getAssignedBy());
            vo.setAssignedAt(record.getAssignedAt());
            result.add(vo);
        }

        return result;
    }

    @Override
    public List<Long> getEnterpriseIdsByAgencyUserId(Long agencyUserId) {
        return agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(agencyUserId);
    }
}
