package com.huicai.base.voucher.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.context.ReferenceContextSwap;
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

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 类级事务（P102/M5b，2026-10-03）：走租户表的路径必须有事务，否则
 * TenantRlsInitializer 切面不触发、app.enterprise_id 设不进去，
 * 应用以非超级用户连接时 RLS 会把本企业数据也过滤掉（读 0 行）。
 * 方法级 @Transactional 优先级更高，不受此影响。
 */
@Transactional
public class VoucherTemplateServiceImpl implements VoucherTemplateService {

    private final VoucherTemplateMapper templateMapper;
    private final VoucherTemplateLineMapper lineMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

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
    public VoucherTemplateEntity matchByClassification(String docTypeCode) {
        if (StrUtil.isBlank(docTypeCode)) return null;
        return templateMapper.selectActiveByClassification(docTypeCode);
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
        if (Boolean.TRUE.equals(template.getIsActive()) && StrUtil.isNotBlank(template.getBusinessType())) {
            deactivateSiblings(template.getBusinessType(), null);
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

        log.info("凭证模板创建: id={}, name={}, docType={}",
                template.getId(), template.getName(), template.getBusinessType());
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
                && StrUtil.isNotBlank(template.getBusinessType())) {
            deactivateSiblings(template.getBusinessType(), template.getId());
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

        if (active && StrUtil.isNotBlank(template.getBusinessType())) {
            deactivateSiblings(template.getBusinessType(), id);
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
        // SPEC-P161：两阶段导入。原单条 INSERT..SELECT 在生产（非超管 + GUC=租户）
        // 下 ref 扫描被 RLS 谓词过滤成 0 行 ⇒ 「成功」返回 0 —— 静默失效同因。
        // Phase A：参考库上下文（ctx=0）读 ent0 模板+分录+科目码进内存；
        // Phase B：还原租户上下文后逐模板落库——target enterprise_id 显式给值过 WITH CHECK，
        //           行级 subject remap（目标企业按 code 找，缺则降级 ref subject_id，原 COALESCE 语义）。
        List<RefTemplate> refs = ReferenceContextSwap.withReferenceLibrary(dataSource, this::readReferenceTemplates);
        if (refs.isEmpty()) {
            log.info("模板参考库为空 (enterprise_id=0)，无需导入");
            return 0;
        }

        // Phase B-1：导入模板（跳过目标企业已存在的，原 NOT EXISTS 语义）
        int tplRows = 0;
        for (RefTemplate ref : refs) {
            List<Long> exists = jdbcTemplate.queryForList(
                "SELECT id FROM t_voucher_template WHERE template_code = ? AND enterprise_id = ? AND deleted = 0",
                Long.class, ref.code(), enterpriseId);
            if (!exists.isEmpty()) {
                continue;
            }
            jdbcTemplate.update("""
                INSERT INTO t_voucher_template (template_code, template_name, doc_type, voucher_type_code, summary, entries, is_active, remark, enterprise_id, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, NOW(), NOW(), 0)
                """,
                ref.code(), ref.name(), ref.docType(), ref.voucherTypeCode(), ref.summary(),
                ref.entries(), ref.isActive(), ref.remark(), enterpriseId);
            tplRows++;
        }

        if (tplRows == 0) {
            log.info("模板参考库导入: enterprise={}, 无新模板需要导入", enterpriseId);
            return 0;
        }

        // 原行为的口径：tplRows>0 时对所有 ent0 参考模板补分录行，行级 (template_code, line_order) 查重防重复
        int lineRows = 0;
        for (RefTemplate ref : refs) {
            List<Long> tgtIds = jdbcTemplate.queryForList(
                "SELECT id FROM t_voucher_template WHERE template_code = ? AND enterprise_id = ? AND deleted = 0",
                Long.class, ref.code(), enterpriseId);
            if (tgtIds.isEmpty()) {
                continue;
            }
            Long tgtId = tgtIds.get(0);
            for (RefLine line : ref.lines()) {
                // 先做行级科目 remap（原 COALESCE 语义）：目标企业同 code 科目优先，缺则降级 ref subject_id
                Long subjectId = line.subjectId();
                if (line.subjectCode() != null) {
                    List<Long> mapped = jdbcTemplate.queryForList(
                        "SELECT id FROM t_subject WHERE code = ? AND enterprise_id = ? AND deleted = 0",
                        Long.class, line.subjectCode(), enterpriseId);
                    if (!mapped.isEmpty()) {
                        subjectId = mapped.get(0);
                    }
                }
                // 再按"完整内容"行级查重（template + order + direction + remapped subject + 款额）——
                // 🔴 不能按 (template_id, line_order)：真实参考数据是借贷配对行，同一
                // line_order 挂借/贷两行（如借1002/贷6001），按单键查重会把每对的贷方全杀掉；
                // 🔴 subject 键要用 remap 后的 target id：部分重导入场景（先前已导入过
                //   分录行的旧模板 + 新增模板触发补行）ref id 与库里 target id 永不相等，必重复
                List<Long> dup = jdbcTemplate.queryForList(
                    "SELECT id FROM t_voucher_template_line WHERE template_id = ? AND line_order = ? "
                        + "AND direction = ? AND subject_id = ? "
                        + "AND dr_amount_template IS NOT DISTINCT FROM ? "
                        + "AND cr_amount_template IS NOT DISTINCT FROM ? "
                        + "AND deleted = 0",
                    Long.class, tgtId, line.lineOrder(), line.direction(), subjectId,
                    line.dr(), line.cr());
                if (!dup.isEmpty()) {
                    continue;
                }
                jdbcTemplate.update("""
                    INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, assist_type, assist_required, line_order, enterprise_id, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)
                    """,
                    tgtId, subjectId, line.dr(), line.cr(), line.summaryT(),
                    line.direction(), line.assistType(), line.assistRequired(), line.lineOrder(), enterpriseId);
                lineRows++;
            }
        }
        log.info("模板参考库导入完成: enterprise={}, templates={}, lines={}", enterpriseId, tplRows, lineRows);
        return tplRows;
    }

    /** Phase A：在参考库上下文（ctx=0）读 ent0 模板 + 分录行（科目码供 remap）。 */
    private List<RefTemplate> readReferenceTemplates() {
        List<RefTemplate> templates = jdbcTemplate.query(
            "SELECT id, template_code, template_name, doc_type, voucher_type_code, summary, entries, is_active, remark " +
                "FROM t_voucher_template WHERE enterprise_id = 0 AND deleted = 0 ORDER BY id",
            (rs, rowNum) -> new RefTemplate(rs.getLong("id"), rs.getString("template_code"),
                rs.getString("template_name"), rs.getString("doc_type"), rs.getString("voucher_type_code"),
                rs.getString("summary"), rs.getString("entries"), rs.getBoolean("is_active"),
                rs.getString("remark"), new java.util.ArrayList<>()));
        if (templates.isEmpty()) {
            return templates;
        }
        Map<Long, List<RefLine>> byTpl = new java.util.HashMap<>();
        jdbcTemplate.query(
            "SELECT l.template_id, l.line_order, l.direction, l.dr_amount_template, l.cr_amount_template, " +
                "l.summary_template, l.assist_type, l.assist_required, l.subject_id, s.code AS subject_code " +
                "FROM t_voucher_template_line l " +
                "JOIN t_voucher_template t ON t.id = l.template_id " +
                "LEFT JOIN t_subject s ON s.id = l.subject_id " +
                "WHERE t.enterprise_id = 0 AND l.deleted = 0 ORDER BY l.template_id, l.line_order, l.id",
            rs -> {
                byTpl.computeIfAbsent(rs.getLong("template_id"), k -> new java.util.ArrayList<>())
                    .add(new RefLine(rs.getInt("line_order"), rs.getString("direction"),
                        rs.getString("dr_amount_template"), rs.getString("cr_amount_template"),
                        rs.getString("summary_template"), rs.getString("assist_type"), rs.getBoolean("assist_required"),
                        rs.getLong("subject_id"), rs.getString("subject_code")));
            });
        for (RefTemplate t : templates) {
            List<RefLine> lines = byTpl.get(t.refId());
            if (lines != null) {
                t.lines().addAll(lines);
            }
        }
        return templates;
    }

    /** 参考库导入内存对象：ent0 模板 + 分录行（subjectCode 是 remap 依据）。 */
    private record RefTemplate(Long refId, String code, String name, String docType, String voucherTypeCode,
                               String summary, String entries, boolean isActive, String remark,
                               List<RefLine> lines) {
    }

    private record RefLine(Integer lineOrder, String direction, String dr, String cr, String summaryT,
                           String assistType, boolean assistRequired, Long subjectId, String subjectCode) {
    }

    @Override
    public List<VoucherTemplateEntity> listReferenceTemplates() {
        // SPEC-P161：参考库放行通道——交换 ctx=0 + GUC 后再查。原实现的 JdbcTemplate
        // 手写 WHERE enterprise_id=0 只「绕过应用层过滤」，拦不住 DB 层 RLS 谓词
        // （enterprise_id=0 被叠加 enterprise_id=租户 ⇒ 生产恒空）——绕过 ≠ 放行。
        String sql = "SELECT id, template_code, template_name, doc_type, voucher_type_code, summary, " +
            "is_active, remark, enterprise_id, created_at, updated_at " +
            "FROM t_voucher_template WHERE enterprise_id = 0 AND deleted = 0 ORDER BY id";
        return ReferenceContextSwap.withReferenceLibrary(dataSource, () ->
            jdbcTemplate.query(sql, (rs, rowNum) -> {
                VoucherTemplateEntity t = new VoucherTemplateEntity();
                t.setId(rs.getLong("id"));
                t.setTemplateCode(rs.getString("template_code"));
                t.setName(rs.getString("template_name"));
                t.setBusinessType(rs.getString("doc_type"));
                t.setVoucherTypeCode(rs.getString("voucher_type_code"));
                t.setSummary(rs.getString("summary"));
                t.setIsActive(rs.getBoolean("is_active"));
                t.setRemark(rs.getString("remark"));
                t.setEnterpriseId(rs.getLong("enterprise_id"));
                t.setCreatedAt(rs.getTimestamp("created_at") != null
                    ? rs.getTimestamp("created_at").toLocalDateTime() : null);
                t.setUpdatedAt(rs.getTimestamp("updated_at") != null
                    ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
                return t;
            }));
    }

    // ─── 内部方法 ───

    /**
     * 停用同分类下的其他激活模板 (保持每个分类仅 1 个激活).
     */
    private void deactivateSiblings(String docTypeFilter, Long excludeId) {
        List<VoucherTemplateEntity> siblings = templateMapper.selectList(
                new LambdaQueryWrapper<VoucherTemplateEntity>()
                        .eq(VoucherTemplateEntity::getBusinessType, docTypeFilter)
                        .eq(VoucherTemplateEntity::getIsActive, true)
                        .ne(excludeId != null, VoucherTemplateEntity::getId, excludeId));
        for (VoucherTemplateEntity sib : siblings) {
            sib.setIsActive(false);
            templateMapper.updateById(sib);
            log.info("自动停用同分类模板: id={}, name={}", sib.getId(), sib.getName());
        }
    }
}
