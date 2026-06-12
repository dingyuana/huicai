package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.BankJournalEntity;
import com.huicai.module.finance.entity.BankStatementEntity;
import com.huicai.module.finance.entity.ClassificationRuleEntity;
import com.huicai.module.finance.mapper.BankJournalMapper;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.service.BankStatementService;
import com.huicai.module.finance.service.ClassificationRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankStatementServiceImpl implements BankStatementService {

    private final BankStatementMapper statementMapper;
    private final BankJournalMapper journalMapper;
    private final ClassificationRuleService classificationRuleService;
    private final FallbackHeuristicService fallbackHeuristic;
    private final ColumnMappingResolver columnMappingResolver;

    @Override
    public IPage<BankStatementEntity> pageQuery(Long accountId, String status, Integer current, Integer size) {
        Page<BankStatementEntity> page = new Page<>(current == null ? 1 : current, size == null ? 20 : size);
        LambdaQueryWrapper<BankStatementEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(accountId != null, BankStatementEntity::getAccountId, accountId)
                .eq(StrUtil.isNotBlank(status), BankStatementEntity::getMatchStatus, status)
                .orderByDesc(BankStatementEntity::getTxDate);
        return statementMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional
    public int importFromCsv(Long accountId, String csvContent) {
        if (StrUtil.isBlank(csvContent)) {
            throw BusinessException.badRequest("CSV内容为空");
        }
        String[] lines = csvContent.split("\\r?\\n");
        if (lines.length == 0) return 0;

        // 1. 解析表头 (智能映射)
        String[] headers = lines[0].split(",");
        ColumnMappingResolver.MappingResult mapping = columnMappingResolver.resolve(headers);

        // 2. 预校验
        if (!mapping.isValid()) {
            throw BusinessException.badRequest(
                    "必含列名缺失 (交易日期/金额). 实际表头: " + String.join(",", headers)
            );
        }

        // 3. 逐行解析导入
        int imported = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (StrUtil.isBlank(line)) continue;
            String[] cols = line.split(",", -1);
            try {
                BankStatementEntity stmt = parseRow(cols, mapping, accountId);
                if (stmt != null) {
                    statementMapper.insert(stmt);
                    imported++;
                }
            } catch (Exception e) {
                log.warn("解析CSV第{}行失败: {}", i + 1, line, e);
            }
        }
        log.info("导入对账单: accountId={}, imported={}", accountId, imported);
        return imported;
    }

    /**
     * 按列映射从 CSV 行解析一条对账单记录.
     *
     * @param cols    CSV 分割后的列数组
     * @param mapping 列名映射结果
     * @param accountId 银行账户 ID
     * @return 解析后的实体, 日期或金额缺失时返回 null
     */
    private BankStatementEntity parseRow(String[] cols, ColumnMappingResolver.MappingResult mapping, Long accountId) {
        BankStatementEntity stmt = new BankStatementEntity();
        stmt.setAccountId(accountId);
        stmt.setMatchStatus("UNMATCHED");

        Integer dateIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE);
        if (dateIdx != null && dateIdx < cols.length) {
            stmt.setTxDate(LocalDate.parse(cols[dateIdx].trim()));
        } else {
            return null;
        }

        Integer amtIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT);
        if (amtIdx != null && amtIdx < cols.length) {
            stmt.setAmount(new BigDecimal(cols[amtIdx].trim()));
        } else {
            return null;
        }

        Integer typeIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_TYPE);
        if (typeIdx != null && typeIdx < cols.length) {
            String typeStr = cols[typeIdx].trim();
            stmt.setTxType(typeStr.contains("收") || typeStr.toLowerCase().contains("in") || typeStr.contains("贷") ? "INCOME" : "EXPENSE");
        }

        Integer counterIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.COUNTER_ACCOUNT);
        if (counterIdx != null && counterIdx < cols.length) {
            stmt.setCounterAccount(cols[counterIdx].trim());
        }

        Integer summaryIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SUMMARY);
        if (summaryIdx != null && summaryIdx < cols.length) {
            stmt.setSummary(cols[summaryIdx].trim());
        }

        Integer extIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.EXTERNAL_NO);
        if (extIdx != null && extIdx < cols.length) {
            stmt.setExternalNo(cols[extIdx].trim());
        }

        return stmt;
    }

    @Override
    public List<Map<String, Object>> autoMatch(Long accountId) {
        List<BankStatementEntity> stmts = listUnmatched(accountId);
        List<BankJournalEntity> journals = journalMapper.selectUnreconciled(accountId);

        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (BankStatementEntity stmt : stmts) {
            BankJournalEntity best = null;
            double bestScore = 0;
            for (BankJournalEntity j : journals) {
                if (!stmt.getTxDate().equals(j.getTxDate())) continue;
                if (j.getVoucherId() == null) continue;
                if (!stmt.getAmount().setScale(2, RoundingMode.HALF_UP)
                        .equals(j.getAmount().setScale(2, RoundingMode.HALF_UP))) continue;
                double score = 1.0;
                if (stmt.getCounterAccount() != null && j.getCounterAccount() != null
                        && stmt.getCounterAccount().equals(j.getCounterAccount())) {
                    score += 0.1;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = j;
                }
            }
            Map<String, Object> s = new HashMap<>();
            s.put("statementId", stmt.getId());
            s.put("txDate", stmt.getTxDate());
            s.put("amount", stmt.getAmount());
            s.put("counterAccount", stmt.getCounterAccount());
            s.put("matchedJournalId", best != null ? best.getId() : null);
            s.put("score", bestScore);
            suggestions.add(s);
        }
        return suggestions;
    }

    @Override
    @Transactional
    public int confirmMatch(Long statementId, Long journalId) {
        BankStatementEntity stmt = statementMapper.selectById(statementId);
        if (stmt == null) throw BusinessException.notFound("对账单记录不存在");
        BankJournalEntity journal = journalMapper.selectById(journalId);
        if (journal == null) throw BusinessException.notFound("日记账记录不存在");
        int n = statementMapper.updateMatch(statementId, journalId, "MATCHED");
        journalMapper.updateReconciled(journalId, true);
        log.info("对账匹配确认: statementId={}, journalId={}", statementId, journalId);
        return n;
    }

    @Override
    @Transactional
    public int ignoreStatement(Long statementId) {
        return statementMapper.updateMatch(statementId, null, "IGNORED");
    }

    @Override
    public List<BankStatementEntity> listUnmatched(Long accountId) {
        return statementMapper.selectByAccountAndStatus(accountId, "UNMATCHED");
    }

    @Override
    @Transactional
    public BankStatementEntity classifySingle(Long statementId) {
        BankStatementEntity stmt = statementMapper.selectById(statementId);
        if (stmt == null) throw BusinessException.notFound("对账单记录不存在");

        // 第一层: 规则引擎匹配
        ClassificationRuleEntity rule = classificationRuleService.match(
                stmt.getSummary(), stmt.getDirection()
        );

        String finalClassification;
        Long finalRuleId;

        if (rule != null) {
            // 规则命中
            finalClassification = rule.getClassification();
            finalRuleId = rule.getId();
            stmt.setAiBusinessScene(null); // 规则命中时清除兜底标记
        } else {
            // 第三层: 兜底启发式 (永不返回 null)
            FallbackHeuristicService.Result fb = fallbackHeuristic.classify(
                    stmt.getSummary(), stmt.getDirection()
            );
            finalClassification = fb.getClassification();
            finalRuleId = null; // 兜底无规则
            // 兜底命中的信息写入 ai_business_scene 便于调试追溯
            stmt.setAiBusinessScene("FB:" + fb.getPriority() + ":" + fb.getMatchedKeyword());
        }

        stmt.setRuleId(finalRuleId);
        stmt.setClassification(finalClassification);
        statementMapper.updateById(stmt);
        return stmt;
    }
}