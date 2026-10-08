package com.huicai.base.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.response.R;
import com.huicai.base.system.entity.SummaryLibEntity;
import com.huicai.base.system.service.SummaryLibService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.huicai.base.system.vo.SummaryLibVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.huicai.base.system.dto.SummaryLibSaveDTO;

@Tag(name = "常用摘要库管理")
@RestController
@RequestMapping("/api/v1/summary-lib")
@RequiredArgsConstructor
public class SummaryLibController {

    private final SummaryLibService summaryLibService;

    @Operation(summary = "获取摘要列表(分页)")
    @GetMapping
    public R<IPage<com.huicai.base.system.vo.SummaryLibVO>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(SummaryLibVO.from(summaryLibService.page(new Page<>(current, size))));
    }

    @Operation(summary = "获取摘要列表(全量)")
    @GetMapping("/all")
    public R<List<com.huicai.base.system.vo.SummaryLibVO>> listAll() {
        return R.ok(SummaryLibVO.from(summaryLibService.list()));
    }

    @Operation(summary = "新增摘要")
    @PostMapping
    public R<com.huicai.base.system.vo.SummaryLibVO> create(@Valid @RequestBody SummaryLibSaveDTO dto) {
        SummaryLibEntity summary = dto.toEntity();
        summaryLibService.save(summary);
        return R.ok(SummaryLibVO.from(summary));
    }

    @Operation(summary = "修改摘要")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SummaryLibSaveDTO dto) {
        SummaryLibEntity summary = dto.toEntity();
        summary.setId(id);
        summaryLibService.updateById(summary);
        return R.ok();
    }

    @Operation(summary = "删除摘要")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        summaryLibService.removeById(id);
        return R.ok();
    }

    @Operation(summary = "获取摘要详情")
    @GetMapping("/{id}")
    public R<com.huicai.base.system.vo.SummaryLibVO> getById(@PathVariable Long id) {
        return R.ok(SummaryLibVO.from(summaryLibService.getById(id)));
    }
}
