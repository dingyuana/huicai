package com.huicai.agency.tenant.controller;

import com.huicai.agency.security.AgencyEnterpriseRoleGuard;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.base.system.service.VoucherTypeService;
import com.huicai.sme.cash.mapper.BankAccountMapper;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.response.R;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.transaction.annotation.Transactional;
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
    // 🔴 事务必须在 Controller 上（SPEC-P128 §7.1 同型，SPEC-P153-A2 实测坐实）：
    //   t_bank_account/t_voucher_type 是 RLS 表。无事务 ⇒ GUC 恒 NULL ⇒
    //   count 恒 0；两个探测又各自 catch(Exception){false}，就绪度面板对
    //   <b>所有企业恒显「未配置」</b>（实测 ent1 实有 1 银行账号/4 凭证类型
    //   仍报 false/false）。 voucherTypeService.count 走 IService default 方法
    //   同样不建事务，故单给 mapper 侧加事务不够，整个方法都要在事务内。
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> readiness(@RequestParam Long enterpriseId) {
        // 可见性校验：与其它客户企业端点同一口径（P120）
        roleGuard.requireVisibleEnterprise(enterpriseId);

        var enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || Integer.valueOf(1).equals(enterprise.getDeleted())) {
            throw BusinessException.notFound("企业不存在");
        }

        // 🔴 就绪度探测的是「目标企业」的配置，而 RLS GUC 只按「当前上下文
        // 企业」设置（SPEC-P153-A2）：代账会计切在 A 客户时探测 B 客户，即使
        // 有事务，GUC=A ⇒ B 的 t_bank_account/t_voucher_type 行全被谓词滤掉，
        // 探测恒 false。探测期间把上下文切到目标企业（每条 SQL 都会经
        // EnterpriseDataPermissionInterceptor 按新上下文重设 GUC），finally 还原。
        Long previous = EnterpriseContextHolder.get();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            EnterpriseContextHolder.set(enterpriseId);
            result.put("enterpriseId", enterpriseId);
            result.put("enterpriseName", enterprise.getEnterpriseName());
            result.put("basicInfoDone", true);
            result.put("bankAccount", hasBankAccount(enterpriseId));
            result.put("voucherType", hasVoucherType(enterpriseId));
        } finally {
            if (previous != null) {
                EnterpriseContextHolder.set(previous);
            } else {
                EnterpriseContextHolder.clear();
            }
        }
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
