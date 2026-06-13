package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.VoucherTemplateEntity;
import com.huicai.module.finance.entity.VoucherTemplateLineEntity;
import com.huicai.module.finance.mapper.VoucherTemplateLineMapper;
import com.huicai.module.finance.mapper.VoucherTemplateMapper;
import com.huicai.module.finance.service.VoucherTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherTemplateServiceImpl implements VoucherTemplateService {

    private final VoucherTemplateMapper templateMapper;
    private final VoucherTemplateLineMapper lineMapper;

    @Override
    public VoucherTemplateEntity getById(Long id) {
        return templateMapper.selectById(id);
    }

    @Override
    public VoucherTemplateEntity getWithLines(Long id) {
        return templateMapper.selectById(id);
    }

    @Override
    public List<VoucherTemplateEntity> listAllActive() {
        return templateMapper.selectList(
                new LambdaQueryWrapper<VoucherTemplateEntity>()
                        .eq(VoucherTemplateEntity::getIsActive, true)
                        .orderByAsc(VoucherTemplateEntity::getClassification));
    }

    @Override
    public VoucherTemplateEntity matchByClassification(String classification) {
        if (StrUtil.isBlank(classification)) return null;
        return templateMapper.selectActiveByClassification(classification);
    }

    @Override
    public List<VoucherTemplateLineEntity> getLines(Long templateId) {
        return lineMapper.selectByTemplateId(templateId);
    }

    @Override
    @Transactional
    public VoucherTemplateEntity create(VoucherTemplateEntity template, List<VoucherTemplateLineEntity> lines) {
        // 校验 name 唯一
        Long exists = templateMapper.selectCount(
                new LambdaQueryWrapper<VoucherTemplateEntity>()
                        .eq(VoucherTemplateEntity::getName, template.getName()));
        if (exists > 0) {
            throw new BusinessException(400, "模板名称已存在: " + template.getName());
        }

        // 如果设置激活, 先停用同分类其他模板
        if (Boolean.TRUE.equals(template.getIsActive()) && StrUtil.isNotBlank(template.getClassification())) {
            deactivateSiblings(template.getClassification(), null);
        }

        templateMapper.insert(template);

        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                VoucherTemplateLineEntity line = lines.get(i);
                line.setTemplateId(template.getId());
                if (line.getLineOrder() == null) {
                    line.setLineOrder(i + 1);
                }
                lineMapper.insert(line);
            }
        }

        log.info("凭证模板创建: id={}, name={}, classification={}",
                template.getId(), template.getName(), template.getClassification());
        return template;
    }

    @Override
    @Transactional
    public void update(VoucherTemplateEntity template) {
        VoucherTemplateEntity old = templateMapper.selectById(template.getId());
        if (old == null) {
            throw BusinessException.notFound("模板不存在: " + template.getId());
        }

        // 如果改为激活, 停用同分类其他模板
        if (Boolean.TRUE.equals(template.getIsActive())
                && !Boolean.TRUE.equals(old.getIsActive())
                && StrUtil.isNotBlank(template.getClassification())) {
            deactivateSiblings(template.getClassification(), template.getId());
        }

        templateMapper.updateById(template);
        log.info("凭证模板更新: id={}", template.getId());
    }

    @Override
    @Transactional
    public void updateLines(Long templateId, List<VoucherTemplateLineEntity> lines) {
        VoucherTemplateEntity old = templateMapper.selectById(templateId);
        if (old == null) {
            throw BusinessException.notFound("模板不存在: " + templateId);
        }

        // 全量替换: 删除旧行, 插入新行
        lineMapper.deleteByTemplateId(templateId);
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                VoucherTemplateLineEntity line = lines.get(i);
                line.setId(null);
                line.setTemplateId(templateId);
                if (line.getLineOrder() == null) {
                    line.setLineOrder(i + 1);
                }
                lineMapper.insert(line);
            }
        }
        log.info("凭证模板分录行更新: templateId={}, count={}", templateId, lines != null ? lines.size() : 0);
    }

    @Override
    @Transactional
    public void toggleActive(Long id, boolean active) {
        VoucherTemplateEntity template = templateMapper.selectById(id);
        if (template == null) {
            throw BusinessException.notFound("模板不存在: " + id);
        }

        if (active && StrUtil.isNotBlank(template.getClassification())) {
            deactivateSiblings(template.getClassification(), id);
        }

        template.setIsActive(active);
        templateMapper.updateById(template);
        log.info("凭证模板 {}: isActive={}", id, active);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        templateMapper.deleteById(id);
        lineMapper.deleteByTemplateId(id);
        log.info("凭证模板删除: id={}", id);
    }

    // ─── 内部方法 ───

    /**
     * 停用同分类下的其他激活模板 (保持每个分类仅 1 个激活).
     */
    private void deactivateSiblings(String classification, Long excludeId) {
        List<VoucherTemplateEntity> siblings = templateMapper.selectList(
                new LambdaQueryWrapper<VoucherTemplateEntity>()
                        .eq(VoucherTemplateEntity::getClassification, classification)
                        .eq(VoucherTemplateEntity::getIsActive, true)
                        .ne(excludeId != null, VoucherTemplateEntity::getId, excludeId));
        for (VoucherTemplateEntity sib : siblings) {
            sib.setIsActive(false);
            templateMapper.updateById(sib);
            log.info("自动停用同分类模板: id={}, name={}", sib.getId(), sib.getName());
        }
    }
}
