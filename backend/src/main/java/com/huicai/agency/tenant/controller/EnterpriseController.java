package com.huicai.agency.tenant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.agency.tenant.dto.EnterpriseCreateDTO;
import com.huicai.agency.tenant.dto.EnterpriseVO;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.AgencyEnterpriseMapper;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.tenant.service.EnterpriseService;
import com.huicai.agency.tenant.service.EnterpriseStateMachineService;
import com.huicai.agency.tenant.vo.CurrentPeriodVO;
import com.huicai.agency.tenant.vo.EnterpriseSwitchVO;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.security.AgencyEnterpriseRoleGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.response.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class EnterpriseController {

    private final AgencyEnterpriseMapper agencyEnterpriseMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final com.huicai.agency.tenant.service.SeedDataService seedDataService;
    private final EnterpriseService enterpriseService;
    private final EnterpriseStateMachineService stateMachineService;
    private final AgencyUserMapper agencyUserMapper;
    private final AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;
    private final PeriodMapper periodMapper;
    private final AgencyEnterpriseRoleGuard roleGuard;
    private final com.huicai.config.security.JwtProvider jwtProvider;
    private final com.huicai.base.system.mapper.UserRoleMapper userRoleMapper;
    private final com.huicai.base.system.service.EnterpriseSwitchAuditService switchAuditService;

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
            Long fromEnterpriseId = SecurityUtils.getCurrentEnterpriseId();
            EnterpriseContextHolder.set(enterpriseId);
            recordSwitchAudit(fromEnterpriseId, enterpriseId);
            return R.ok(buildSwitchVO(enterprise));
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

        Long fromEnterpriseId = SecurityUtils.getCurrentEnterpriseId();
        EnterpriseContextHolder.set(enterpriseId);
        recordSwitchAudit(fromEnterpriseId, enterpriseId);

        return R.ok(buildSwitchVO(enterprise));
    }

    // ========== 客户企业 CRUD (Sprint 3) ==========

    @GetMapping("/api/v1/enterprise/current-period")
    public R<CurrentPeriodVO> currentPeriod() {
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId == null) {
            throw BusinessException.badRequest("尚未切换企业, 无法获取默认期间");
        }
        // SPEC-P120：会计/助理不得读取未派给自己的企业期间
        roleGuard.requireVisibleEnterprise(enterpriseId);

        EnterpriseEntity enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || enterprise.getDeleted() == 1) {
            throw BusinessException.notFound("企业不存在");
        }

        String startPeriod = enterprise.getStartPeriod();
        CurrentPeriodVO vo = new CurrentPeriodVO();
        vo.setStartPeriod(startPeriod);
        vo.setEarliestUnclosedPeriod(enterpriseMapper.selectEarliestUnclosedPeriod(enterpriseId));
        vo.setLatestClosedPeriod(enterpriseMapper.selectLatestClosedPeriod(enterpriseId));

        if (startPeriod != null) {
            String hasDataPeriod = enterpriseMapper.selectLatestPeriodWithData(enterpriseId);
            if (hasDataPeriod != null && hasDataPeriod.compareTo(startPeriod) >= 0) {
                vo.setHasDataPeriod(hasDataPeriod);
                vo.setCurrentPeriod(hasDataPeriod);
            } else {
                vo.setCurrentPeriod(startPeriod);
            }
            return R.ok(vo);
        }

        // 存量企业（start_period 为空）：返回企业期间列表中最新的一个，与改造前行为一致
        //
        // ⚠️ SPEC-P120：原实现**未按企业过滤**，取的是全库最新期间 ——
        // 租户隔离靠 EnterpriseDataPermissionInterceptor 注入，但那层在
        // 多租户表白名单里对本表不一定生效（§4.5 第 45 条 DIR-004 同型）。
        PeriodEntity latest = periodMapper.selectOne(
                new LambdaQueryWrapper<PeriodEntity>()
                        .eq(PeriodEntity::getEnterpriseId, enterpriseId)
                        .orderByDesc(PeriodEntity::getPeriodCode)
                        .last("LIMIT 1"));
        vo.setCurrentPeriod(latest == null ? null : latest.getPeriodCode());
        return R.ok(vo);
    }

    @PostMapping("/api/v1/agency/enterprises")
    public R<EnterpriseVO> create(@Valid @RequestBody EnterpriseCreateDTO dto) {
        roleGuard.requireAgencyAdmin("新建客户企业");
        return R.ok(enterpriseService.create(dto));
    }

    @PutMapping("/api/v1/agency/enterprises/{id}")
    public R<EnterpriseVO> update(@PathVariable Long id, @RequestBody EnterpriseCreateDTO dto) {
        roleGuard.requireAgencyAdmin("修改客户企业");
        roleGuard.requireVisibleEnterprise(id);
        return R.ok(enterpriseService.update(id, dto));
    }

    @GetMapping("/api/v1/agency/enterprises/{id}")
    public R<EnterpriseVO> getById(@PathVariable Long id) {
        roleGuard.requireVisibleEnterprise(id);
        return R.ok(enterpriseService.getById(id));
    }

    /**
     * 客户企业列表 —— <b>不接收调用方自填的 agencyId</b>。
     *
     * <p><b>SPEC-P120</b>：原签名是 {@code page(@RequestParam Long agencyId)}，
     * 会计传任意 {@code agencyId} 即可列出<b>任意代理</b>的全部客户企业。
     * 现改为由 {@link AgencyEnterpriseRoleGuard} 按角色收敛可见范围：
     * 经理/审核员见本代理全部，会计/助理仅见派给自己的。
     */
    @GetMapping("/api/v1/agency/enterprises/page")
    public R<IPage<EnterpriseVO>> page(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        List<Long> visibleIds = roleGuard.resolveVisibleEnterpriseIds();
        if (visibleIds.isEmpty()) {
            return R.ok(new Page<>());
        }
        return R.ok(enterpriseService.pageByAgency(
                SecurityUtils.getCurrentAgencyId(), page, size, visibleIds));
    }

    @DeleteMapping("/api/v1/agency/enterprises/{id}")
    public R<Void> delete(@PathVariable Long id) {
        roleGuard.requireAgencyAdmin("删除客户企业");
        roleGuard.requireVisibleEnterprise(id);
        enterpriseService.delete(id);
        return R.ok();
    }

    /**
     * SPEC-P124 切片3：切换企业时**重签 access 与 refresh 双 token**。
     *
     * <p><b>为什么必须重签</b>：企业上下文此前只存在于前端 localStorage 与
     * {@code X-Enterprise-Id} 请求头，服务端 token 里的 {@code enterpriseId}
     * 永远是登录时的值。二者一旦废止（切片5），切换就会在 access token 过期、
     * 走 {@code /auth/refresh} 续期时被<b>静默退回旧企业</b>。
     *
     * <p><b>为何两个都要换</b>：refresh token 同样带 {@code enterpriseId}，
     * 只换 access 等于把回退推迟到下一次续期。
     *
     * <p>授权判定（绑定关系 / 派工关系校验）仍在原处，此处只负责签发。
     */
    /**
     * SPEC-P124 切片5：切换审计在**切换发生处**记录。
     *
     * <p>此前审计写在 {@code JwtAuthenticationFilter} 的 {@code X-Enterprise-Id}
     * 覆盖分支里，即「每请求记一次」，且切换端点本身不记。请求头一旦废止，
     * 审计会<b>整体丢失</b> —— 故必须先把它搬到真正的切换发生处。
     *
     * <p>语义也更正确：从「每请求都记一条」变为「切换时才记」，噪音大幅降低。
     */
    private void recordSwitchAudit(Long fromEnterpriseId, Long toEnterpriseId) {
        if (toEnterpriseId == null || toEnterpriseId.equals(fromEnterpriseId)) {
            return;
        }
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            switchAuditService.recordEnterpriseSwitch(userId, fromEnterpriseId, toEnterpriseId);
        }
    }

    private EnterpriseSwitchVO buildSwitchVO(EnterpriseEntity enterprise) {
        Long userId = SecurityUtils.getCurrentUserId();
        String username = SecurityUtils.getCurrentUsername();
        String userType = SecurityUtils.getCurrentUserType();
        String agencyRole = SecurityUtils.getCurrentAgencyRole();
        Long agencyId = SecurityUtils.getCurrentAgencyId();
        List<String> roles = userRoleMapper.getRoleIdsByUserId(userId).stream()
                .map(String::valueOf).collect(Collectors.toList());

        String token = jwtProvider.generateAccessToken(username, userId, roles,
                enterprise.getId(), agencyId, userType, agencyRole);
        String refreshToken = jwtProvider.generateRefreshToken(username, userId, roles,
                enterprise.getId(), agencyId, userType, agencyRole);

        return new EnterpriseSwitchVO(enterprise.getId(), enterprise.getEnterpriseName(),
                enterprise.getSeedDataDone(), enterprise.getStartPeriod(), token, refreshToken);
    }

    @PostMapping("/api/v1/agency/enterprises/{id}/bind")
    public R<Void> bind(@PathVariable Long id, @RequestParam Long agencyId) {
        roleGuard.requireAgencyAdmin("绑定客户企业");
        enterpriseService.bind(id, agencyId);
        return R.ok();
    }

    /**
     * SPEC-P126：强制重跑客户账套的种子数据克隆。
     *
     * <p><b>为什么需要这个入口</b>：修复前「复制 0 行」也会被标记为已播种，
     * 且已播种即短路跳过 ⇒ 一旦克隆失败，该企业<b>永远无法自愈</b>。
     * 本入口允许跳过该短路重跑；克隆本身幂等（唯一键去重），可安全重复调用。
     *
     * <p>限代理经理 —— 它会写入客户账套的标准数据。
     */
    @PostMapping("/api/v1/agency/enterprises/{id}/reseed")
    public R<Map<String, Object>> reseed(@PathVariable Long id) {
        roleGuard.requireAgencyAdmin("重跑客户账套种子数据");
        roleGuard.requireVisibleEnterprise(id);
        boolean done = seedDataService.cloneSeedData(id, true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enterpriseId", id);
        result.put("seeded", done);
        result.put("seedDataDone", enterpriseMapper.selectById(id).getSeedDataDone());
        return R.ok(result);
    }

    @PostMapping("/api/v1/agency/enterprises/{id}/activate")
    public R<EnterpriseVO> activate(@PathVariable Long id) {
        roleGuard.requireAgencyAdmin("启用客户账套");
        roleGuard.requireVisibleEnterprise(id);
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
        roleGuard.requireAgencyAdmin("停用客户账套");
        roleGuard.requireVisibleEnterprise(id);
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
        roleGuard.requireAgencyAdmin("解绑客户企业");
        enterpriseService.unbind(id, agencyId);
        return R.ok();
    }
}
