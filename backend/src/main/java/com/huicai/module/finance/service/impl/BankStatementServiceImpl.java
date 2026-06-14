package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.service.ReconciliationService;
import com.huicai.module.finance.entity.BankJournalEntity;
import com.huicai.module.finance.entity.BankStatementEntity;
import com.huicai.module.finance.entity.ClassificationRuleEntity;
import com.huicai.module.finance.mapper.BankJournalMapper;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.service.BankStatementService;
import com.huicai.module.finance.service.ClassificationRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankStatementServiceImpl implements BankStatementService {

    /**
     * 导入时主动生单开关.
     * 默认 false = 严格按 SPEC FR-BANK-05/06: 导入阶段仅分类入库, 不触发单据/凭证生成.
     * 出纳在工作台 review() 确认后才触发 autoGenerate.
     * 设为 true = 导入后立即对每条流水调 autoGenerateService.autoGenerate (旧行为, 仅供运维回退).
     */
    @Value("${huicai.bank.autoGenerateOnImport:false}")
    private boolean autoGenerateOnImport;

    private final BankStatementMapper statementMapper;
    private final BankJournalMapper journalMapper;
    private final ClassificationRuleService classificationRuleService;
    private final FallbackHeuristicService fallbackHeuristic;
    private final ColumnMappingResolver columnMappingResolver;
    private final AutoGenerationService autoGenerationService;
    private final ReconciliationService reconciliationService;

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
        int aClassCount = 0, bClassCount = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (StrUtil.isBlank(line)) continue;
            String[] cols = line.split(",", -1);
            try {
                BankStatementEntity stmt = parseRow(cols, mapping, accountId);
                if (stmt != null) {
                    statementMapper.insert(stmt);
                    imported++;

                    // R1: 导入后主动生单 (老丁 2026-06-13 指示)
                    if (autoGenerateOnImport) {
                        try {
                            String type = AutoGenerationService.classifyType(stmt.getClassification());
                            if ("C".equals(type)) {
                                stmt.setReviewStatus("UNCONFIRMED");
                                statementMapper.updateById(stmt);
                            } else {
                                autoGenerationService.autoGenerate(stmt.getId(), 1L);
                                if ("A".equals(type)) aClassCount++;
                                else if ("B".equals(type)) bClassCount++;
                            }
                        } catch (Exception e) {
                            log.warn("导入后自动生单失败: statementId={}, classification={}",
                                    stmt.getId(), stmt.getClassification(), e);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析CSV第{}行失败: {}", i + 1, line, e);
            }
        }
        log.info("导入对账单: accountId={}, imported={}, aClass={}, bClass={}, autoGenerateOnImport={}",
                accountId, imported, aClassCount, bClassCount, autoGenerateOnImport);
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
            stmt.setAiConfidence(90);
            stmt.setAiSuggestedAction(null);
        } else {
            // 第三层: 兜底启发式 (永不返回 null)
            FallbackHeuristicService.Result fb = fallbackHeuristic.classify(
                    stmt.getSummary(), stmt.getDirection()
            );
            finalClassification = fb.getClassification();
            finalRuleId = null; // 兜底无规则
            stmt.setAiBusinessScene("FB:" + fb.getPriority() + ":" + fb.getMatchedKeyword());
            // 关键词兜底 (priority 1-9) = 75; 方向兜底或 pending (priority 10) = 50 需人工确认
            stmt.setAiConfidence(fb.getPriority() < 10 ? 75 : 50);
            stmt.setAiSuggestedAction(fb.getPriority() < 10 ? null : "manual_confirm");
        }

        stmt.setRuleId(finalRuleId);
        stmt.setClassification(finalClassification);
        statementMapper.updateById(stmt);
        return stmt;
    }

    @Override
    @Transactional
    public BankStatementEntity review(Long statementId) {
        BankStatementEntity stmt = statementMapper.selectById(statementId);
        if (stmt == null) {
            throw BusinessException.notFound("对账单记录不存在");
        }
        if (StrUtil.isBlank(stmt.getClassification())) {
            throw BusinessException.badRequest("流水尚未分类, 请先调用 classifySingle");
        }
        stmt.setReviewStatus("CONFIRMED");
        stmt.setReviewedBy(1L);
        stmt.setReviewedAt(LocalDateTime.now());
        statementMapper.updateById(stmt);

        // SPEC FR-BANK-05/06: 出纳确认后才触发单据/凭证生成
        // A类 (bank_fee/interest_income/tax/social_security/insurance) → 直接生成凭证
        // B类 (business_receipt/payment) → 先生成业务单据, 再生成凭证
        // C类 (internal_transfer/salary_payment/pending) → 仅生成单据或不处理
        try {
            String type = AutoGenerationService.classifyType(stmt.getClassification());
            if (!"C".equals(type)) {
                boolean ok = autoGenerationService.autoGenerateInNewTx(stmt.getId(), stmt.getReviewedBy());
                log.info("出纳确认生单: statementId={}, classification={}, type={}, ok={}",
                        statementId, stmt.getClassification(), type, ok);
            } else {
                log.info("出纳确认(C类, 不生单): statementId={}, classification={}", statementId, stmt.getClassification());
            }
        } catch (Exception e) {
            log.warn("出纳确认后生单失败: statementId={}, classification={}, err={}",
                    statementId, stmt.getClassification(), e.getMessage());
        }

        log.info("出纳确认分类: statementId={}, classification={}", statementId, stmt.getClassification());
        return stmt;
    }

    @Override
    @Transactional
    public int batchReview(List<Long> statementIds) {
        if (statementIds == null || statementIds.isEmpty()) {
            throw BusinessException.badRequest("确认 ID 列表为空");
        }
        int confirmed = 0;
        for (Long id : statementIds) {
            try {
                review(id);
                confirmed++;
            } catch (Exception e) {
                log.warn("批量确认失败: statementId={}", id, e);
            }
        }
        log.info("批量确认分类: 总数={}, 成功={}", statementIds.size(), confirmed);
        return confirmed;
    }

    @Override
    public BankStatementEntity getDetail(Long id) {
        BankStatementEntity entity = statementMapper.selectById(id);
        if (entity == null) throw BusinessException.notFound("对账单记录不存在");
        return entity;
    }

    @Override
    public void deleteStatement(Long id) {
        BankStatementEntity entity = statementMapper.selectById(id);
        if (entity == null) throw BusinessException.notFound("对账单记录不存在");
        statementMapper.deleteById(id);
        log.info("删除对账单: id={}", id);
    }

    @Override
    public BankStatementEntity updateClassification(Long id, String classification) {
        BankStatementEntity stmt = statementMapper.selectById(id);
        if (stmt == null) throw BusinessException.notFound("对账单记录不存在");
        stmt.setClassification(classification);
        stmt.setRuleId(null);
        stmt.setAiBusinessScene("MANUAL");
        statementMapper.updateById(stmt);
        log.info("手动修改分类: id={}, classification={}", id, classification);
        return stmt;
    }

    @Override
    public Map<String, Integer> classificationCounts(Long accountId, String reviewStatus) {
        if (accountId == null) return Map.of();
        List<Map<String, Object>> rows = (StrUtil.isNotBlank(reviewStatus))
                ? statementMapper.countByClassificationByReview(accountId, reviewStatus)
                : statementMapper.countByClassification(accountId);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String cls = row.get("classification") == null ? "pending" : String.valueOf(row.get("classification"));
            Number cnt = (Number) row.get("cnt");
            result.put(cls, cnt == null ? 0 : cnt.intValue());
        }
        return result;
    }

    @Override
    public ReconciliationRecommendResult reconciliationRecommend(Long statementId) {
        BankStatementEntity stmt = statementMapper.selectById(statementId);
        if (stmt == null) {
            return new ReconciliationRecommendResult("对账单记录不存在", List.of());
        }
        if (stmt.getClassification() == null) {
            return new ReconciliationRecommendResult("流水尚未分类", List.of());
        }
        String cls = stmt.getClassification();
        if (!"business_receipt".equals(cls) && !"business_payment".equals(cls)) {
            return new ReconciliationRecommendResult("当前分类(" + cls + ")不支持核销推荐", List.of());
        }
        String direction = "business_receipt".equals(cls) ? "in" : "out";
        String counterpartyName = stmt.getCounterAccount() != null ? stmt.getCounterAccount() : "";
        String summary = stmt.getSummary() != null ? stmt.getSummary() : "";

        ReconciliationService.RecommendResult recommend = reconciliationService.recommendForStatement(
                stmt.getId(), stmt.getAccountId(), direction, stmt.getAmount(),
                counterpartyName, summary);

        if (recommend.items() == null || recommend.items().isEmpty()) {
            return new ReconciliationRecommendResult("未找到匹配的应收/应付记录", List.of());
        }
        return new ReconciliationRecommendResult("推荐" + recommend.items().size() + "条核销项", recommend.items());
    }
}