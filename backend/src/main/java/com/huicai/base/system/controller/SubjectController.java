package com.huicai.base.system.controller;

import com.huicai.common.response.R;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.model.dto.SubjectCreateDTO;
import com.huicai.base.system.model.dto.SubjectUpdateDTO;
import com.huicai.base.system.model.vo.SubjectTreeVO;
import com.huicai.base.system.service.SubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "科目管理")
@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @Operation(summary = "获取科目树(全量)")
    @GetMapping("/tree")
    public R<List<SubjectTreeVO>> getTree() {
        return R.ok(subjectService.getTree());
    }

    @Operation(summary = "新增科目")
    @PostMapping
    public R<Subject> create(@Valid @RequestBody SubjectCreateDTO dto) {
        return R.ok(subjectService.create(dto));
    }

    @Operation(summary = "修改科目")
    @PutMapping("/{id}")
    public R<Subject> update(@PathVariable Long id, @Valid @RequestBody SubjectUpdateDTO dto) {
        return R.ok(subjectService.update(id, dto));
    }

    @Operation(summary = "删除科目")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return R.ok();
    }

    @Operation(summary = "获取科目详情")
    @GetMapping("/{id}")
    public R<Subject> getById(@PathVariable Long id) {
        return R.ok(subjectService.getById(id));
    }

    @Operation(summary = "一键导入国家标准科目")
    @PostMapping("/import-standard")
    public R<Integer> importStandard() {
        int count = subjectService.importStandard();
        return R.ok(count);
    }
}