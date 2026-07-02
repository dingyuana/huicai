package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.CustomerMapper;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.arap.mapper.VendorMapper;
import com.huicai.module.arap.service.ReconciliationService;
import com.huicai.module.finance.constant.BankClassification;
import com.huicai.module.finance.constant.StatementStatus;
import com.huicai.module.finance.entity.BankJournalEntity;
import com.huicai.module.finance.entity.BankStatementEntity;
import com.huicai.module.finance.entity.ClassificationRuleEntity;
import com.huicai.module.finance.mapper.BankJournalMapper;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.mapper.ClassificationRuleMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.BusinessDocEntity;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final ClassificationRuleMapper classificationRuleMapper;
    private final FallbackHeuristicService fallbackHeuristic;
    private final ColumnMappingResolver columnMappingResolver;
    private final AutoGenerationService autoGenerationService;
    private final ReconciliationService reconciliationService;
    private final VoucherMapper voucherMapper;
    private final BusinessDocMapper businessDocMapper;
    private final CustomerMapper customerMapper;
    private final VendorMapper vendorMapper;
    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;

    /**
     * 分页查询对账单.
     * <p>
     * 防御性过滤: status / classification / reviewStatus 三个字符串参数,
     * 当值为 null / blank / "__all__" 哨兵时, 不加 eq 条件 (视为"全部").
     * 之前用 {@code .eq(StrUtil.isNotBlank(...))} 在收到显式空串或哨兵时仍可能下发错误的 WHERE 条件.
     */
    @Override
    public IPage<BankStatementEntity> pageQuery(Long accountId, String status, String classification, String reviewStatus, Integer current, Integer size) {
        Page<BankStatementEntity> page = new Page<>(current == null ? 1 : current, size == null ? 20 : size);
        LambdaQueryWrapper<BankStatementEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(accountId != null, BankStatementEntity::getAccountId, accountId)
                .eq(shouldFilter(status), BankStatementEntity::getMatchStatus, status)
                .eq(shouldFilter(classification), BankStatementEntity::getClassification, classification)
                .orderByDesc(BankStatementEntity::getTxDate);
        
        if (shouldFilter(reviewStatus)) {
            String[] statuses = reviewStatus.split(",");
            if (statuses.length > 1) {
                wrapper.in(BankStatementEntity::getReviewStatus, Arrays.asList(statuses));
            } else {
                wrapper.eq(BankStatementEntity::getReviewStatus, reviewStatus);
            }
        }
        
        IPage<BankStatementEntity> result = statementMapper.selectPage(page, wrapper);
        // 填充生成结果的非持久化字段
        populateGeneratedRefs(result.getRecords());
        return result;
    }

    /**
     * 判定字符串过滤值是否应参与 WHERE 条件.
     * <p>
     * 规则: 非 null && 非空白 && 不等于 "__all__" 哨兵 → 参与过滤.
     * 用于 MyBatis-Plus LambdaQueryWrapper.eq(condition, column, val) 的 condition 位.
     */
    private static boolean shouldFilter(String val) {
        return val != null && !val.isBlank() && !"__all__".equals(val);
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

                    // P0: CSV 导入后触发分类引擎（与 Excel 导入一致）
                    try {
                        this.classifySingle(stmt.getId());
                    } catch (Exception e) {
                        log.warn("CSV导入后分类失败: statementId={}", stmt.getId(), e);
                    }

                    // R1: 导入后主动生单 (re-read stmt to get updated classification)
                    if (autoGenerateOnImport) {
                        try {
                            stmt = statementMapper.selectById(stmt.getId());
                            String type = AutoGenerationService.classifyType(stmt.getClassification());
                            if ("C".equals(type)) {
                                stmt.setReviewStatus(StatementStatus.UNCONFIRMED);
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
            if (typeStr.contains("来账") || typeStr.contains("收") || typeStr.contains("贷") || typeStr.toLowerCase().contains("in")) {
                stmt.setTxType("INCOME");
            } else if (typeStr.contains("往账") || typeStr.contains("付") || typeStr.contains("借") || typeStr.toLowerCase().contains("out")) {
                stmt.setTxType("EXPENSE");
            }
        } else {
            // 无交易类型列时, 根据金额正负推断
            if (stmt.getAmount() != null && stmt.getAmount().signum() >= 0) {
                stmt.setTxType("INCOME");
            } else if (stmt.getAmount() != null) {
                stmt.setTxType("EXPENSE");
            }
        }

        // 按方向选取对方名称: INCOME(收款) → 付款人(付钱方); EXPENSE(付款) → 收款人(收钱方)
        boolean isIncoming = "INCOME".equals(stmt.getTxType());
        String counterparty = null;
        Integer payerIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.PAYER_NAME);
        Integer payeeIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.PAYEE_NAME);
        if (isIncoming && payerIdx != null && payerIdx < cols.length) {
            counterparty = cols[payerIdx].trim();
        } else if (!isIncoming && payeeIdx != null && payeeIdx < cols.length) {
            counterparty = cols[payeeIdx].trim();
        }
        // fallback: 通用对方账号/名称列
        if (StrUtil.isBlank(counterparty)) {
            Integer counterIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.COUNTER_ACCOUNT);
            if (counterIdx != null && counterIdx < cols.length) {
                counterparty = cols[counterIdx].trim();
            }
        }
        stmt.setCounterAccount(StrUtil.isNotBlank(counterparty) ? counterparty : null);

        Integer summaryIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SUMMARY);
        if (summaryIdx != null && summaryIdx < cols.length) {
            stmt.setSummary(cols[summaryIdx].trim());
        }

        Integer purposeIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.PURPOSE);
        if (purposeIdx != null && purposeIdx < cols.length) {
            stmt.setPurpose(cols[purposeIdx].trim());
        }

        Integer remarkIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TRANSACTION_REMARK);
        if (remarkIdx != null && remarkIdx < cols.length) {
            stmt.setTransactionRemark(cols[remarkIdx].trim());
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

        // 合并多个文本字段: 业务类型 + 摘要 + 用途 + 交易附言
        String combinedText = Stream.of(stmt.getSummary(), stmt.getPurpose(), stmt.getTransactionRemark())
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(" "));

        // 第一层: 规则引擎匹配 (合并字段 + 方向 + 对方户名)
        ClassificationRuleEntity rule = classificationRuleService.match(
                combinedText, stmt.getDirection(), stmt.getCounterAccount()
        );

        // 防误判: 摘要含社保关键词但对方是商业公司 → 跳过规则, 走兜底
        if (rule != null && BankClassification.SALARY_SOCIAL.equals(rule.getClassification())
                && StrUtil.isNotBlank(stmt.getCounterAccount())
                && isCommercialEntity(stmt.getCounterAccount())) {
            rule = null;
        }

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
            // 将摘要/用途/交易附言和对方户名拼接, 使所有字段中的关键词都能参与匹配
            String fbDescription = combinedText;
            if (StrUtil.isNotBlank(stmt.getCounterAccount())) {
                fbDescription = (fbDescription != null ? fbDescription + " " : "") + stmt.getCounterAccount();
            }
            FallbackHeuristicService.Result fb = fallbackHeuristic.classify(
                    fbDescription, stmt.getDirection()
            );

            // 防误判: 兜底匹配社保但对方是商业公司 → 降级为方向兜底
            if (BankClassification.SALARY_SOCIAL.equals(fb.getClassification())
                    && StrUtil.isNotBlank(stmt.getCounterAccount())
                    && isCommercialEntity(stmt.getCounterAccount())) {
                fb = fallbackHeuristic.classify("", stmt.getDirection());
            }

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
    public BankStatementEntity review(Long statementId, Long userId) {
        BankStatementEntity stmt = statementMapper.selectById(statementId);
        if (stmt == null) {
            throw BusinessException.notFound("对账单记录不存在");
        }
        if (StrUtil.isBlank(stmt.getClassification())) {
            throw BusinessException.badRequest("流水尚未分类, 请先调用 classifySingle");
        }

        String curStatus = stmt.getReviewStatus();
        boolean canReview = curStatus == null
                || StatementStatus.PENDING.equals(curStatus)
                || StatementStatus.CLASSIFIED.equals(curStatus)
                || StatementStatus.MANUAL_PENDING.equals(curStatus)
                || StatementStatus.RECLASSIFIED.equals(curStatus);
        if (!canReview) {
            throw BusinessException.badRequest("当前状态 " + curStatus + " 无法复审, 请先撤回或等待审批");
        }

        // review() 只做审核确认，不生成凭证
        stmt.setReviewedBy(userId);
        stmt.setReviewedAt(LocalDateTime.now());
        stmt.setReviewStatus(StatementStatus.CONFIRMED);

        statementMapper.updateById(stmt);
        log.info("出纳确认: statementId={}, status=CONFIRMED", statementId);
        return stmt;
    }

    @Override
    @Transactional
    public BankStatementEntity audit(Long statementId, Long userId) {
        BankStatementEntity stmt = statementMapper.selectById(statementId);
        if (stmt == null) {
            throw BusinessException.notFound("对账单记录不存在");
        }
        if (!StatementStatus.CONFIRMED.equals(stmt.getReviewStatus())) {
            throw BusinessException.badRequest(
                "当前状态 " + stmt.getReviewStatus() + " 无法审核，请先完成出纳确认");
        }

        // 获取分类路由类型
        String type = resolveRouteType(stmt);

        // 自动生成凭证+单据（均为草稿状态）
        boolean ok = autoGenerationService.autoGenerateInNewTx(stmt.getId(), userId);
        if (!ok) {
            throw BusinessException.badRequest("自动制证失败, classification=" + stmt.getClassification());
        }

        // 重新查询获取生成后的数据
        stmt = statementMapper.selectById(stmt.getId());
        String newStatus = "A".equals(type) ? StatementStatus.VOUCHER_GENERATED : StatementStatus.PAYMENT_CREATED;
        stmt.setReviewStatus(newStatus);
        statementMapper.updateById(stmt);

        log.info("主管审核+制证: statementId={}, classification={}, status={}",
                statementId, stmt.getClassification(), newStatus);
        return stmt;
    }

    @Override
    @Transactional
    public int batchAudit(List<Long> statementIds, Long userId) {
        if (statementIds == null || statementIds.isEmpty()) {
            throw BusinessException.badRequest("ID 列表为空");
        }
        int audited = 0;
        for (Long id : statementIds) {
            try {
                audit(id, userId);
                audited++;
            } catch (Exception e) {
                log.warn("批量审核失败: statementId={}, err={}", id, e.getMessage());
            }
        }
        log.info("批量审核: 总数={}, 成功={}", statementIds.size(), audited);
        return audited;
    }

    @Override
    @Transactional
    public BankStatementEntity generateVoucher(Long statementId, Long userId) {
        BankStatementEntity stmt = statementMapper.selectById(statementId);
        if (stmt == null) {
            throw BusinessException.notFound("对账单记录不存在");
        }
        // 允许 CONFIRMED（重试场景）或 AUDITED（旧数据过渡兼容）
        String curStatus = stmt.getReviewStatus();
        if (!"AUDITED".equals(curStatus) && !StatementStatus.CONFIRMED.equals(curStatus)) {
            throw BusinessException.badRequest(
                "当前状态 " + curStatus + " 无法生成凭证，请先完成主管审核");
        }

        String type = resolveRouteType(stmt);

        boolean ok = autoGenerationService.autoGenerateInNewTx(stmt.getId(), userId);
        if (!ok) {
            throw BusinessException.badRequest("自动制证失败, classification=" + stmt.getClassification());
        }

        stmt = statementMapper.selectById(stmt.getId());
        String newStatus = "A".equals(type) ? StatementStatus.VOUCHER_GENERATED : StatementStatus.PAYMENT_CREATED;
        stmt.setReviewStatus(newStatus);
        statementMapper.updateById(stmt);

        log.info("制证完成: statementId={}, classification={}, status={}",
                statementId, stmt.getClassification(), newStatus);
        return stmt;
    }

    @Override
    @Transactional
    public int batchGenerateVouchers(List<Long> statementIds, Long userId) {
        if (statementIds == null || statementIds.isEmpty()) {
            throw BusinessException.badRequest("ID 列表为空");
        }
        int generated = 0;
        for (Long id : statementIds) {
            try {
                generateVoucher(id, userId);
                generated++;
            } catch (Exception e) {
                log.warn("批量制证失败: statementId={}, err={}", id, e.getMessage());
            }
        }
        log.info("批量制证: 总数={}, 成功={}", statementIds.size(), generated);
        return generated;
    }

    @Override
    @Transactional
    public void approve(Long statementId) {
        BankStatementEntity stmt = statementMapper.selectById(statementId);
        if (stmt == null) {
            throw BusinessException.notFound("对账单记录不存在");
        }
        String curStatus = stmt.getReviewStatus();
        if (!StatementStatus.VOUCHER_GENERATED.equals(curStatus) && !StatementStatus.PAYMENT_CREATED.equals(curStatus)) {
            throw BusinessException.badRequest("当前状态 " + curStatus + " 无法核准, 仅支持 voucher_generated/payment_created");
        }
        stmt.setReviewStatus(StatementStatus.APPROVED);
        statementMapper.updateById(stmt);
        log.info("核准过账: statementId={}", statementId);
    }

    @Override
    @Transactional
    public BankStatementEntity processManual(Long statementId, String targetType, String paymentType, Long userId) {
        BankStatementEntity stmt = statementMapper.selectById(statementId);
        if (stmt == null) {
            throw BusinessException.notFound("对账单记录不存在");
        }
        if (!StatementStatus.MANUAL_PENDING.equals(stmt.getReviewStatus())) {
            throw BusinessException.badRequest("当前状态 " + stmt.getReviewStatus() + " 无法人工处理, 仅支持 manual_pending");
        }
        if (!"A".equals(targetType) && !"B".equals(targetType)) {
            throw BusinessException.badRequest("targetType 必须为 A 或 B");
        }

        stmt.setReviewedBy(userId);
        stmt.setReviewedAt(LocalDateTime.now());

        switch (targetType) {
            case "A":
                try {
                    boolean ok = autoGenerationService.autoGenerateInNewTx(stmt.getId(), stmt.getReviewedBy());
                    stmt.setReviewStatus(ok ? StatementStatus.VOUCHER_GENERATED : StatementStatus.MANUAL_PENDING);
                    log.info("人工指定A类: statementId={}, ok={}", statementId, ok);
                } catch (Exception e) {
                    log.warn("人工指定A类失败: statementId={}, err={}", statementId, e.getMessage());
                    throw BusinessException.badRequest("A类制证失败: " + e.getMessage());
                }
                break;
            case "B":
                try {
                    boolean ok = autoGenerationService.autoGenerateInNewTx(stmt.getId(), stmt.getReviewedBy());
                    stmt.setReviewStatus(ok ? StatementStatus.PAYMENT_CREATED : StatementStatus.MANUAL_PENDING);
                    log.info("人工指定B类: statementId={}, paymentType={}, ok={}", statementId, paymentType, ok);
                } catch (Exception e) {
                    log.warn("人工指定B类失败: statementId={}, err={}", statementId, e.getMessage());
                    throw BusinessException.badRequest("B类生单失败: " + e.getMessage());
                }
                break;
        }

        statementMapper.updateById(stmt);
        log.info("人工处理完成: statementId={}, targetType={}", statementId, targetType);
        return stmt;
    }

    @Override
    public List<BankStatementService.PreviewEntry> previewDraft(Long statementId) {
        BankStatementEntity stmt = statementMapper.selectById(statementId);
        if (stmt == null) {
            throw BusinessException.notFound("对账单记录不存在");
        }
        if (StrUtil.isBlank(stmt.getClassification())) {
            throw BusinessException.badRequest("流水尚未分类, 无法预览");
        }

        BigDecimal amount = stmt.getAmount();
        String direction = stmt.getDirection();
        List<BankStatementService.PreviewEntry> entries = new ArrayList<>();

        // 按分类计算分录: 复用 AutoGenerationService 中硬编码的分录逻辑
        switch (stmt.getClassification()) {
            case BankClassification.BANK_INTEREST_FEE:
                if ("in".equals(direction)) {
                    entries.add(new BankStatementService.PreviewEntry("debit", "1002", "银行存款", amount, stmt.getSummary()));
                    entries.add(new BankStatementService.PreviewEntry("credit", "6602.02", "利息收入", amount, stmt.getSummary()));
                } else {
                    entries.add(new BankStatementService.PreviewEntry("debit", "6602.01", "手续费", amount, stmt.getSummary()));
                    entries.add(new BankStatementService.PreviewEntry("credit", "1002", "银行存款", amount, stmt.getSummary()));
                }
                break;
            case BankClassification.TAX_WITHHOLDING:
                entries.add(new BankStatementService.PreviewEntry("debit", "2221", "应交税费", amount, stmt.getSummary()));
                entries.add(new BankStatementService.PreviewEntry("credit", "1002", "银行存款", amount, stmt.getSummary()));
                break;
            case BankClassification.SALARY_SOCIAL:
                entries.add(new BankStatementService.PreviewEntry("debit", "2211", "应付职工薪酬", amount, stmt.getSummary()));
                entries.add(new BankStatementService.PreviewEntry("credit", "1002", "银行存款", amount, stmt.getSummary()));
                break;
            case BankClassification.BUSINESS_RECEIPT:
                entries.add(new BankStatementService.PreviewEntry("debit", "1002", "银行存款", amount, stmt.getSummary()));
                entries.add(new BankStatementService.PreviewEntry("credit", "1122", "应收账款", amount, stmt.getSummary()));
                break;
            case BankClassification.BUSINESS_PAYMENT:
                entries.add(new BankStatementService.PreviewEntry("debit", "2202", "应付账款", amount, stmt.getSummary()));
                entries.add(new BankStatementService.PreviewEntry("credit", "1002", "银行存款", amount, stmt.getSummary()));
                break;
            case BankClassification.INTERNAL_TRANSFER:
                entries.add(new BankStatementService.PreviewEntry("debit", "1012", "其他货币资金", amount, stmt.getSummary()));
                entries.add(new BankStatementService.PreviewEntry("credit", "1002", "银行存款", amount, stmt.getSummary()));
                break;
            default:
                throw BusinessException.badRequest("分类 " + stmt.getClassification() + " 不支持预览生成");
        }

        return entries;
    }

    @Override
    @Transactional
    public int batchReview(List<Long> statementIds, Long userId) {
        if (statementIds == null || statementIds.isEmpty()) {
            throw BusinessException.badRequest("确认 ID 列表为空");
        }
        int confirmed = 0;
        for (Long id : statementIds) {
            try {
                review(id, userId);
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
        populateGeneratedRefs(List.of(entity));
        return entity;
    }

    @Override
    public void deleteStatement(Long id) {
        BankStatementEntity entity = statementMapper.selectById(id);
        if (entity == null) throw BusinessException.notFound("对账单记录不存在");
        if (isLocked(entity.getReviewStatus())) {
            throw BusinessException.badRequest(
                "当前状态 " + entity.getReviewStatus() + " 不允许删除，请先撤回");
        }
        statementMapper.deleteById(id);
        log.info("删除对账单: id={}", id);
    }

    @Override
    public BankStatementEntity updateClassification(Long id, String classification) {
        BankStatementEntity stmt = statementMapper.selectById(id);
        if (stmt == null) throw BusinessException.notFound("对账单记录不存在");
        if (isLocked(stmt.getReviewStatus())) {
            throw BusinessException.badRequest(
                "当前状态 " + stmt.getReviewStatus() + " 不允许修改分类，请先撤回");
        }
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
            String cls = row.get("classification") == null ? BankClassification.OTHER_UNKNOWN : String.valueOf(row.get("classification"));
            Number cnt = (Number) row.get("cnt");
            result.put(cls, cnt == null ? 0 : cnt.intValue());
        }
        return result;
    }

    /** 判断状态是否已锁定（不可删除/修改分类） */
    private static boolean isLocked(String reviewStatus) {
        return StatementStatus.CONFIRMED.equals(reviewStatus)
            || StatementStatus.VOUCHER_GENERATED.equals(reviewStatus)
            || StatementStatus.PAYMENT_CREATED.equals(reviewStatus)
            || StatementStatus.APPROVED.equals(reviewStatus);
    }

    /** 解析路由类型 A/B/C：优先取匹配规则中的 routeType，其次按 classification 默认推断 */
    private String resolveRouteType(BankStatementEntity stmt) {
        String type = null;
        if (stmt.getRuleId() != null) {
            ClassificationRuleEntity rule = classificationRuleMapper.selectById(stmt.getRuleId());
            if (rule != null && StrUtil.isNotBlank(rule.getRouteType())) {
                type = rule.getRouteType();
            }
        }
        if (type == null) {
            type = AutoGenerationService.classifyType(stmt.getClassification());
        }
        return type;
    }

    /** 判断对方户名是否为商业公司 (用于社保关键词防误判) */
    private static boolean isCommercialEntity(String name) {
        return name.contains("有限公司")
                || name.contains("有限责任公司")
                || name.contains("集团有限公司");
    }

    /**
     * 批量填充 generatedVoucherNo / generatedDocNo 等非持久化前端展示字段.
     * 根据 generatedVoucherId 查 t_voucher.voucher_no, 根据 generatedDocId 查 t_business_doc.doc_no.
     */
    private void populateGeneratedRefs(List<BankStatementEntity> records) {
        // 凭证号
        List<Long> voucherIds = records.stream()
                .map(BankStatementEntity::getGeneratedVoucherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!voucherIds.isEmpty()) {
            Map<Long, String> voucherMap = voucherMapper.selectBatchIds(voucherIds).stream()
                    .collect(Collectors.toMap(VoucherEntity::getId, VoucherEntity::getVoucherNo));
            records.stream()
                    .filter(r -> r.getGeneratedVoucherId() != null)
                    .forEach(r -> r.setGeneratedVoucherNo(voucherMap.get(r.getGeneratedVoucherId())));
        }
        // 单据号
        List<Long> docIds = records.stream()
                .map(BankStatementEntity::getGeneratedDocId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!docIds.isEmpty()) {
            Map<Long, String> docMap = businessDocMapper.selectBatchIds(docIds).stream()
                    .collect(Collectors.toMap(BusinessDocEntity::getId, BusinessDocEntity::getDocNo));
            records.stream()
                    .filter(r -> r.getGeneratedDocId() != null)
                    .forEach(r -> r.setGeneratedDocNo(docMap.get(r.getGeneratedDocId())));
        }
    }
}
