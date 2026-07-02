package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 列名智能映射 — 将 CSV 表头按别名模糊匹配映射到内部字段.
 * <p>
 * 支持中英文别名, 大小写不敏感.
 * 匹配算法:
 * 1. 精确匹配 (header equals alias)
 * 2. contains 匹配, 按别名长度降序 (长别名更具体优先)
 * 3. 若字段已匹配, 新匹配的别名更短则跳过
 */
@Service
public class ColumnMappingResolver {

    @Getter
    public enum Field {
        TX_DATE("交易日期", "日期", "记账日期", "date", "transaction date", "tx_date",
                "开票日期", "发票日期", "invoice date"),
        TX_TYPE("交易类型", "类型", "借贷标志", "方向", "type", "direction", "debit_credit",
                "凭证类型", "voucher type"),
        AMOUNT("金额", "发生额", "本笔金额", "amount", "money", "value",
                "交易金额", "价税合计", "不含税金额"),
        COUNTER_ACCOUNT("对方账户", "对方名称", "对手方", "对方账号", "counterparty", "counter account", "counter_account"),
        SUMMARY("摘要", "备注", "附言", "description", "summary", "memo", "remark",
                "reference", "备注信息", "备注说明", "红冲备注", "备注内容", "发票备注"),
        PURPOSE("用途", "purpose"),
        TRANSACTION_REMARK("交易附言", "附言", "remark", "transaction_remark", "transaction remark"),
        EXTERNAL_NO("交易流水号", "凭证号", "流水号", "external no", "external_no", "trace no",
                "交易流水号", "transaction reference number", "record id"),
        PAYER_NAME("付款人名称", "payer name", "payer's name", "付款人"),
        PAYEE_NAME("收款人名称", "payee name", "payee's name", "收款人"),
        INVOICE_NO("发票号码", "发票号", "发票代码", "invoice no", "invoice number", "invoice",
                "数电发票号码", "全电发票号码"),
        SELLER_TAX_ID("销方识别号", "销方税号", "seller tax id", "seller tax no",
                "销售方纳税人识别号", "销方纳税人识别号"),
        SELLER_NAME("销方名称", "销售方名称", "seller name", "seller"),
        BUYER_TAX_ID("购方识别号", "购方税号", "buyer tax id", "buyer tax no",
                "购买方纳税人识别号", "购方纳税人识别号", "客户税号"),
        BUYER_NAME("购买方名称", "购方名称", "客户名称", "buyer name", "buyer", "customer name"),
        GOODS_NAME("货物或应税劳务名称", "商品名称", "服务名称", "货物名称", "劳务名称",
                "goods name", "service name", "product name"),
        TAX_RATE("税率", "tax rate"),
        TAX_AMOUNT("税额", "tax amount", "tax"),
        TOTAL_AMOUNT("价税合计", "total amount", "total", "价税合计金额"),
        IS_POSITIVE("是否正数发票", "是否正数", "正数发票", "positive invoice", "红冲", "红字",
                "is positive");

        private final String[] aliases;

        Field(String... aliases) {
            this.aliases = aliases;
        }
    }

    @Getter
    public static class MappingResult {
        private final Map<Field, Integer> fieldToColumnIndex;
        private final List<String> originalHeaders;
        private final List<String> warnings;
        private final boolean isValid;

        MappingResult(Map<Field, Integer> fieldToColumnIndex,
                      List<String> originalHeaders,
                      List<String> warnings,
                      boolean isValid) {
            this.fieldToColumnIndex = fieldToColumnIndex;
            this.originalHeaders = originalHeaders;
            this.warnings = warnings;
            this.isValid = isValid;
        }
    }

