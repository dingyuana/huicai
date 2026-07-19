package com.huicai.base.voucher.service;

/**
 * 凭证号生成服务
 */
public interface VoucherNoService {

    /**
     * 生成下一个凭证号
     *
     * @param period        会计期间(YYYYMM)
     * @param voucherTypeId 凭证类型ID
     * @return 凭证号
     */
    String generateNextNo(String period, Long voucherTypeId);

    /**
     * 获取当前凭证号(不递增)
     *
     * @param period        会计期间(YYYYMM)
     * @param voucherTypeId 凭证类型ID
     * @return 当前凭证号
     */
    String getCurrentNo(String period, Long voucherTypeId);
}
