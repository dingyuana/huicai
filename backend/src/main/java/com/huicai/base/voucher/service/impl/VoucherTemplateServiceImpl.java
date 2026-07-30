package com.huicai.base.voucher.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.base.voucher.entity.VoucherTemplateLineEntity;
import com.huicai.base.voucher.mapper.VoucherTemplateLineMapper;
import com.huicai.base.voucher.mapper.VoucherTemplateMapper;
import com.huicai.base.voucher.service.VoucherTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherTemplateServiceImpl implements VoucherTemplateService {

    private final VoucherTemplateMapper templateMapper;
    private final VoucherTemplateLineMapper lineMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public VoucherTemplateEntity getById(Long id) {
        return templateMapper.selectById(id);
    }

    @Override
    public List<VoucherTemplateEntity> listAllActive() {
        return templateMapper.selectList(
                new LambdaQueryWrapper<VoucherTemplateEntity>()
                        .eq(VoucherTemplateEntity::getIsActive, true)
                        .orderByAsc(VoucherTemplateEntity::getId));
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

    // ─── 模板参考库 ───

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importFromReference(Long enterpriseId) {
        // 查询参考库模板数量
        Long refCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_voucher_template WHERE enterprise_id = 0 AND deleted = 0",
            Long.class);
        if (refCount == null || refCount == 0) {
            log.info("模板参考库为空 (enterprise_id=0)，无需导入");
            return 0;
        }

        // 使用 SQL 批量导入：通过 template_code 关联，避免重复导入
        // 1. 导入模板（跳过已存在的）
        String insertTplSql = """
            INSERT INTO t_voucher_template (template_code, template_name, doc_type, voucher_type_code, summary, entries, is_active, remark, enterprise_id, created_at, updated_at, deleted)
            SELECT ref.template_code, ref.template_name, ref.doc_type, ref.voucher_type_code, ref.summary, ref.entries, ref.is_active, ref.remark, ?, NOW(), NOW(), 0
            FROM t_voucher_template ref
            WHERE ref.enterprise_id = 0 AND ref.deleted = 0
              AND NOT EXISTS (
                SELECT 1 FROM t_voucher_template tgt
                WHERE tgt.template_code = ref.template_code AND tgt.enterprise_id = ?
              )
            ON CONFLICT (template_code, enterprise_id) DO NOTHING
            """;

        int tplRows = jdbcTemplate.update(insertTplSql, enterpriseId, enterpriseId);

        // 2. 导入模板分录行，重映射 template_id 和 subject_id
        if (tplRows > 0) {
            String insertLineSql = """
                INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, assist_type, assist_required, line_order, enterprise_id, created_at, updated_at, deleted)
                SELECT
                  (SELECT id FROM t_voucher_template WHERE template_code = ref_tpl.template_code AND enterprise_id = ?),
                  COALESCE(
                    (SELECT id FROM t_subject WHERE code = sc.code AND enterprise_id = ?),
                    ref_line.subject_id
                  ),
                  ref_line.dr_amount_template, ref_line.cr_amount_template, ref_line.summary_template,
                  ref_line.direction, ref_line.assist_type, ref_line.assist_required, ref_line.line_order,
                  ?, NOW(), NOW(), 0
                FROM t_voucher_template_line ref_line
                JOIN t_voucher_template ref_tpl ON ref_tpl.id = ref_line.template_id
                LEFT JOIN t_subject sc ON sc.id = ref_line.subject_id
                WHERE ref_tpl.enterprise_id = 0 AND ref_line.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM t_voucher_template_line tgt_line
                    JOIN t_voucher_template tgt_tpl ON tgt_tpl.id = tgt_line.template_id
                    WHERE tgt_tpl.template_code = ref_tpl.template_code
                      AND tgt_tpl.enterprise_id = ?
                      AND tgt_line.line_order = ref_line.line_order
                  )
                ON CONFLICT DO NOTHING
                """;

            int lineRows = jdbcTemplate.update(insertLineSql, enterpriseId, enterpriseId, enterpriseId, enterpriseId);
            log.info("模板参考库导入完成: enterprise={}, templates={}, lines={}", enterpriseId, tplRows, lineRows);
        } else {
            log.info("模板参考库导入: enterprise={}, 无新模板需要导入", enterpriseId);
        }

        return tplRows;
    }

    @Override
    public List<VoucherTemplateEntity> listReferenceTemplates() {
        // 使用 JdbcTemplate 绕过 MyBatis-Plus 企业数据权限拦截器
        String sql = "SELECT id, template_code, template_name, doc_type, voucher_type_code, summary, " +
            "is_active, remark, enterprise_id, created_at, updated_at " +
            "FROM t_voucher_template WHERE enterprise_id = 0 AND deleted = 0 ORDER BY id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            VoucherTemplateEntity t = new VoucherTemplateEntity();
            t.setId(rs.getLong("id"));
            t.setName(rs.getString("template_name"));
            t.setBusinessType(rs.getString("doc_type"));
            t.setIsActive(rs.getBoolean("is_active"));
            t.setEnterpriseId(rs.getLong("enterprise_id"));
            t.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
            t.setUpdatedAt(rs.getTimestamp("updated_at") != null
                ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
            return t;
        });
    }

    // ─── 内部方法 ───

    /**
     * 停用同分类下的其他激活模板 (保持每个分类仅 1 个激活).
     */
    private void deactivateSiblings(String classification, Long excludeId) {
        List<VoucherTemplateEntity> siblings = templateMapper.selectList(
                new LambdaQueryWrapper<VoucherTemplateEntity>()
                        .eq(VoucherTemplateEntity::getBusinessType, classification)
                        .eq(VoucherTemplateEntity::getIsActive, true)
                        .ne(excludeId != null, VoucherTemplateEntity::getId, excludeId));
        for (VoucherTemplateEntity sib : siblings) {
            sib.setIsActive(false);
            templateMapper.updateById(sib);
            log.info("自动停用同分类模板: id={}, name={}", sib.getId(), sib.getName());
        }
    }
}
