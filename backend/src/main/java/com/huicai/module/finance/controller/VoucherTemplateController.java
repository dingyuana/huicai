package com.huicai.module.finance.controller;

import com.huicai.common.response.R;
import com.huicai.module.finance.dto.VoucherTemplateVO;
import com.huicai.module.finance.entity.VoucherTemplateEntity;
import com.huicai.module.finance.entity.VoucherTemplateLineEntity;
import com.huicai.module.finance.service.VoucherTemplateService;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 凭证模板管理 — 配置驱动的科目映射 (替代硬编码).
 */
@Tag(name = "凭证模板管理")
@RestController
@RequestMapping("/api/v1/voucher-templates")
@RequiredArgsConstructor
public class VoucherTemplateController {

    private final VoucherTemplateService templateService;
    private final SubjectMapper subjectMapper;

    @Operation(summary = "模板列表 (含分录行)")
    @GetMapping
    public R<List<VoucherTemplateVO>> list(@RequestParam(required = false) String classification) {
        List<VoucherTemplateEntity> templates;
        if (classification != null && !classification.isEmpty()) {
            VoucherTemplateEntity t = templateService.matchByClassification(classification);
            templates = t != null ? List.of(t) : List.of();
        } else {
            templates = templateService.listAllActive();
        }
        if (templates.isEmpty() && (classification == null || classification.isEmpty())) {
            templates = templateService.listAllActive();
        }

        List<VoucherTemplateVO> vos = templates.stream()
                .map(t -> enrichLines(VoucherTemplateVO.fromEntity(t, templateService.getLines(t.getId()))))
                .collect(Collectors.toList());
        return R.ok(vos);
    }

    @Operation(summary = "全部模板 (含未激活、含分录行)")
    @GetMapping("/all")
    public R<List<VoucherTemplateVO>> listAll() {
        List<VoucherTemplateEntity> templates = templateService.listAllActive();
        List<VoucherTemplateVO> vos = templates.stream()
                .map(t -> enrichLines(VoucherTemplateVO.fromEntity(t, templateService.getLines(t.getId()))))
                .collect(Collectors.toList());
        return R.ok(vos);
    }

    @Operation(summary = "模板详情 (含分录行)")
    @GetMapping("/{id}")
    public R<VoucherTemplateVO> getById(@PathVariable Long id) {
        VoucherTemplateEntity template = templateService.getById(id);
        if (template == null) {
            return R.badRequest("模板不存在");
        }
        return R.ok(enrichLines(VoucherTemplateVO.fromEntity(template, templateService.getLines(id))));
    }

    @Operation(summary = "创建模板 (含分录行)")
    @PostMapping
    public R<VoucherTemplateVO> create(@RequestBody VoucherTemplateCreateRequest request) {
        VoucherTemplateEntity template = templateService.create(request.toEntity(), request.getLines());
        return R.ok(enrichLines(VoucherTemplateVO.fromEntity(template, templateService.getLines(template.getId()))));
    }

    @Operation(summary = "更新模板基本信息")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody VoucherTemplateEntity template) {
        template.setId(id);
        templateService.update(template);
        return R.ok();
    }

    @Operation(summary = "更新模板分录行 (全量替换)")
    @PutMapping("/{id}/lines")
    public R<Void> updateLines(@PathVariable Long id, @RequestBody List<VoucherTemplateLineEntity> lines) {
        templateService.updateLines(id, lines);
        return R.ok();
    }

    @Operation(summary = "激活/停用模板")
    @PostMapping("/{id}/toggle-active")
    public R<Void> toggleActive(@PathVariable Long id, @RequestParam boolean active) {
        templateService.toggleActive(id, active);
        return R.ok();
    }

    @Operation(summary = "删除模板 (逻辑删除)")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }

    // ─── 辅助方法 ───

    /** 填充分录行的科目代码/名称 */
    private VoucherTemplateVO enrichLines(VoucherTemplateVO vo) {
        if (vo.getLines() != null) {
            for (VoucherTemplateVO.LineVO line : vo.getLines()) {
                if (line.getSubjectId() != null) {
                    Subject s = subjectMapper.selectById(line.getSubjectId());
                    if (s != null) {
                        line.setSubjectCode(s.getCode());
                        line.setSubjectName(s.getName());
                    }
                }
            }
        }
        return vo;
    }

    // ─── 请求体 DTO ───

    @lombok.Data
    public static class VoucherTemplateCreateRequest {
        private String name;
        private String description;
        private String classification;
        private String source;
        private String businessType;
        private String direction;
        private Integer matchPriority;
        private String numberPrefix;
        private Boolean isActive;
        private List<VoucherTemplateLineEntity> lines;

        public VoucherTemplateEntity toEntity() {
            VoucherTemplateEntity e = new VoucherTemplateEntity();
            e.setName(name);
            e.setDescription(description);
            e.setClassification(classification);
            e.setSource(source);
            e.setBusinessType(businessType);
            e.setDirection(direction);
            e.setMatchPriority(matchPriority);
            e.setNumberPrefix(numberPrefix != null ? numberPrefix : "JZ");
            e.setIsActive(isActive != null ? isActive : true);
            return e;
        }
    }
}
