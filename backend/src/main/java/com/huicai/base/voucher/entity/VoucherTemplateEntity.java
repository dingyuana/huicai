package com.huicai.base.voucher.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import com.huicai.base.system.handler.JsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 凭证模板 — 配置驱动的科目映射 (替代 AutoGenerationService 中的硬编码).
 * 每个模板绑定一个 classification, 用于银行流水 A 类自动制证.
 *
 * <p><b>P110 Phase 1（2026-10-06）Entity↔DB 对齐修复</b>：
 * 表真实只有 8 个业务列，但旧 Entity 声明了 16 个字段（其中 8 个
 * {@code @TableField(exist = false)} 野列 + 5 处 BaseEntity 冗余重声明），导致：
 * ① INSERT 不包含 {@code template_code}/{@code entries} 这两个 NOT NULL 列 ⇒ 凭证模板从未能创建成功；
 * ② SELECT 也不回读 {@code voucher_type_code}/{@code summary}/{@code remark}；
 * ③ {@code description}/{@code classification}/{@code source}/{@code direction}/{@code matchPriority}/{@code numberPrefix}
 *   均是「历史设计稿的概念残留」，从未落库，读取恒 null（该读取方：VoucherTemplateVO）。
 * 本版本以表结构为唯一事实来源重写声明，清掉野列与冗余字段声明（getClassification/getDirection
 * 等调用方已同步修订）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_voucher_template")
public class VoucherTemplateEntity extends BaseEntity {

    /** 模板代码（每企业唯一，业务链接标识） */
    @TableField("template_code")
    private String templateCode;

    /** 模板名称（DB 列名 template_name，唯一受 (template_code, enterprise_id) 约束） */
    @TableField("template_name")
    private String name;

    /** 单据类型（DB 列名 doc_type；代码内取名 businessType 以与 TemplateContext 对齐） */
    @TableField("doc_type")
    private String businessType;

    /** 凭证类型代码（DB: voucher_type_code） */
    private String voucherTypeCode;

    /** 摘要模板（DB: summary） */
    private String summary;

    /** 分录模板 JSON 数组（DB: entries jsonb）——须用 JsonbTypeHandler */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String entries;

    /** 是否启用 */
    private Boolean isActive;

    /** 备注（DB: remark） */
    private String remark;
}
