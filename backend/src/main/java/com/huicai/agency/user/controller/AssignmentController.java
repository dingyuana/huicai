package com.huicai.agency.user.controller;

import com.huicai.agency.user.dto.AssignmentCreateDTO;
import com.huicai.agency.user.dto.AssignmentVO;
import com.huicai.agency.user.service.AgencyUserEnterpriseService;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.response.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agency/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AgencyUserEnterpriseService agencyUserEnterpriseService;

    /**
     * 🔴 <b>读端点也必须有角色校验（V176 同批补齐）</b>
     *
     * <p>此前本控制器三个端点均<b>无任何鉴权</b>，而
     * {@code SecurityConfig} 对它们只要求 {@code anyRequest().authenticated()}
     * ⇒ 任何登录用户都能 {@code GET /assignments?agencyUserId=任意值}
     * 读出别家代理的派工名单。
     *
     * <p><b>为什么此前没被 RLS 挡住</b>：本表曾开 RLS，但谓词是
     * {@code enterprise_id = app.enterprise_id}，对「列出某会计的全部派工」
     * 这类查询只会<b>静默过滤</b>（不报错、少返回几行），不构成有效鉴权 ——
     * 且该谓词本身与业务语义矛盾，V176 已撤除。撤除后应用层必须自补。
     *
     * <p>服务层 {@code listByAgencyUserId} 另有 {@code agency_id} 归属校验，
     * 与本处角色校验构成两道：<b>角色</b>管「能不能进这个功能」，
     * <b>归属</b>管「能看哪一家」。缺任何一道都会漏（AGENTS §4.5 第 43 条）。
     */
    @PostMapping
    public R<Void> assign(@Valid @RequestBody AssignmentCreateDTO dto) {
        requireDispatcher();
        agencyUserEnterpriseService.assign(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> unassign(@PathVariable Long id) {
        requireDispatcher();
        agencyUserEnterpriseService.unassign(id);
        return R.ok();
    }

    @GetMapping
    public R<List<AssignmentVO>> listByAgencyUserId(@RequestParam Long agencyUserId) {
        requireDispatcher();
        return R.ok(agencyUserEnterpriseService.listByAgencyUserId(agencyUserId));
    }

    /**
     * 只有代理管理员 / 超管可操作派工。
     *
     * <p>口径与 {@code AgencyUserEnterpriseServiceImpl#assign} 保持一致 ——
     * 若两处不同，会出现「端点放行但服务层拒绝」或反之的错位，
     * 而错位方向的错误只在其中一侧被改动时才暴露。
     */
    private void requireDispatcher() {
        boolean dispatcher = "AGENCY_ADMIN".equals(SecurityUtils.getCurrentAgencyRole())
                || "SUPER_ADMIN".equals(SecurityUtils.getCurrentUserType());
        if (!dispatcher) {
            throw BusinessException.forbidden("无权访问客户派工");
        }
    }
}