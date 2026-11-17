package com.huicai.sme.salary.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.entity.DeptEntity;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.response.R;
import com.huicai.sme.salary.entity.DeptSubjectMapEntity;
import com.huicai.sme.salary.mapper.DeptSubjectMapMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SPEC-P155 R2：部门→费用科目映射。SALC 计提分录按员工 dept_id 查本配置
 * 得费用科目（销售部→6601 等），无映射/无部门回落 6602。
 * 写端点权限复用 salary:save（工资设置族）。
 */
@Tag(name = "部门费用科目映射")
@RestController
@RequestMapping("/api/sme/salary/v1/dept-subject-maps")
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class) // A-2/P128：事务挂 Controller，防 GUC NULL ⇒ RLS 滤空
public class DeptSubjectMapController {

    private final DeptSubjectMapMapper mapMapper;
    private final DeptMapper deptMapper;
    private final SubjectMapper subjectMapper;

    @Operation(summary = "映射列表（联部门名/科目名回显）")
    @PreAuthorize("hasAuthority('salary:list')")
    @GetMapping
    public R<List<MapVO>> list() {
        return R.ok(mapMapper.selectList(new LambdaQueryWrapper<DeptSubjectMapEntity>()
                        .orderByAsc(DeptSubjectMapEntity::getId))
                .stream().map(this::toVO).toList());
    }

    @Operation(summary = "保存映射（按部门 upsert：同部门旧映射软删）")
    @PreAuthorize("hasAuthority('salary:save')")
    @PostMapping
    public R<Long> save(@jakarta.validation.Valid @RequestBody MapSaveDTO dto) {
        if (deptMapper.selectById(dto.getDeptId()) == null) {
            throw BusinessException.badRequest("部门不存在（deptId=" + dto.getDeptId() + "）");
        }
        Subject subject = subjectMapper.selectOne(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getId, dto.getSubjectId()).eq(Subject::getDeleted, 0));
        if (subject == null) {
            throw BusinessException.badRequest("费用科目不存在（subjectId=" + dto.getSubjectId() + "）");
        }
        DeptSubjectMapEntity old = mapMapper.selectOne(
                new LambdaQueryWrapper<DeptSubjectMapEntity>()
                        .eq(DeptSubjectMapEntity::getDeptId, dto.getDeptId()));
        DeptSubjectMapEntity map = old != null ? old : new DeptSubjectMapEntity();
        map.setDeptId(dto.getDeptId());
        map.setSubjectId(dto.getSubjectId());
        map.setRemark(dto.getRemark());
        if (old == null) {
            mapMapper.insert(map);
        } else {
            mapMapper.updateById(map);
        }
        return R.ok(map.getId());
    }

    @Operation(summary = "删除映射")
    @PreAuthorize("hasAuthority('salary:save')")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        DeptSubjectMapEntity map = mapMapper.selectById(id);
        if (map == null) {
            throw BusinessException.notFound("映射配置不存在");
        }
        mapMapper.deleteById(id);
        return R.ok();
    }

    private MapVO toVO(DeptSubjectMapEntity m) {
        MapVO vo = new MapVO();
        vo.setId(m.getId());
        vo.setDeptId(m.getDeptId());
        DeptEntity dept = deptMapper.selectById(m.getDeptId());
        vo.setDeptName(dept == null ? null : dept.getName());
        vo.setSubjectId(m.getSubjectId());
        Subject subject = subjectMapper.selectById(m.getSubjectId());
        vo.setSubjectName(subject == null ? null : subject.getName());
        vo.setSubjectCode(subject == null ? null : subject.getCode());
        vo.setRemark(m.getRemark());
        return vo;
    }

    /** 出参 VO（OutParam 零容忍：不直出 Entity）。 */
    @Data
    public static class MapVO {
        private Long id;
        private Long deptId;
        private String deptName;
        private Long subjectId;
        private String subjectCode;
        private String subjectName;
        private String remark;
    }

    @Data
    public static class MapSaveDTO {
        @NotNull(message = "部门不能为空")
        private Long deptId;

        @NotNull(message = "费用科目不能为空")
        private Long subjectId;

        private String remark;
    }
}