    public MappingResult resolve(String[] headers) {
        if (headers == null || headers.length == 0) {
            Map<Field, Integer> emptyMap = new EnumMap<>(Field.class);
            return new MappingResult(emptyMap, headers == null ? List.of() : Arrays.asList(headers),
                    buildAllWarnings(emptyMap), false);
        }

        List<Candidate> candidates = new ArrayList<>();

        // 列名标准化：去除 [English] 和（中文）后缀，再匹配
        String[] normalizedHeaders = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i];
            if (h != null) {
                h = h.replaceAll("\\s*\\[.*?\\]\\s*", "").trim();   // [English Name]
                h = h.replaceAll("\\s*（.*?）\\s*", "").trim();     // （中文文本）
                normalizedHeaders[i] = h;
            }
        }

        for (int colIdx = 0; colIdx < headers.length; colIdx++) {
            if (headers[colIdx] == null) continue;
            String header = normalizedHeaders[colIdx];
            if (header.isEmpty()) continue;
            String headerLower = header.toLowerCase();

            for (Field field : Field.values()) {
                for (String alias : field.getAliases()) {
                    String aliasLower = alias.toLowerCase();
                    if (headerLower.equals(aliasLower)) {
                        candidates.add(new Candidate(colIdx, field, Integer.MAX_VALUE)); // exact match
                    } else if (headerLower.contains(aliasLower)) {
                        candidates.add(new Candidate(colIdx, field, alias.length()));
                    }
                }
            }
        }

        // Sort: exact match first (score=MAX_VALUE), then longer alias first, then column index
        candidates.sort((a, b) -> {
            if (a.score != b.score) return b.score - a.score;  // higher score first
            if (a.colIdx != b.colIdx) return a.colIdx - b.colIdx;
            return a.field.ordinal() - b.field.ordinal();
        });

        Map<Field, Integer> fieldToColumnIndex = new EnumMap<>(Field.class);
        Map<Field, Integer> fieldToScore = new EnumMap<>(Field.class);
        boolean[] headerUsed = new boolean[headers.length];

        for (Candidate c : candidates) {
            // Column already used by an equal-or-better candidate
            if (headerUsed[c.colIdx]) continue;

            // Field already mapped
            if (fieldToColumnIndex.containsKey(c.field)) {
                // If this candidate has a better score (longer alias/exact), remap
                int existingScore = fieldToScore.getOrDefault(c.field, 0);
                if (c.score > existingScore) {
                    // Free the old column
                    int oldCol = fieldToColumnIndex.get(c.field);
                    headerUsed[oldCol] = false;
                    // Assign new
                    fieldToColumnIndex.put(c.field, c.colIdx);
                    fieldToScore.put(c.field, c.score);
                    headerUsed[c.colIdx] = true;
                }
                continue;
            }

            fieldToColumnIndex.put(c.field, c.colIdx);
            fieldToScore.put(c.field, c.score);
            headerUsed[c.colIdx] = true;
        }

        boolean isValid = fieldToColumnIndex.containsKey(Field.TX_DATE)
                && fieldToColumnIndex.containsKey(Field.AMOUNT);

        return new MappingResult(
                Collections.unmodifiableMap(fieldToColumnIndex),
                Arrays.asList(headers),
                buildAllWarnings(fieldToColumnIndex),
                isValid
        );
    }

    /**
     * 根据用户手动指定的列映射创建 MappingResult.
     * fieldToHeader: Map<Field 名称, Excel 表头原文>
     * 例如: {"TX_DATE": "交易日期", "AMOUNT": "交易金额", "TX_TYPE": "交易类型"}
     */
    public MappingResult resolveFromUserMapping(String[] headers, Map<String, String> fieldToHeader) {
        if (headers == null || headers.length == 0) {
            return new MappingResult(new EnumMap<>(Field.class), List.of(), List.of(), false);
        }

        List<String> headerList = Arrays.asList(headers);
        String[] normalizedHeaders = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i];
            if (h != null) {
                h = h.replaceAll("\\s*\\[.*?\\]\\s*", "").trim();
                h = h.replaceAll("\\s*（.*?）\\s*", "").trim();
                normalizedHeaders[i] = h;
            }
        }

        Map<Field, Integer> fieldToColumnIndex = new EnumMap<>(Field.class);
        for (Map.Entry<String, String> entry : fieldToHeader.entrySet()) {
            String fieldName = entry.getKey();
            String userHeader = entry.getValue();
            if (StrUtil.isBlank(userHeader)) continue;

            try {
                Field field = Field.valueOf(fieldName);
                for (int i = 0; i < headers.length; i++) {
                    String matchTarget = headers[i] != null ? headers[i].trim() : "";
                    if (matchTarget.equals(userHeader) ||
                            (normalizedHeaders[i] != null && normalizedHeaders[i].equals(userHeader))) {
                        fieldToColumnIndex.put(field, i);
                        break;
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        boolean isValid = fieldToColumnIndex.containsKey(Field.TX_DATE)
                && fieldToColumnIndex.containsKey(Field.AMOUNT);

        return new MappingResult(
                Collections.unmodifiableMap(fieldToColumnIndex),
                headerList,
                buildAllWarnings(fieldToColumnIndex),
                isValid
        );
    }

    private static class Candidate {
        final int colIdx;
        final Field field;
        final int score; // Integer.MAX_VALUE for exact match, alias length for contains

        Candidate(int colIdx, Field field, int score) {
            this.colIdx = colIdx;
            this.field = field;
            this.score = score;
        }
    }

    private static List<String> buildAllWarnings(Map<Field, Integer> mapping) {
        List<String> warnings = new ArrayList<>();
        for (Field field : Field.values()) {
            if (!mapping.containsKey(field)) {
                warnings.add(field.name() + " 未识别");
            }
        }
        return Collections.unmodifiableList(warnings);
    }
}