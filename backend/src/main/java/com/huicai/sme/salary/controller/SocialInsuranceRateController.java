package com.huicai.sme.salary.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.response.R;
import com.huicai.sme.salary.entity.SalaryItemEntity;
import com.huicai.sme.salary.entity.SocialInsuranceRateEntity;
import com.huicai.sme.salary.mapper.SalaryItemMapper;
import com.huicai.sme.salary.mapper.SocialInsuranceRateMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * SPEC-P155 R1：单位社保险种比例配置。
 *
 * <p>挂在 {@code t_salary_item}(category=COMPANY_SOCIAL) 项目行上，每项目一条
 * 生效比例（uq_social_rate_item）。写端点权限复用 salary:save（工资设置族）。</p>
 */
@Tag(name = "单位社保比例配置")
@RestController
@RequestMapping("/api/sme/salary/v1/social-rates")
@RequiredArgsConstructor
@Validated
@Transactional(rollbackFor = Exception.class) // A-2/P128：事务挂 Controller，防 GUC NULL ⇒ RLS 滤空
public class SocialInsuranceRateController {

    private final SocialInsuranceRateMapper rateMapper;
    private final SalaryItemMapper salaryItemMapper;

    @Operation(summary = "比例配置列表（联项目名回显）")
    @PreAuthorize("hasAuthority('salary:list')")
    @GetMapping
    public R<List<RateVO>> list() {
        List<SocialInsuranceRateEntity> rows = rateMapper.selectList(
                new LambdaQueryWrapper<SocialInsuranceRateEntity>()
                        .orderByAsc(SocialInsuranceRateEntity::getId));
        return R.ok(rows.stream().map(this::toVO).toList());
    }

    @Operation(summary = "保存比例（按项目 upsert：同项目旧配置软删）")
    @PreAuthorize("hasAuthority('salary:save')")
    @PostMapping
    public R<Long> save(@Valid @RequestBody RateSaveDTO dto) {        SalaryItemEntity item = mustBeCompanySocialItem(dto.getItemId());
        // 同项目旧配置软删（uq_social_rate_item 部分索引允许重插）
        SocialInsuranceRateEntity old = rateMapper.selectOne(
                new LambdaQueryWrapper<SocialInsuranceRateEntity>()
                        .eq(SocialInsuranceRateEntity::getItemId, dto.getItemId()));
        SocialInsuranceRateEntity rate = old != null ? old : new SocialInsuranceRateEntity();
        rate.setItemId(dto.getItemId());
        rate.setCompanyRate(dto.getCompanyRate());
        rate.setBaseFloor(dto.getBaseFloor());
        rate.setBaseCap(dto.getBaseCap());
        rate.setRemark(dto.getRemark());
        // SPEC-P159：基数口径透传（null 归一为 GROSS，DB 列 NOT NULL DEFAULT 'GROSS' 由实体 fill 兜底也可）
        rate.setBaseMode(dto.getBaseMode() == null ? "GROSS" : dto.getBaseMode());
        if (old == null) {
            rateMapper.insert(rate);
        } else {
            rateMapper.updateById(rate);
        }
        return R.ok(rate.getId());
    }

    @Operation(summary = "删除比例配置")
    @PreAuthorize("hasAuthority('salary:save')")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        SocialInsuranceRateEntity rate = rateMapper.selectById(id);
        if (rate == null) {
            throw BusinessException.notFound("比例配置不存在");
        }
        rateMapper.deleteById(id);
        return R.ok();
    }

    private SalaryItemEntity mustBeCompanySocialItem(Long itemId) {
        SalaryItemEntity item = salaryItemMapper.selectById(itemId);
        if (item == null || !"COMPANY_SOCIAL".equals(item.getCategory())) {
            throw BusinessException.badRequest("比例配置只能挂在「单位社保」类别的薪资项目上（itemId=" + itemId + "）");
        }
        return item;
    }

    private RateVO toVO(SocialInsuranceRateEntity r) {
        RateVO vo = new RateVO();
        vo.setId(r.getId());
        vo.setItemId(r.getItemId());
        SalaryItemEntity item = salaryItemMapper.selectById(r.getItemId());
        vo.setItemName(item == null ? null : item.getItemName());
        vo.setCompanyRate(r.getCompanyRate());
        vo.setBaseFloor(r.getBaseFloor());
        vo.setBaseCap(r.getBaseCap());
        vo.setBaseMode(r.getBaseMode());
        vo.setRemark(r.getRemark());
        return vo;
    }

    /** 出参 VO（OutParam 零容忍：不直出 Entity）。 */
    @Data
    public static class RateVO {
        private Long id;
        private Long itemId;
        private String itemName;
        private BigDecimal companyRate;
        private BigDecimal baseFloor;
        private BigDecimal baseCap;
        /** SPEC-P159：基数口径 GROSS=当期应发 | EMP_DECL=员工社保核定基数 */
        private String baseMode;
        private String remark;
    }

    @Data
    public static class RateSaveDTO {
        @NotNull(message = "薪资项目不能为空")
        private Long itemId;

        @NotNull(message = "单位比例不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "单位比例必须大于 0")
        @DecimalMax(value = "1", message = "单位比例不能超过 1（100%）")
        private BigDecimal companyRate;

        @Size(max = 500, message = "备注过长")
        private String remark;

        private BigDecimal baseFloor;
        private BigDecimal baseCap;

        /** SPEC-P159：基数口径 GROSS=当期应发（默认） | EMP_DECL=员工社保核定基数 */
        @Pattern(regexp = "GROSS|EMP_DECL", message = "基数口径只允许 GROSS 或 EMP_DECL")
        private String baseMode;
    }
}
