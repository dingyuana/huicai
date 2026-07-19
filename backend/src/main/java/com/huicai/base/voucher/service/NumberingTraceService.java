package com.huicai.base.voucher.service;

import com.huicai.base.voucher.dto.NumberingTraceVO;

/**
 * 编号关联追溯服务
 * 提供全链路双向追溯查询能力
 */
public interface NumberingTraceService {

    /**
     * 按任意编号查全链路追溯
     * 
     * @param traceNo 查询编号（可以是发票号、业务单号、核销单号、凭证号）
     * @return 追溯结果（上游 + 下游）
     */
    NumberingTraceVO traceByNumber(String traceNo);
}
