package com.huicai.module.finance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.finance.dto.BusinessDocDTO;
import com.huicai.module.finance.dto.BusinessDocQueryDTO;
import com.huicai.module.finance.dto.BusinessDocVO;
import com.huicai.module.finance.entity.VoucherTemplateEntity;
import com.huicai.module.finance.mapper.VoucherTemplateMapper;
import com.huicai.module.finance.service.BusinessDocService;
import com.huicai.module.system.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "业务单据")
@RestController
@RequestMapping("/api/v1/business-docs")
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
    public R<List<VoucherTemplateEntity>> templates() {
        return R.ok(templateMapper.selectAllActive());
    }
}
