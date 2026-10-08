package com.huicai.base.masterdata.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.base.masterdata.entity.EmployeeEntity;
import com.huicai.base.masterdata.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.huicai.base.masterdata.dto.EmployeeSaveDTO;
import jakarta.validation.Valid;

@Tag(name = "员工档案 - P11-1")
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<com.huicai.base.masterdata.vo.EmployeeVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(com.huicai.base.masterdata.vo.EmployeeVO.from(service.pageQuery(keyword, isActive, current, size)));
    }

    @Operation(summary = "查询全部在职员工")
    @GetMapping("/list")
    public R<List<com.huicai.base.masterdata.vo.EmployeeVO>> list() {
        return R.ok(com.huicai.base.masterdata.vo.EmployeeVO.from(service.listAll()));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<com.huicai.base.masterdata.vo.EmployeeVO> getById(@PathVariable Long id) {
        return R.ok(com.huicai.base.masterdata.vo.EmployeeVO.from(service.getById(id)));
    }

    @Operation(summary = "按姓名查询（P11-3 银行流水匹配用）")
    @GetMapping("/by-name")
    public R<com.huicai.base.masterdata.vo.EmployeeVO> byName(@RequestParam String name) {
        return R.ok(com.huicai.base.masterdata.vo.EmployeeVO.from(service.findByName(name)));
    }

    @Operation(summary = "创建")
    @PostMapping
    public R<com.huicai.base.masterdata.vo.EmployeeVO> create(@Valid @RequestBody EmployeeSaveDTO dto) {
        EmployeeEntity entity = dto.toEntity();
        return R.ok(com.huicai.base.masterdata.vo.EmployeeVO.from(service.create(entity)));
    }

    @Operation(summary = "更新")
    @PutMapping("/{id}")
    public R<com.huicai.base.masterdata.vo.EmployeeVO> update(@PathVariable Long id, @Valid @RequestBody EmployeeSaveDTO dto) {
        EmployeeEntity entity = dto.toEntity();
        entity.setId(id);
        return R.ok(com.huicai.base.masterdata.vo.EmployeeVO.from(service.update(entity)));
    }

    @Operation(summary = "删除（逻辑）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
