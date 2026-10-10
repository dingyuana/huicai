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
/**
 * 类级事务（P102/M5b，2026-10-03）：走租户表的路径必须有事务，否则
 * TenantRlsInitializer 切面不触发、app.enterprise_id 设不进去，
 * 应用以非超级用户连接时 RLS 会把本企业数据也过滤掉（读 0 行）。
 * 方法级 @Transactional 优先级更高，不受此影响。
 */
@Transactional
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
        // V176 起本表不开 RLS（enterprise_id 是「被服务客户」而非「归属租户」），
        // 故**必须**在此按 agency_id 收敛，否则任何登录用户都能查别人的派工。
        // AGENTS §4.5 第 43 条：整表豁免后必须在每条路径自己补隔离。
        requireSameAgency(agencyUserId);

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

    /**
     * 断言目标代理用户属于当前操作者所属的代理公司。
     *
     * <p><b>为什么必须有</b>：V176 撤掉了本表的 RLS（其 {@code enterprise_id}
     * 语义是「被服务客户」，用 {@code = app.enterprise_id} 过滤会与「代理跨客户派工」
     * 直接矛盾，实测导致 POST 500 且读路径静默丢行）。撤掉 DB 层兜底后，
     * 隔离责任落到应用层 —— 若此处不校验，任何登录用户都能按 id 遍历
     * {@code GET /assignments?agencyUserId=} 读出别家代理的派工名单。
     *
     * <p><b>超管豁免</b>：{@code SUPER_ADMIN} 本就跨代理（与
     * {@link #assign} 的口径保持一致），否则超管将无法运维。
     *
     * @throws BusinessException 目标用户不存在，或不属于当前代理公司
     */
    private void requireSameAgency(Long agencyUserId) {
        if ("SUPER_ADMIN".equals(SecurityUtils.getCurrentUserType())) {
            return;
        }
        Long currentAgencyId = SecurityUtils.getCurrentAgencyId();
        if (currentAgencyId == null) {
            // fail-loud：agencyId 缺失说明登录上下文构造有问题（历史上 LoginUser
            // 的 2 参构造器把它硬编码成 null，见该类注释）。此时若静默按
            // 「无权」处理，会变成**所有代理管理员被锁在门外**且报错文案
            // 指向「权限不足」，极难定位（§4.3 第 10 条「静默失败」形态）。
            throw new BusinessException(500,
                    "登录上下文缺少代理公司信息(agencyId)，无法校验派工归属；"
                            + "请检查 LoginUser 构造是否传入 t_user.agency_id");
        }
        AgencyUserEntity target = agencyUserMapper.selectById(agencyUserId);
        if (target == null || target.getDeleted() == 1) {
            throw BusinessException.notFound("代理用户不存在");
        }
        if (!currentAgencyId.equals(target.getAgencyId())) {
            throw BusinessException.forbidden("无权查看其他代理公司的派工记录");
        }
    }
}
