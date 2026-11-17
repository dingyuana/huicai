package com.huicai.agency.batch.service.impl;

import com.huicai.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class BatchRequestValidator {

    static final int MAX_ITEMS = 100;

    private BatchRequestValidator() {
    }

    static List<Long> requireUniqueIds(List<Long> ids, String label) {
        if (ids == null || ids.isEmpty()) {
            throw BusinessException.badRequest(label + "不能为空");
        }
        if (ids.size() > MAX_ITEMS) {
            throw BusinessException.badRequest("单次最多处理 " + MAX_ITEMS + label);
        }

        Set<Long> unique = new HashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw BusinessException.badRequest(label + "包含无效 ID");
            }
            if (!unique.add(id)) {
                throw BusinessException.badRequest(label + "包含重复 ID: " + id);
            }
        }
        return new ArrayList<>(ids);
    }

    static String requireInvoiceType(String invoiceType) {
        if (invoiceType == null) {
            throw BusinessException.badRequest("发票类型必须为 input 或 output");
        }
        String normalized = invoiceType.trim().toLowerCase(Locale.ROOT);
        if (!"input".equals(normalized) && !"output".equals(normalized)) {
            throw BusinessException.badRequest("发票类型必须为 input 或 output");
        }
        return normalized;
    }

    static String requirePeriod(String period) {
        if (period == null || !period.matches("\\d{6}")) {
            throw BusinessException.badRequest("期间格式必须为 YYYYMM");
        }
        int month = Integer.parseInt(period.substring(4, 6));
        if (month < 1 || month > 12) {
            throw BusinessException.badRequest("期间月份必须为 01 至 12");
        }
        return period;
    }
}
