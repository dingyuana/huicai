package com.huicai.sme.arap.service;

import cn.hutool.core.util.StrUtil;
import com.huicai.common.util.TemplateContext;
import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.base.voucher.mapper.VoucherTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 凭证模板多维匹配引擎.
 *
 * <p>匹配优先级:
 * <ol>
 *   <li>source + businessType + direction（精确匹配）</li>
 *   <li>source + businessType（忽略方向）</li>
 *   <li>classification（兼容现有银行流水分类）</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class TemplateMatcher {

    private final VoucherTemplateMapper templateMapper;

    /**
     * 根据上下文匹配合适的激活模板.
     *
     * @param ctx 上下文
     * @return 匹配到的模板（含分录行），无匹配返回 null
     */
    public VoucherTemplateEntity match(TemplateContext ctx) {
        if (ctx == null) return null;

        // 1. 精确匹配: source + businessType + direction
        VoucherTemplateEntity t = find(ctx.getSource(), ctx.getBusinessType(), ctx.getDirection());
        if (t != null) return t;

        // 2. 业务类型匹配: source + businessType（忽略方向）
        t = find(ctx.getSource(), ctx.getBusinessType(), null);
        if (t != null) return t;

        // 3. 分类匹配（兼容现有）
        if (StrUtil.isNotBlank(ctx.getClassification())) {
            t = templateMapper.selectActiveByClassification(ctx.getClassification());
            if (t != null) return t;
        }

        return null;
    }

    private VoucherTemplateEntity find(String source, String businessType, String direction) {
        if (StrUtil.isBlank(source) && StrUtil.isBlank(businessType)) return null;
        return templateMapper.matchByDimensions(source, businessType, direction);
    }
}