package com.huicai.base.voucher.service;

import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.base.voucher.entity.VoucherTemplateLineEntity;

import java.util.List;

/**
 * 凭证模板服务 — 模板 CRUD + 分类匹配.
 */
public interface VoucherTemplateService {

    // ─── 查询 ───

    /** 根据 ID 查询模板 */
    VoucherTemplateEntity getById(Long id);

    /** 查询所有激活模板 (含分录行) */
    List<VoucherTemplateEntity> listAllActive();

    /** 根据分类匹配激活模板 (含分录行), 若无则返回 null */
    VoucherTemplateEntity matchByClassification(String classification);

    /** 查询模板的所有分录行 */
    List<VoucherTemplateLineEntity> getLines(Long templateId);

    // ─── 创建 ───

    /** 创建模板 (含分录行) */
    VoucherTemplateEntity create(VoucherTemplateEntity template, List<VoucherTemplateLineEntity> lines);

    // ─── 更新 ───

    /** 更新模板基本信息 */
    void update(VoucherTemplateEntity template);

    /** 更新模板分录行 (全量替换) */
    void updateLines(Long templateId, List<VoucherTemplateLineEntity> lines);

    // ─── 状态 ───

    /** 激活/停用模板 */
    void toggleActive(Long id, boolean active);

    /** 删除模板 (逻辑删除) */
    void delete(Long id);

    // ─── 模板参考库 ───

    /** 从参考库 (enterprise_id=0) 导入所有模板到当前企业，返回导入的模板数量 */
    int importFromReference(Long enterpriseId);

    /** 查询参考库模板列表 (enterprise_id=0)，绕过企业数据权限拦截器 */
    List<VoucherTemplateEntity> listReferenceTemplates();
}
