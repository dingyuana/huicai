package com.huicai.base.voucher.service;

/**
 * 年度结账服务（轻量年结，方案 B）
 *
 * <p>不引入独立年度实体，在现有「企业→期间」模型上补全年结能力：
 * <ul>
 *   <li>校验全年 12 个月均已结账</li>
 *   <li>锁定全年期间（status → locked）</li>
 *   <li>自动生成次年度 1-12 月期间</li>
 *   <li>将 12 月期末余额结转为次年 1 月期初余额</li>
 * </ul>
 */
public interface YearCloseService {

    /**
     * 年结前检查：全年 12 个月是否均已结账
     *
     * @param year 会计年度（如 2026）
     * @return 检查结果，含未结账月份列表
     */
    java.util.Map<String, Object> checkBeforeYearClose(Integer year);

    /**
     * 执行年度结账
     *
     * @param year 会计年度
     */
    void yearClose(Integer year);
}
