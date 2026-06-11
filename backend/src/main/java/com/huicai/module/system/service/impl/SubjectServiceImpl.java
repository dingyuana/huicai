package com.huicai.module.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import com.huicai.module.system.model.dto.SubjectCreateDTO;
import com.huicai.module.system.model.dto.SubjectUpdateDTO;
import com.huicai.module.system.model.vo.SubjectTreeVO;
import com.huicai.module.system.service.SubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectMapper subjectMapper;

    // ===== 方向常量 =====
    private static final String DIRECTION_DEBIT = "debit";
    private static final String DIRECTION_CREDIT = "credit";

    @Override
    public List<SubjectTreeVO> getTree() {
        // 查询所有未删除的科目
        List<Subject> allSubjects = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>()
                        .orderByAsc(Subject::getLevel)
                        .orderByAsc(Subject::getCode));

        // 构建树
        return buildTree(allSubjects);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Subject create(SubjectCreateDTO dto) {
        // 校验编码唯一性
        checkCodeUnique(dto.getCode(), null);

        // 校验借贷方向
        validateDirection(dto.getDirection());

        // 校验辅助核算类型
        validateAuxCalcType(dto.getAuxCalcType());

        // 计算层级
        int level = 1;
        if (dto.getParentId() != null) {
            Subject parent = subjectMapper.selectById(dto.getParentId());
            if (parent == null) {
                throw BusinessException.notFound("父科目不存在");
            }
            level = parent.getLevel() + 1;

            // 父科目不再是末级
            if (parent.getIsLeaf()) {
                parent.setIsLeaf(false);
                subjectMapper.updateById(parent);
            }
        }

        // 新建科目
        Subject subject = new Subject();
        BeanUtil.copyProperties(dto, subject);
        subject.setLevel(level);
        subject.setIsLeaf(true);

        subjectMapper.insert(subject);
        log.info("新增科目: id={}, code={}, name={}, level={}", subject.getId(), subject.getCode(), subject.getName(), level);
        return subject;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Subject update(Long id, SubjectUpdateDTO dto) {
        Subject existing = subjectMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("科目不存在");
        }

        // 校验编码唯一性（排除自身）
        if (!existing.getCode().equals(dto.getCode())) {
            checkCodeUnique(dto.getCode(), id);
        }

        // 校验借贷方向
        validateDirection(dto.getDirection());

        // 校验辅助核算类型
        validateAuxCalcType(dto.getAuxCalcType());

        // 不允许修改 parent_id（防止树结构混乱）
        BeanUtil.copyProperties(dto, existing);
        existing.setId(id);

        subjectMapper.updateById(existing);
        log.info("更新科目: id={}, code={}, name={}", id, dto.getCode(), dto.getName());
        return subjectMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Subject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw BusinessException.notFound("科目不存在");
        }

        // 检查是否有子科目
        Long childCount = subjectMapper.selectCount(
                new LambdaQueryWrapper<Subject>().eq(Subject::getParentId, id));
        if (childCount > 0) {
            throw BusinessException.badRequest("该科目下有子科目，无法删除");
        }

        subjectMapper.deleteById(id);
        log.info("删除科目: id={}, code={}, name={}", id, subject.getCode(), subject.getName());
    }

    @Override
    public Subject getById(Long id) {
        Subject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw BusinessException.notFound("科目不存在");
        }
        return subject;
    }

    // ===================== 私有方法 =====================

    /**
     * 校验科目编码唯一性
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<Subject> wrapper = new LambdaQueryWrapper<Subject>()
                .eq(Subject::getCode, code);
        if (excludeId != null) {
            wrapper.ne(Subject::getId, excludeId);
        }
        Long count = subjectMapper.selectCount(wrapper);
        if (count > 0) {
            throw BusinessException.conflict("科目编码 '" + code + "' 已存在");
        }
    }

    /**
     * 校验借贷方向
     */
    private void validateDirection(String direction) {
        if (!DIRECTION_DEBIT.equals(direction) && !DIRECTION_CREDIT.equals(direction)) {
            throw BusinessException.badRequest("借贷方向不合法: 只能为 debit(借方) 或 credit(贷方)");
        }
    }

    /**
     * 校验辅助核算类型
     */
    private void validateAuxCalcType(String auxCalcType) {
        if (auxCalcType == null || auxCalcType.isEmpty()) {
            return;
        }
        switch (auxCalcType) {
            case "customer":
            case "vendor":
            case "department":
            case "project":
            case "employee":
                return;
            default:
                throw BusinessException.badRequest(
                        "辅助核算类型不合法: customer/vendor/department/project/employee");
        }
    }

    /**
     * 构建科目树
     */
    private List<SubjectTreeVO> buildTree(List<Subject> subjects) {
        // 先转为 VO 列表
        List<SubjectTreeVO> voList = subjects.stream()
                .map(this::toTreeVO)
                .toList();

        // 按 parentId 分组
        Map<Long, List<SubjectTreeVO>> parentChildMap = voList.stream()
                .filter(vo -> vo.getParentId() != null)
                .collect(Collectors.groupingBy(SubjectTreeVO::getParentId));

        // 为每个节点挂载子节点
        for (SubjectTreeVO vo : voList) {
            List<SubjectTreeVO> children = parentChildMap.get(vo.getId());
            if (children != null) {
                vo.setChildren(children);
            } else {
                vo.setChildren(new ArrayList<>());
            }
        }

        // 返回根节点（parentId == null 的科目）
        return voList.stream()
                .filter(vo -> vo.getParentId() == null)
                .collect(Collectors.toList());
    }

    /**
     * Subject -> SubjectTreeVO
     */
    private SubjectTreeVO toTreeVO(Subject subject) {
        SubjectTreeVO vo = new SubjectTreeVO();
        BeanUtil.copyProperties(subject, vo);
        return vo;
    }
}