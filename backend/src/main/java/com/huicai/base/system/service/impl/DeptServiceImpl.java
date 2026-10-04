package com.huicai.base.system.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.DeptEntity;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.base.system.service.DeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
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
