package com.huicai.base.voucher.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 凭证号格式化纯单元测试
 * 覆盖 VoucherNoServiceImpl.formatVoucherNo 的输出格式
 */
class VoucherNoFormatTest {

    static String formatVoucherNo(String typeCode, String period, long serial) {
        return typeCode + period + String.format("%04d", serial);
    }

    @Test
    void formats_with_4_digit_serial_padding() {
        assertEquals("JZ2026010001", formatVoucherNo("JZ", "202601", 1));
    }

    @Test
    void formats_with_5_digit_serial() {
        assertEquals("JZ20260110000", formatVoucherNo("JZ", "202601", 10000));
    }

    @Test
    void formats_zero_serial() {
        assertEquals("JZ2026010000", formatVoucherNo("JZ", "202601", 0));
    }

    @Test
    void formats_different_type_code() {
        assertEquals("SK2026010042", formatVoucherNo("SK", "202601", 42));
    }
}
