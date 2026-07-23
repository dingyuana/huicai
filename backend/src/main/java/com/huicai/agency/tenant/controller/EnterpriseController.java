package com.huicai.agency.tenant.controller;

import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.AgencyEnterpriseMapper;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.tenant.vo.EnterpriseSwitchVO;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.response.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enterprise")
@RequiredArgsConstructor
public class EnterpriseController {

    private final AgencyEnterpriseMapper agencyEnterpriseMapper;
    private final EnterpriseMapper enterpriseMapper;

    /**
     * 切换企业 — AGENCY 用户选择要操作的客户企业
     */
    @PostMapping("/switch")
    public R<EnterpriseSwitchVO> switchEnterprise(@RequestParam Long enterpriseId) {
        String userType = SecurityUtils.getCurrentUserType();
        if (!"AGENCY".equals(userType)) {
            throw BusinessException.forbidden("无权切换企业");
        }

        Long agencyId = SecurityUtils.getCurrentAgencyId();
        if (agencyId == null) {
            throw BusinessException.forbidden("无权切换企业");
        }

        // 校验绑定关系
        boolean bound = agencyEnterpriseMapper.getEnterpriseIdsByAgencyId(agencyId)
                .contains(enterpriseId);
        if (!bound) {
            throw BusinessException.forbidden("无权切换企业");
        }

        EnterpriseEntity enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || enterprise.getDeleted() == 1) {
            throw BusinessException.notFound("企业不存在");
        }

        // 更新上下文
        EnterpriseContextHolder.set(enterpriseId);

        return R.ok(new EnterpriseSwitchVO(
                enterprise.getId(),
                enterprise.getEnterpriseName(),
                enterprise.getSeedDataDone()
        ));
    }
}
