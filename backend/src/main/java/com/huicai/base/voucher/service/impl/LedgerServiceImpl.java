package com.huicai.base.voucher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.balance.mapper.SubjectBalanceMapper;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.service.LedgerService;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private final SubjectBalanceMapper subjectBalanceMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final SubjectService subjectService;

    @Override
    public List<Map<String, Object>> subjectBalance(String period) {
        List<SubjectBalanceEntity> balances = subjectBalanceMapper.selectList(
                new LambdaQueryWrapper<SubjectBalanceEntity>()
                        .eq(SubjectBalanceEntity::getPeriod, period)
                        .orderByAsc(SubjectBalanceEntity::getSubjectId));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SubjectBalanceEntity b : balances) {
            Subject subject = subjectService.getById(b.getSubjectId());
            if (subject == null || !Boolean.TRUE.equals(subject.getIsLeaf())) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("subjectId", b.getSubjectId());
            row.put("subjectCode", subject.getCode());
            row.put("subjectName", subject.getName());
            row.put("direction", subject.getDirection());
            row.put("beginBalance", b.getBeginBalance());
            row.put("debitTotal", b.getDebitTotal());
            row.put("creditTotal", b.getCreditTotal());
            row.put("endBalance", b.getEndBalance());
            rows.add(row);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> generalLedger(Long subjectId, String period) {
        Subject subject = subjectService.getById(subjectId);
        if (subject == null) {
            return new ArrayList<>();
        }
        boolean isDebit = "debit".equals(subject.getDirection());

        SubjectBalanceEntity balance = subjectBalanceMapper.selectOne(
                new LambdaQueryWrapper<SubjectBalanceEntity>()
                        .eq(SubjectBalanceEntity::getSubjectId, subjectId)
                        .eq(SubjectBalanceEntity::getPeriod, period));

        List<VoucherEntryEntity> entries = voucherEntryMapper.selectList(
                new LambdaQueryWrapper<VoucherEntryEntity>()
                        .eq(VoucherEntryEntity::getSubjectId, subjectId)
                        .orderByAsc(VoucherEntryEntity::getId));

        List<Map<String, Object>> rows = new ArrayList<>();

        Map<String, Object> opening = new HashMap<>();
        opening.put("type", "OPENING");
        opening.put("summary", "期初余额");
        opening.put("debit", BigDecimal.ZERO);
        opening.put("credit", BigDecimal.ZERO);
        BigDecimal running = balance == null ? BigDecimal.ZERO : balance.getBeginBalance();
        opening.put("running", running);
        rows.add(opening);

        for (VoucherEntryEntity e : entries) {
            BigDecimal d = e.getDebit() == null ? BigDecimal.ZERO : e.getDebit();
            BigDecimal c = e.getCredit() == null ? BigDecimal.ZERO : e.getCredit();
            if (isDebit) {
                running = running.add(d).subtract(c);
            } else {
                running = running.add(c).subtract(d);
            }
            Map<String, Object> row = new HashMap<>();
            row.put("type", "ENTRY");
            row.put("voucherId", e.getVoucherId());
            row.put("summary", e.getSummary());
            row.put("debit", d);
            row.put("credit", c);
            row.put("running", running);
            rows.add(row);
        }

        Map<String, Object> closing = new HashMap<>();
        closing.put("type", "CLOSING");
        closing.put("summary", "本期合计");
        BigDecimal totalD = BigDecimal.ZERO;
        BigDecimal totalC = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            if ("ENTRY".equals(r.get("type"))) {
                totalD = totalD.add((BigDecimal) r.get("debit"));
                totalC = totalC.add((BigDecimal) r.get("credit"));
            }
        }
        closing.put("debit", totalD);
        closing.put("credit", totalC);
        closing.put("running", running);
        rows.add(closing);

        return rows;
    }

    @Override
    public List<Map<String, Object>> subsidiaryLedger(Long subjectId, String period) {
        Subject subject = subjectService.getById(subjectId);
        if (subject == null) {
            return new ArrayList<>();
        }

        List<VoucherEntryEntity> entries = voucherEntryMapper.selectList(
                new LambdaQueryWrapper<VoucherEntryEntity>()
                        .eq(VoucherEntryEntity::getSubjectId, subjectId)
                        .orderByAsc(VoucherEntryEntity::getId));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (VoucherEntryEntity e : entries) {
            Map<String, Object> row = new HashMap<>();
            row.put("voucherId", e.getVoucherId());
            row.put("subjectId", e.getSubjectId());
            row.put("subjectCode", subject.getCode());
            row.put("subjectName", subject.getName());
            row.put("summary", e.getSummary());
            row.put("debit", e.getDebit());
            row.put("credit", e.getCredit());
            row.put("assistJson", e.getAssistJson());
            rows.add(row);
        }
        return rows;
    }
}
