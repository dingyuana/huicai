package com.huicai.sme.cash.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.sme.cash.service.BankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.huicai.sme.cash.vo.BankAccountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.huicai.sme.cash.dto.BankAccountSaveDTO;
import jakarta.validation.Valid;

@Tag(name = "银行账户")
@RestController
@RequestMapping("/api/sme/cash/v1/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService service;
    private final com.huicai.agency.tenant.mapper.EnterpriseMapper enterpriseMapper;
    private final com.huicai.agency.security.AgencyEnterpriseRoleGuard roleGuard;

    @Operation(summary = "分页查询（可按企业过滤，SPEC-P129）")
    @GetMapping("/page")
    public R<IPage<BankAccountVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long enterpriseId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        // SPEC-P129 §1③：允许按企业收敛列表。
        // ⚠️ 必须先过可见集校验：否则「客户端自填 enterpriseId」就是一个可控的跨租户读入口
        //    （这正是本类 /all-enterprises 注释里已明确的边界）。
        //    不传时行为与改动前完全一致（AT-129-9 回归锁）。
        if (enterpriseId != null) {
            roleGuard.requireVisibleEnterprise(enterpriseId);
        }
        return R.ok(BankAccountVO.from(service.pageQuery(keyword, current, size, enterpriseId)));
    }

    /**
     * 系统管理员：查看<b>全部企业</b>的银行账户。
     *
     * <p><b>用途</b>：银行账户列表此前只返回当前企业的账户，且返回体不含企业信息，
     * 管理员无法判断一条账户属于哪家公司（实测 enterpriseId 为 null），
     * 因而无法跨公司核对与管理。
     *
     * <p><b>边界</b>：<b>只读</b>。新建/修改仍绑定当前企业上下文 ——
     * 刻意不提供「指定企业写入」，那等于开一个客户端可控的跨租户写入口，
     * 与 SPEC-P124 刚收敛掉的「多源权威」方向相反。
     *
     * <p>限 SUPER_ADMIN：这是跨企业读取入口。
     */
    @GetMapping("/all-enterprises")
    public R<List<BankAccountVO>> allEnterprises() {
        String userType = com.huicai.base.system.util.SecurityUtils.getCurrentUserType();
        if (!"SUPER_ADMIN".equals(userType)) {
            throw com.huicai.common.exception.BusinessException.forbidden(
                    "仅系统管理员可查看全部企业的银行账户");
        }
        List<BankAccountVO> list = BankAccountVO.from(service.listAllEnterprisesForSuperAdmin());
        // t_enterprise 未开 RLS，可直接读；补企业名便于管理员辨认
        java.util.Map<Long, String> names = enterpriseMapper
                .selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.huicai.agency.tenant.entity.EnterpriseEntity>()
                        .eq(com.huicai.agency.tenant.entity.EnterpriseEntity::getDeleted, 0))
                .stream().collect(java.util.stream.Collectors.toMap(
                        com.huicai.agency.tenant.entity.EnterpriseEntity::getId,
                        com.huicai.agency.tenant.entity.EnterpriseEntity::getEnterpriseName,
                        (a, b) -> a));
        // 企业名按实体自身的 enterprise_id 回填；不把租户列放进 VO
        service.listAllEnterprisesForSuperAdmin().forEach(e ->
                list.stream().filter(vo -> vo.getId().equals(e.getId()))
                        .forEach(vo -> vo.setEnterpriseName(names.get(e.getEnterpriseId()))));
        return R.ok(list);
    }

    @Operation(summary = "查询所有启用账户")
    @GetMapping("/active")
    public R<List<BankAccountVO>> active() {
        return R.ok(BankAccountVO.from(service.listActive()));
    }

    @Operation(summary = "账户详情")
    @GetMapping("/{id}")
    public R<BankAccountVO> getById(@PathVariable Long id) {
        return R.ok(BankAccountVO.from(service.getById(id)));
    }

    @Operation(summary = "新增账户")
    @PostMapping
    public R<BankAccountVO> create(@Valid @RequestBody BankAccountSaveDTO dto) {
        BankAccountEntity entity = dto.toEntity();
        return R.ok(BankAccountVO.from(service.create(entity)));
    }

    @Operation(summary = "修改账户")
    @PutMapping("/{id}")
    public R<BankAccountVO> update(@PathVariable Long id, @Valid @RequestBody BankAccountSaveDTO dto) {
        BankAccountEntity entity = dto.toEntity();
        return R.ok(BankAccountVO.from(service.update(id, entity)));
    }

    @Operation(summary = "删除账户")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
