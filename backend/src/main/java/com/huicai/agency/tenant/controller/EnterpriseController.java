package com.huicai.agency.tenant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.agency.tenant.dto.EnterpriseCreateDTO;
import com.huicai.agency.tenant.dto.EnterpriseVO;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.AgencyEnterpriseMapper;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.tenant.service.EnterpriseService;
import com.huicai.agency.tenant.vo.EnterpriseSwitchVO;
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

    // ========== 切换接口 (Sprint 2) ==========

    @PostMapping("/api/v1/enterprise/switch")
    public R<EnterpriseSwitchVO> switchEnterprise(@RequestParam Long enterpriseId) {
        String userType = SecurityUtils.getCurrentUserType();
        if (!"AGENCY".equals(userType)) {
            throw BusinessException.forbidden("无权切换企业");
        }

        Long agencyId = SecurityUtils.getCurrentAgencyId();
        if (agencyId == null) {
            throw BusinessException.forbidden("无权切换企业");
        }

        boolean bound = agencyEnterpriseMapper.getEnterpriseIdsByAgencyId(agencyId)
                .contains(enterpriseId);
        if (!bound) {
            throw BusinessException.forbidden("无权切换企业");
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

    @PostMapping("/api/v1/agency/enterprises/{id}/unbind")
    public R<Void> unbind(@PathVariable Long id, @RequestParam Long agencyId) {
        enterpriseService.unbind(id, agencyId);
        return R.ok();
    }
}
