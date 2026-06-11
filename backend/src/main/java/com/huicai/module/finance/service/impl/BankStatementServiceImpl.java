package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.BankJournalEntity;
import com.huicai.module.finance.entity.BankStatementEntity;
import com.huicai.module.finance.mapper.BankJournalMapper;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.service.BankStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankStatementServiceImpl implements BankStatementService {

    private final BankStatementMapper statementMapper;
    private final BankJournalMapper journalMapper;

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
        int imported = 0;
        boolean firstLine = true;
        for (String line : lines) {
            if (StrUtil.isBlank(line)) continue;
            String[] cols = line.split(",");
            if (firstLine) {
                firstLine = false;
                // 首行可能是表头, 尝试检测
                if (cols[0].contains("日期") || cols[0].toLowerCase().contains("date")) continue;
            }
            if (cols.length < 3) continue;
            try {
                BankStatementEntity stmt = new BankStatementEntity();
                stmt.setAccountId(accountId);
                stmt.setTxDate(java.time.LocalDate.parse(cols[0].trim()));
                String typeStr = cols[1].trim();
                stmt.setTxType(typeStr.contains("收") || typeStr.toLowerCase().contains("in") ? "INCOME" : "EXPENSE");
                stmt.setAmount(new BigDecimal(cols[2].trim()));
                if (cols.length > 3) stmt.setCounterAccount(cols[3].trim());
                if (cols.length > 4) stmt.setSummary(cols[4].trim());
                stmt.setMatchStatus("UNMATCHED");
                statementMapper.insert(stmt);
                imported++;
            } catch (Exception e) {
                log.warn("解析CSV行失败: {}", line, e);
            }
        }
        log.info("导入对账单: accountId={}, imported={}", accountId, imported);
        return imported;
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
}
