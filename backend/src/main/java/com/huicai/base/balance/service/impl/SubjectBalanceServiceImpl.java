package com.huicai.base.balance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.dto.SubjectBalanceVO;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.balance.mapper.SubjectBalanceMapper;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.service.SubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 科目余额更新服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectBalanceServiceImpl implements SubjectBalanceService {

    private final SubjectBalanceMapper subjectBalanceMapper;
    private final SubjectService subjectService;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateBalanceOnPost(VoucherEntity voucher, List<VoucherEntryEntity> entries) {
        String period = voucher.getPeriod();
        int year = Integer.parseInt(period.substring(0, 4));

        for (VoucherEntryEntity entry : entries) {
            Subject subject = subjectService.getById(entry.getSubjectId());
            if (subject == null) {
                throw BusinessException.notFound("科目不存在: " + entry.getSubjectId());
            }

            if (!Boolean.TRUE.equals(subject.getIsLeaf())) {
                continue;
            }

            SubjectBalanceEntity balance = findOrCreate(subject.getId(), period, year);

            BigDecimal newDebitTotal = balance.getDebitTotal().add(entry.getDebit());
            BigDecimal newCreditTotal = balance.getCreditTotal().add(entry.getCredit());
            balance.setDebitTotal(newDebitTotal);
            balance.setCreditTotal(newCreditTotal);

            BigDecimal endBalance;
            if ("debit".equals(subject.getDirection())) {
                endBalance = balance.getBeginBalance().add(newDebitTotal).subtract(newCreditTotal);
            } else {
                endBalance = balance.getBeginBalance().add(newCreditTotal).subtract(newDebitTotal);
            }
            balance.setEndBalance(endBalance);

            subjectBalanceMapper.updateById(balance);
            log.info("更新科目余额: subjectId={}, period={}, debitTotal={}, creditTotal={}, endBalance={}",
                    subject.getId(), period, newDebitTotal, newCreditTotal, endBalance);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initOpeningBalances(String period, Map<Long, BigDecimal> balances) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }
        if (balances == null || balances.isEmpty()) {
            throw BusinessException.badRequest("期初余额数据不能为空");
        }

        int year = Integer.parseInt(period.substring(0, 4));

        LambdaQueryWrapper<SubjectBalanceEntity> existsWrapper = new LambdaQueryWrapper<>();
        existsWrapper.eq(SubjectBalanceEntity::getPeriod, period);
        Long existing = subjectBalanceMapper.selectCount(existsWrapper);
        if (existing > 0) {
            throw BusinessException.conflict("期间 " + period + " 已存在余额数据, 请先清空");
        }

        BigDecimal totalDebitSide = BigDecimal.ZERO;
        BigDecimal totalCreditSide = BigDecimal.ZERO;
        List<SubjectBalanceEntity> batchList = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : balances.entrySet()) {
            Long subjectId = entry.getKey();
            BigDecimal opening = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();

            Subject subject = subjectService.getById(subjectId);
            if (subject == null) {
                throw BusinessException.notFound("科目不存在: " + subjectId);
            }
            if (!Boolean.TRUE.equals(subject.getIsLeaf())) {
                throw BusinessException.badRequest("非末级科目不允许录入期初余额: " + subject.getCode());
            }

            if ("debit".equals(subject.getDirection())) {
                totalDebitSide = totalDebitSide.add(opening);
            } else {
                totalCreditSide = totalCreditSide.add(opening);
            }

            SubjectBalanceEntity balance = new SubjectBalanceEntity();
            balance.setSubjectId(subjectId);
            balance.setYear(year);
            balance.setPeriod(period);
            balance.setBeginBalance(opening);
            balance.setDebitTotal(BigDecimal.ZERO);
            balance.setCreditTotal(BigDecimal.ZERO);
            balance.setEndBalance(opening);
            batchList.add(balance);
        }

        if (totalDebitSide.compareTo(totalCreditSide) != 0) {
            throw BusinessException.badRequest(
                    "期初试算不平衡: 借方合计=" + totalDebitSide + ", 贷方合计=" + totalCreditSide);
        }

        for (SubjectBalanceEntity balance : batchList) {
            subjectBalanceMapper.insert(balance);
        }
        log.info("期初建账完成: period={}, 科目数={}, 借方合计={}, 贷方合计={}",
                period, batchList.size(), totalDebitSide, totalCreditSide);
    }

    @Override
    public List<SubjectBalanceEntity> queryByPeriod(String period) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }
        LambdaQueryWrapper<SubjectBalanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubjectBalanceEntity::getPeriod, period)
                .orderByAsc(SubjectBalanceEntity::getSubjectId);
        return subjectBalanceMapper.selectList(wrapper);
    }

    @Override
    public List<SubjectBalanceVO> queryByPeriodWithSubject(String period) {
        List<SubjectBalanceEntity> balances = queryByPeriod(period);
        if (balances.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> subjectIds = balances.stream()
                .map(SubjectBalanceEntity::getSubjectId)
                .collect(Collectors.toSet());
        Map<Long, Subject> subjectMap = subjectService.listByIds(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, s -> s, (a, b) -> a));

        List<SubjectBalanceVO> result = new ArrayList<>(balances.size());
        for (SubjectBalanceEntity balance : balances) {
            SubjectBalanceVO vo = new SubjectBalanceVO();
            vo.setId(balance.getId());
            vo.setSubjectId(balance.getSubjectId());
            vo.setYear(balance.getYear());
            vo.setPeriod(balance.getPeriod());
            vo.setBeginBalance(balance.getBeginBalance());
            vo.setDebitTotal(balance.getDebitTotal());
            vo.setCreditTotal(balance.getCreditTotal());
            vo.setEndBalance(balance.getEndBalance());
            vo.setCreatedAt(balance.getCreatedAt());
            vo.setUpdatedAt(balance.getUpdatedAt());

            Subject subject = subjectMap.get(balance.getSubjectId());
            if (subject != null) {
                vo.setSubjectCode(subject.getCode());
                vo.setSubjectName(subject.getName());
                vo.setDirection(subject.getDirection());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public Map<String, Object> checkTrialBalance(String period) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }

        List<SubjectBalanceEntity> balances = queryByPeriod(period);

        BigDecimal totalBeginDebit = BigDecimal.ZERO;
        BigDecimal totalBeginCredit = BigDecimal.ZERO;
        BigDecimal totalDebitTotal = BigDecimal.ZERO;
        BigDecimal totalCreditTotal = BigDecimal.ZERO;
        BigDecimal totalEndDebit = BigDecimal.ZERO;
        BigDecimal totalEndCredit = BigDecimal.ZERO;

        for (SubjectBalanceEntity balance : balances) {
            Subject subject = subjectService.getById(balance.getSubjectId());
            if (subject == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(subject.getIsLeaf())) {
                continue;
            }

            BigDecimal begin = balance.getBeginBalance() == null ? BigDecimal.ZERO : balance.getBeginBalance();
            BigDecimal debit = balance.getDebitTotal() == null ? BigDecimal.ZERO : balance.getDebitTotal();
            BigDecimal credit = balance.getCreditTotal() == null ? BigDecimal.ZERO : balance.getCreditTotal();
            BigDecimal end = balance.getEndBalance() == null ? BigDecimal.ZERO : balance.getEndBalance();

            if ("debit".equals(subject.getDirection())) {
                totalBeginDebit = totalBeginDebit.add(begin);
                totalDebitTotal = totalDebitTotal.add(debit);
                totalCreditTotal = totalCreditTotal.add(credit);
                if (end.compareTo(BigDecimal.ZERO) >= 0) {
                    totalEndDebit = totalEndDebit.add(end);
                } else {
                    totalEndCredit = totalEndCredit.add(end.abs());
                }
            } else {
                totalBeginCredit = totalBeginCredit.add(begin);
                totalDebitTotal = totalDebitTotal.add(debit);
                totalCreditTotal = totalCreditTotal.add(credit);
                if (end.compareTo(BigDecimal.ZERO) >= 0) {
                    totalEndCredit = totalEndCredit.add(end);
                } else {
                    totalEndDebit = totalEndDebit.add(end.abs());
                }
            }
        }

        boolean beginBalanced = totalBeginDebit.compareTo(totalBeginCredit) == 0;
        boolean movementBalanced = totalDebitTotal.compareTo(totalCreditTotal) == 0;
        boolean endBalanced = totalEndDebit.compareTo(totalEndCredit) == 0;

        Map<String, Object> result = new HashMap<>();
        result.put("period", period);
        result.put("beginBalanced", beginBalanced);
        result.put("movementBalanced", movementBalanced);
        result.put("endBalanced", endBalanced);
        result.put("totalBeginDebit", totalBeginDebit);
        result.put("totalBeginCredit", totalBeginCredit);
        result.put("totalDebitTotal", totalDebitTotal);
        result.put("totalCreditTotal", totalCreditTotal);
        result.put("totalEndDebit", totalEndDebit);
        result.put("totalEndCredit", totalEndCredit);
        result.put("balanced", beginBalanced && movementBalanced && endBalanced);
        return result;
    }

    private SubjectBalanceEntity findOrCreate(Long subjectId, String period, int year) {
        LambdaQueryWrapper<SubjectBalanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubjectBalanceEntity::getSubjectId, subjectId)
                .eq(SubjectBalanceEntity::getPeriod, period);
        SubjectBalanceEntity balance = subjectBalanceMapper.selectOne(wrapper);

        if (balance == null) {
            BigDecimal beginBalance = getPreviousEndBalance(subjectId, period);

            balance = new SubjectBalanceEntity();
            balance.setSubjectId(subjectId);
            balance.setYear(year);
            balance.setPeriod(period);
            balance.setBeginBalance(beginBalance);
            balance.setDebitTotal(BigDecimal.ZERO);
            balance.setCreditTotal(BigDecimal.ZERO);
            balance.setEndBalance(beginBalance);
            subjectBalanceMapper.insert(balance);
        }

        return balance;
    }

    private BigDecimal getPreviousEndBalance(Long subjectId, String period) {
        int year = Integer.parseInt(period.substring(0, 4));
        int month = Integer.parseInt(period.substring(4, 6));

        while (true) {
            if (month == 1) {
                year--;
                month = 12;
            } else {
                month--;
            }
            String prevPeriod = String.format("%04d%02d", year, month);

            LambdaQueryWrapper<SubjectBalanceEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SubjectBalanceEntity::getSubjectId, subjectId)
                    .eq(SubjectBalanceEntity::getPeriod, prevPeriod);
            SubjectBalanceEntity prev = subjectBalanceMapper.selectOne(wrapper);

            if (prev != null) {
                return prev.getEndBalance();
            }

            if (month == 12 && year == Integer.parseInt(period.substring(0, 4)) - 1) {
                return BigDecimal.ZERO;
            }
        }
    }
}
