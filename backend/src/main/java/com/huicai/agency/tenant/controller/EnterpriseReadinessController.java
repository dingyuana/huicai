package com.huicai.agency.tenant.controller;

import com.huicai.agency.security.AgencyEnterpriseRoleGuard;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.base.system.service.VoucherTypeService;
import com.huicai.sme.cash.mapper.BankAccountMapper;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.response.R;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SPEC-P129 §1② / §3：企业「主数据就绪度」探测。
 *
 * <p><b>刻意不落库</b>：就绪度是由「有没有」算出的<b>派生事实</b>。若固化成列或状态，
 * 就会与实际配置漂移 —— 用户删掉最后一个银行账号，列里仍是 1，永远显示「已配置」。
 * P128 的 {@code enterprise_id=0} 死数据正是这类固化的恶果。
 *
 * <p><b>探测失败按「未配置」返回</b>，不抛异常：就绪度面板是<b>引导</b>，
 * 探不通时让它显示「未配置」比让整块报错更有用（但仍会保留真实配置项的存在性）。
 */
@RestController
@RequestMapping("/api/v1/enterprise/readiness")
public class EnterpriseReadinessController {

    private final EnterpriseMapper enterpriseMapper;
    private final BankAccountMapper bankAccountMapper;
    private final VoucherTypeService voucherTypeService;
    private final AgencyEnterpriseRoleGuard roleGuard;

    public EnterpriseReadinessController(EnterpriseMapper enterpriseMapper,
                                         BankAccountMapper bankAccountMapper,
                                         VoucherTypeService voucherTypeService,
                                         AgencyEnterpriseRoleGuard roleGuard) {
        this.enterpriseMapper = enterpriseMapper;
        this.bankAccountMapper = bankAccountMapper;
        this.voucherTypeService = voucherTypeService;
        this.roleGuard = roleGuard;
    }

    @GetMapping
    public R<Map<String, Object>> readiness(@RequestParam Long enterpriseId) {
        // 可见性校验：与其它客户企业端点同一口径（P120）
        roleGuard.requireVisibleEnterprise(enterpriseId);

        var enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || Integer.valueOf(1).equals(enterprise.getDeleted())) {
            throw BusinessException.notFound("企业不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enterpriseId", enterpriseId);
        result.put("enterpriseName", enterprise.getEnterpriseName());
        result.put("basicInfoDone", true);
        result.put("bankAccount", hasBankAccount(enterpriseId));
        result.put("voucherType", hasVoucherType(enterpriseId));
        return R.ok(result);
    }

    /** 该企业是否有启用的银行账号 */
    private boolean hasBankAccount(Long enterpriseId) {
        try {
            Long n = bankAccountMapper.selectCount(Wrappers.<BankAccountEntity>
                    lambdaQuery()
                    .eq(BankAccountEntity::getEnterpriseId, enterpriseId)
                    .eq(BankAccountEntity::getDeleted, 0));
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 该企业是否已配置凭证类型。
     *
     * <p>🔴 这里<b>不能</b>直接调 {@code requireByCode}：它按<b>当前上下文企业</b>解析，
     * 而本端点的入参是任意可见企业。要按目标企业判定，只能读表。
     */
    private boolean hasVoucherType(Long enterpriseId) {
        try {
            Long n = voucherTypeService.count(Wrappers.<VoucherTypeEntity>lambdaQuery()
                    .eq(VoucherTypeEntity::getEnterpriseId, enterpriseId)
                    .eq(VoucherTypeEntity::getDeleted, 0));
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
