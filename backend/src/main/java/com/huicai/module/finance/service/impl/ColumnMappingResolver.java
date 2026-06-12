package com.huicai.module.finance.service.impl;

import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 列名智能映射 — 将 CSV 表头按别名模糊匹配映射到内部字段.
 * <p>
 * 支持中英文别名, 大小写不敏感, 精确匹配优先于 contains 匹配.
 * 必含字段 {@link Field#TX_DATE} 和 {@link Field#AMOUNT} 缺失时 {@link MappingResult#isValid} 为 false.
 */
@Service
public class ColumnMappingResolver {

    /**
     * 内部字段枚举, 每个字段包含一组别名.
     */
    @Getter
    public enum Field {
        TX_DATE("交易日期", "日期", "记账日期", "date", "transaction date", "tx_date"),
        TX_TYPE("交易类型", "类型", "借贷标志", "type", "direction", "debit_credit"),
        AMOUNT("金额", "发生额", "本笔金额", "amount", "money", "value"),
        COUNTER_ACCOUNT("对方账户", "对手方", "对方账号", "counterparty", "counter account", "counter_account"),
        SUMMARY("摘要", "备注", "附言", "description", "summary", "memo", "remark"),
        EXTERNAL_NO("交易流水号", "凭证号", "流水号", "external no", "external_no", "trace no");

        private final String[] aliases;

        Field(String... aliases) {
            this.aliases = aliases;
        }
    }

    /**
     * 列名映射结果.
     */
    @Getter
    public static class MappingResult {
        private final Map<Field, Integer> fieldToColumnIndex;  // 字段 → 列索引
        private final List<String> originalHeaders;            // 原始表头
        private final List<String> warnings;                   // 警告 (未识别的字段)
        private final boolean isValid;                         // TX_DATE + AMOUNT 都识别才 true

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

    /**
     * 解析表头数组, 返回字段→列索引映射.
     * <p>
     * 匹配算法:
     * <ol>
     *   <li>遍历每个 header, lowercase + trim</li>
     *   <li>对每个 Field, 先尝试精确匹配任一 alias (case insensitive)</li>
     *   <li>未精确匹配时, 回退到 contains 匹配</li>
     *   <li>已映射的 Field 不会被后续同名字段覆盖 (先到先得)</li>
     * </ol>
     *
     * @param headers 表头字符串数组, 可为空
     * @return MappingResult, 始终非 null
     */
    public MappingResult resolve(String[] headers) {
        if (headers == null || headers.length == 0) {
            Map<Field, Integer> emptyMap = new EnumMap<>(Field.class);
            List<String> emptyHeaders = headers == null ? List.of() : Arrays.asList(headers);
            List<String> warnings = buildAllWarnings(emptyMap);
            return new MappingResult(emptyMap, emptyHeaders, warnings, false);
        }

        Map<Field, Integer> fieldToColumnIndex = new EnumMap<>(Field.class);
        boolean[] headerUsed = new boolean[headers.length];

        // 第一轮: 精确匹配 (header equals alias, case insensitive)
        for (int colIdx = 0; colIdx < headers.length; colIdx++) {
            if (headers[colIdx] == null) continue;
            String header = headers[colIdx].trim();
            if (header.isEmpty()) continue;
            String headerLower = header.toLowerCase();

            for (Field field : Field.values()) {
                if (fieldToColumnIndex.containsKey(field)) continue; // 已映射
                for (String alias : field.getAliases()) {
                    if (headerLower.equals(alias.toLowerCase())) {
                        fieldToColumnIndex.put(field, colIdx);
                        headerUsed[colIdx] = true;
                        break;
                    }
                }
                if (headerUsed[colIdx]) break;
            }
        }

        // 第二轮: contains 匹配 (header contains alias, case insensitive)
        // 只对第一轮未匹配的 header 执行
        for (int colIdx = 0; colIdx < headers.length; colIdx++) {
            if (headerUsed[colIdx] || headers[colIdx] == null) continue;
            String header = headers[colIdx].trim();
            if (header.isEmpty()) continue;
            String headerLower = header.toLowerCase();

            for (Field field : Field.values()) {
                if (fieldToColumnIndex.containsKey(field)) continue;
                for (String alias : field.getAliases()) {
                    if (headerLower.contains(alias.toLowerCase())) {
                        fieldToColumnIndex.put(field, colIdx);
                        headerUsed[colIdx] = true;
                        break;
                    }
                }
                if (headerUsed[colIdx]) break;
            }
        }

        boolean isValid = fieldToColumnIndex.containsKey(Field.TX_DATE)
                && fieldToColumnIndex.containsKey(Field.AMOUNT);

        List<String> warnings = buildAllWarnings(fieldToColumnIndex);

        return new MappingResult(
                Collections.unmodifiableMap(fieldToColumnIndex),
                Arrays.asList(headers),
                warnings,
                isValid
        );
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