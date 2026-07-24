package com.huicai.agency.tenant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.agency.tenant.dto.EnterpriseCreateDTO;
import com.huicai.agency.tenant.dto.EnterpriseVO;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.AgencyEnterpriseMapper;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.tenant.service.EnterpriseService;
import com.huicai.agency.tenant.service.EnterpriseStateMachineService;
import com.huicai.agency.tenant.vo.EnterpriseSwitchVO;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.response.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class EnterpriseController {

    private final AgencyEnterpriseMapper agencyEnterpriseMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final EnterpriseService enterpriseService;
    private final EnterpriseStateMachineService stateMachineService;
    private final AgencyUserMapper agencyUserMapper;
    private final AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;

    // ========== 切换接口 (Sprint 2) ==========

    @PostMapping("/api/v1/enterprise/switch")
    public R<EnterpriseSwitchVO> switchEnterprise(@RequestParam Long enterpriseId) {
        String userType = SecurityUtils.getCurrentUserType();

        // SUPER_ADMIN 可切换到任意企业，无需校验绑定关系
        if ("SUPER_ADMIN".equals(userType)) {
            EnterpriseEntity enterprise = enterpriseMapper.selectById(enterpriseId);
            if (enterprise == null || enterprise.getDeleted() == 1) {
                throw BusinessException.notFound("企业不存在");
            }
            EnterpriseContextHolder.set(enterpriseId);
            return R.ok(new EnterpriseSwitchVO(
                    enterprise.getId(), enterprise.getEnterpriseName(), enterprise.getSeedDataDone()));
        }

        if (!"AGENCY".equals(userType)) {
            throw BusinessException.forbidden("无权切换企业");
        }

        Long agencyId = SecurityUtils.getCurrentAgencyId();
        if (agencyId == null) {
            throw BusinessException.forbidden("无权切换企业");
        }

        // V2.0: 按 agencyRole 分流校验
        String agencyRole = SecurityUtils.getCurrentAgencyRole();
        boolean allowed;
        if ("AGENCY_ADMIN".equals(agencyRole) || "REVIEWER".equals(agencyRole)) {
            // 经理和审核员：校验 t_agency_enterprise 绑定
            allowed = agencyEnterpriseMapper.getEnterpriseIdsByAgencyId(agencyId)
                    .contains(enterpriseId);
        } else {
            // 会计和助理：校验 t_agency_user_enterprise 分配
            Long userId = SecurityUtils.getCurrentUserId();
            AgencyUserEntity agencyUser = agencyUserMapper.selectOne(
                    new LambdaQueryWrapper<AgencyUserEntity>()
                            .eq(AgencyUserEntity::getUserId, userId)
                            .eq(AgencyUserEntity::getDeleted, 0));
            if (agencyUser == null) {
                throw BusinessException.forbidden("无权切换企业");
            }
            allowed = agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(agencyUser.getId())
                    .contains(enterpriseId);
        }

        if (!allowed) {
            throw BusinessException.forbidden("您未被分配该客户企业，请联系经理");
        }

        EnterpriseEntity enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || enterprise.getDeleted() == 1) {
            throw BusinessException.notFound("企业不存在");
        }

        EnterpriseContextHolder.set(enterpriseId);

        return R.ok(new EnterpriseSwitchVO(
                enterprise.getId(),
                enterprise.getEnterpriseName(),
                enterprise.getSeedDataDone()
        ));
    }

    // ========== 客户企业 CRUD (Sprint 3) ==========

    @PostMapping("/api/v1/agency/enterprises")
    public R<EnterpriseVO> create(@Valid @RequestBody EnterpriseCreateDTO dto) {
        return R.ok(enterpriseService.create(dto));
    }

    @PutMapping("/api/v1/agency/enterprises/{id}")
    public R<EnterpriseVO> update(@PathVariable Long id, @RequestBody EnterpriseCreateDTO dto) {
        return R.ok(enterpriseService.update(id, dto));
    }

    @GetMapping("/api/v1/agency/enterprises/{id}")
    public R<EnterpriseVO> getById(@PathVariable Long id) {
        return R.ok(enterpriseService.getById(id));
    }

    @GetMapping("/api/v1/agency/enterprises/page")
    public R<IPage<EnterpriseVO>> page(@RequestParam Long agencyId,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return R.ok(enterpriseService.pageByAgency(agencyId, page, size));
    }

    @DeleteMapping("/api/v1/agency/enterprises/{id}")
    public R<Void> delete(@PathVariable Long id) {
        enterpriseService.delete(id);
        return R.ok();
    }

    @PostMapping("/api/v1/agency/enterprises/{id}/bind")
    public R<Void> bind(@PathVariable Long id, @RequestParam Long agencyId) {
        enterpriseService.bind(id, agencyId);
        return R.ok();
    }

    @PostMapping("/api/v1/agency/enterprises/{id}/activate")
    public R<EnterpriseVO> activate(@PathVariable Long id) {
        EnterpriseEntity entity = stateMachineService.activateEnterprise(id);
        // 重新查询以获取 cloneSeedData 后更新的 seedDataDone
        EnterpriseEntity refreshed = enterpriseMapper.selectById(id);
        EnterpriseVO vo = new EnterpriseVO();
        vo.setId(refreshed.getId());
        vo.setEnterpriseCode(refreshed.getEnterpriseCode());
        vo.setEnterpriseName(refreshed.getEnterpriseName());
        vo.setTaxId(refreshed.getTaxId());
        vo.setMode(refreshed.getMode());
        vo.setAgencyId(refreshed.getAgencyId());
        vo.setStatus(refreshed.getStatus());
        vo.setSeedDataDone(refreshed.getSeedDataDone());
        vo.setCreatedAt(refreshed.getCreatedAt());
        return R.ok(vo);
    }

    @PostMapping("/api/v1/agency/enterprises/{id}/suspend")
    public R<EnterpriseVO> suspend(@PathVariable Long id, @RequestParam(required = false, defaultValue = "手动暂停") String reason) {
        EnterpriseEntity entity = stateMachineService.suspendEnterprise(id, reason);
        EnterpriseVO vo = new EnterpriseVO();
        vo.setId(entity.getId());
        vo.setEnterpriseCode(entity.getEnterpriseCode());
        vo.setEnterpriseName(entity.getEnterpriseName());
        vo.setTaxId(entity.getTaxId());
        vo.setMode(entity.getMode());
        vo.setAgencyId(entity.getAgencyId());
        vo.setStatus(entity.getStatus());
        vo.setSeedDataDone(entity.getSeedDataDone());
        vo.setCreatedAt(entity.getCreatedAt());
        return R.ok(vo);
    }

    @PostMapping("/api/v1/agency/enterprises/{id}/unbind")
    public R<Void> unbind(@PathVariable Long id, @RequestParam Long agencyId) {
        enterpriseService.unbind(id, agencyId);
        return R.ok();
    }
}
