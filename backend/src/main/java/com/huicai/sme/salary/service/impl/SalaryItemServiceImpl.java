package com.huicai.sme.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.salary.dto.SalaryItemSaveDTO;
import com.huicai.sme.salary.entity.SalaryItemEntity;
import com.huicai.sme.salary.mapper.SalaryItemMapper;
import com.huicai.sme.salary.service.SalaryItemService;
import com.huicai.sme.salary.vo.SalaryItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SPEC-P143（D-4）：薪资项目配置实现 —— 简单 CRUD，itemCode 企业内唯一
 * （uq_salary_item_code 部分索引，软删后可复用编码）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SalaryItemServiceImpl implements SalaryItemService {

    private final SalaryItemMapper salaryItemMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SalaryItemVO> list() {
        return salaryItemMapper.selectList(new LambdaQueryWrapper<SalaryItemEntity>()
                .orderByAsc(SalaryItemEntity::getSortOrder)
                .orderByAsc(SalaryItemEntity::getId))
                .stream().map(SalaryItemServiceImpl::toVO).collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SalaryItemSaveDTO dto, Long userId) {
        assertCodeFree(dto.getItemCode(), null);
        SalaryItemEntity e = new SalaryItemEntity();
        apply(e, dto, userId);
        salaryItemMapper.insert(e);
        log.info("薪资项目创建: id={}, code={}, category={}, userId={}",
                e.getId(), e.getItemCode(), e.getCategory(), userId);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SalaryItemSaveDTO dto, Long userId) {
        SalaryItemEntity e = salaryItemMapper.selectById(id);
        if (e == null || Integer.valueOf(1).equals(e.getDeleted())) {
            throw BusinessException.notFound("薪资项目不存在");
        }
        assertCodeFree(dto.getItemCode(), id);
        apply(e, dto, userId);
        e.setUpdatedBy(userId);
        salaryItemMapper.updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        SalaryItemEntity e = salaryItemMapper.selectById(id);
        if (e == null || Integer.valueOf(1).equals(e.getDeleted())) {
            throw BusinessException.notFound("薪资项目不存在");
        }
        e.setDeleted(1);
        e.setUpdatedBy(userId);
        salaryItemMapper.updateById(e);
        log.info("薪资项目删除: id={}, userId={}", id, userId);
    }

    private void assertCodeFree(String code, Long excludeId) {
        Long dup = salaryItemMapper.selectCount(new LambdaQueryWrapper<SalaryItemEntity>()
                .eq(SalaryItemEntity::getItemCode, code)
                .ne(excludeId != null, SalaryItemEntity::getId, excludeId));
        if (dup != null && dup > 0) {
            throw BusinessException.badRequest("项目编码已存在：" + code);
        }
    }

    private void apply(SalaryItemEntity e, SalaryItemSaveDTO dto, Long userId) {
        e.setItemCode(dto.getItemCode());
        e.setItemName(dto.getItemName());
        e.setCategory(dto.getCategory());
        e.setDefaultAmount(dto.getDefaultAmount() == null ? java.math.BigDecimal.ZERO : dto.getDefaultAmount());
        e.setIsTaxable(dto.getIsTaxable() == null || dto.getIsTaxable());
        e.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        e.setRemark(dto.getRemark());
        e.setCreatedBy(userId);
    }

    private static SalaryItemVO toVO(SalaryItemEntity e) {
        SalaryItemVO vo = new SalaryItemVO();
        vo.setId(e.getId());
        vo.setItemCode(e.getItemCode());
        vo.setItemName(e.getItemName());
        vo.setCategory(e.getCategory());
        vo.setDefaultAmount(e.getDefaultAmount());
        vo.setIsTaxable(e.getIsTaxable());
        vo.setSortOrder(e.getSortOrder());
        vo.setRemark(e.getRemark());
        return vo;
    }
}
