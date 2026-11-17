package com.huicai.sme.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.base.business.dto.BusinessDocDTO;
import com.huicai.base.business.dto.BusinessDocQueryDTO;
import com.huicai.base.business.dto.BusinessDocVO;
import com.huicai.base.voucher.dto.VoucherTemplateVO;
import com.huicai.base.voucher.mapper.VoucherTemplateMapper;
import com.huicai.base.business.service.BusinessDocService;
import com.huicai.base.system.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "业务单据")
@RestController
@RequestMapping("/api/sme/arap/v1/business-docs")
@RequiredArgsConstructor
public class BusinessDocController {

    private final BusinessDocService docService;
    private final VoucherTemplateMapper templateMapper;

    @Operation(summary = "分页查询业务单据")
    @PostMapping("/page")
    public R<IPage<BusinessDocVO>> page(@RequestBody BusinessDocQueryDTO queryDTO) {
        return R.ok(docService.pageQuery(queryDTO));
    }

    @Operation(summary = "单据详情")
    @GetMapping("/{id}")
    public R<BusinessDocVO> getById(@PathVariable Long id) {
        return R.ok(docService.getDetail(id));
    }

    @Operation(summary = "新增单据")
    @PostMapping
    public R<BusinessDocVO> create(@RequestBody BusinessDocDTO dto) {
        return R.ok(docService.create(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "修改单据")
    @PutMapping("/{id}")
    public R<BusinessDocVO> update(@PathVariable Long id, @RequestBody BusinessDocDTO dto) {
        dto.setId(id);
        return R.ok(docService.update(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "删除单据")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        docService.delete(id);
        return R.ok();
    }

    @Operation(summary = "提交单据")
    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        docService.submit(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "审批通过")
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id) {
        docService.approve(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "驳回单据")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id) {
        docService.reject(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "单据生成凭证")
    @PostMapping("/{id}/generate-voucher")
    public R<BusinessDocVO> generateVoucher(@PathVariable Long id) {
        return R.ok(docService.generateVoucher(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "红冲单据")
    @PostMapping("/{id}/reverse")
    public R<BusinessDocVO> reverse(@PathVariable Long id) {
        return R.ok(docService.reverse(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "查询所有有效凭证模板")
    @GetMapping("/templates")
    // 🔴 事务必须在 Controller 上（SPEC-P128 §7.1 同型，2026-11-05 实测坐实）：
    //   t_voucher_template 是 RLS 租户表，直调 Mapper 无事务 ⇒ TenantGucSetter
    //   提前 return ⇒ GUC 恒 NULL ⇒ 谓词取 NULL ⇒ DB 有 18 个模板仍返 []。
    //   同表对照：走 Service 的 /voucher-templates/all 返回 18 行。
    @Transactional(rollbackFor = Exception.class)
    public R<List<VoucherTemplateVO>> templates() {
        // 第二参传 null：分录行需逐模板再查一次 N+1，此处只要模板头（P102 批次 15）
        return R.ok(templateMapper.selectAllActive().stream()
                .map(t -> VoucherTemplateVO.fromEntity(t, null))
                .toList());
    }
}
