package com.huicai.agency.client.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.agency.client.dto.ContractCreateDTO;
import com.huicai.agency.client.dto.ContractVO;
import com.huicai.agency.client.dto.RenewalReminderVO;
import com.huicai.agency.client.service.ContractService;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.response.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agency/contracts")
@RequiredArgsConstructor
public class ClientController {

    private final ContractService contractService;

    /**
     * 🔴 <b>V177 同批补齐：五个端点（含只读的）均须角色校验</b>
     *
     * <p>此前本控制器<b>完全无鉴权</b>，而 {@code SecurityConfig} 对它只要求
     * {@code anyRequest().authenticated()} ⇒ 任何登录用户都能
     * {@code GET /contracts/page} 遍历读出<b>全部客户</b>的合同金额与到期日。
     * 此前 RLS 恰好掩盖了读路径（但代价是代理看不到自己客户的续费提醒）。
     *
     * <p>V177 撤掉 RLS 后，若不补这道，等于<b>用一个洞换另一个洞</b>
     * （AGENTS §4.5 第 43 条）。服务层的 {@code agency_id} 归属校验管「能看哪一家」，
     * 本处管「能不能进合同功能」，两者正交、缺一即漏。
     */
    @PostMapping
    public R<ContractVO> create(@Valid @RequestBody ContractCreateDTO dto) {
        requireContractAccess();
        return R.ok(contractService.create(dto));
    }

    @GetMapping("/{id}")
    public R<ContractVO> getById(@PathVariable Long id) {
        requireContractAccess();
        return R.ok(contractService.getById(id));
    }

    @GetMapping("/page")
    public R<IPage<ContractVO>> page(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        requireContractAccess();
        return R.ok(contractService.page(page, size));
    }
    @GetMapping("/renewal-reminders")
    public R<List<RenewalReminderVO>> getRenewalReminders() {
        requireContractAccess();
        return R.ok(contractService.getRenewalReminders());
    }

    @PutMapping("/{id}/renew")
    public R<ContractVO> renew(@PathVariable Long id) {
        requireContractAccess();
        return R.ok(contractService.renew(id));
    }

    /** 只有代理管理员 / 超管可操作客户合同。 */
    private void requireContractAccess() {
        boolean allowed = "AGENCY_ADMIN".equals(SecurityUtils.getCurrentAgencyRole())
                || "SUPER_ADMIN".equals(SecurityUtils.getCurrentUserType());
        if (!allowed) {
            throw BusinessException.forbidden("无权访问客户合同");
        }
    }
}
