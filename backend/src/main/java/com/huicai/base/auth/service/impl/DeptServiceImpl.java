package com.huicai.base.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.auth.entity.DeptEntity;
import com.huicai.base.auth.mapper.DeptMapper;
import com.huicai.base.auth.service.DeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    private final DeptMapper deptMapper;

    @Override
    public List<DeptEntity> getDeptTree() {
        List<DeptEntity> allDepts = deptMapper.selectList(
                new LambdaQueryWrapper<DeptEntity>()
                        .eq(DeptEntity::getDeleted, 0)
                        .orderByAsc(DeptEntity::getSortOrder));
        return buildTree(allDepts, null);
    }

    @Override
    public DeptEntity getById(Long id) {
        return deptMapper.selectById(id);
    }

    @Override
    public void create(DeptEntity dept) {
        deptMapper.insert(dept);
    }

    @Override
    public void update(DeptEntity dept) {
        deptMapper.updateById(dept);
    }

    @Override
    public void delete(Long id) {
        Long count = deptMapper.selectCount(
                new LambdaQueryWrapper<DeptEntity>()
                        .eq(DeptEntity::getParentId, id)
                        .eq(DeptEntity::getDeleted, 0));
        if (count > 0) {
            throw new BusinessException("存在子部门，无法删除");
        }
        deptMapper.deleteById(id);
    }

    private List<DeptEntity> buildTree(List<DeptEntity> allDepts, Long parentId) {
        List<DeptEntity> tree = new ArrayList<>();
        for (DeptEntity dept : allDepts) {
            if (parentId == null && dept.getParentId() == null ||
                parentId != null && parentId.equals(dept.getParentId())) {
                dept.setChildren(buildTree(allDepts, dept.getId()));
                tree.add(dept);
            }
        }
        return tree;
    }
}
