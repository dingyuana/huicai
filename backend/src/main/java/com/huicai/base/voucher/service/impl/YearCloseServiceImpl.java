package com.huicai.base.voucher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.mapper.SubjectBalanceMapper;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.voucher.service.YearCloseService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class YearCloseServiceImpl implements YearCloseService {

    private final PeriodService periodService;
    private final SubjectBalanceService subjectBalanceService;
    private final SubjectBalanceMapper subjectBalanceMapper;

    @Override
    public Map<String, Object> checkBeforeYearClose(Integer year) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<PeriodEntity> periods = getYearPeriods(year);

        List<Integer> unclosedMonths = new ArrayList<>();
        for (PeriodEntity p : periods) {
            if (!"closed".equals(p.getStatus())) {
                unclosedMonths.add(p.getMonth());
            }
        }

        boolean allClosed = unclosedMonths.isEmpty();
        result.put("year", year);
        result.put("allClosed", allClosed);
        result.put("unclosedMonths", unclosedMonths);
        result.put("message", allClosed
                ? "全年 12 个月均已结账，可执行年结"
                : "以下月份未结账：" + unclosedMonths);
        return result;
    }

    @Override
    public void yearClose(Integer year) {
        List<PeriodEntity> periods = getYearPeriods(year);
        if (periods.size() != 12) {
            throw BusinessException.badRequest("年度 " + year + " 期间数量不为 12，实际 " + periods.size());
        }

        // 1. 校验全年已结账
        for (PeriodEntity p : periods) {
            if (!"closed".equals(p.getStatus())) {
                throw BusinessException.badRequest(year + " 年 " + p.getMonth() + " 月未结账，无法年结");
            }
        }

        // 2. 获取 12 月期末余额
        String decPeriod = String.format("%04d12", year);
        List<SubjectBalanceEntity> decBalances = subjectBalanceMapper.selectList(
                new LambdaQueryWrapper<SubjectBalanceEntity>()
                        .eq(SubjectBalanceEntity::getPeriod, decPeriod)
        );
        Map<Long, BigDecimal> openingBalances = new HashMap<>();
        for (SubjectBalanceEntity b : decBalances) {
            if (b.getEndBalance() != null && b.getEndBalance().signum() != 0) {
                openingBalances.put(b.getSubjectId(), b.getEndBalance());
            }
        }

        // 3. 锁定全年期间
        for (PeriodEntity p : periods) {
            periodService.lockPeriod(p.getId());
        }

        // 4. 生成次年度 1-12 月期间
        int nextYear = year + 1;
        for (int month = 1; month <= 12; month++) {
            PeriodEntity np = new PeriodEntity();
            np.setYear(nextYear);
            np.setMonth(month);
            np.setPeriodCode(String.format("%04d%02d", nextYear, month));
            np.setStartDate(LocalDate.of(nextYear, month, 1));
            np.setEndDate(LocalDate.of(nextYear, month, 1).plusMonths(1).minusDays(1));
            np.setStatus("open");
            periodService.save(np);
        }

        // 5. 结转期初余额到次年 1 月
        String janPeriod = String.format("%04d01", nextYear);
        if (!openingBalances.isEmpty()) {
            subjectBalanceService.initOpeningBalances(janPeriod, openingBalances);
            subjectBalanceService.lockOpeningBalances(janPeriod);
        }

        log.info("年度结账完成：{} → {}，结转科目数 {}", year, nextYear, openingBalances.size());
    }

    private List<PeriodEntity> getYearPeriods(Integer year) {
        Long enterpriseId = EnterpriseContextHolder.get();
        LambdaQueryWrapper<PeriodEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PeriodEntity::getYear, year)
                .orderByAsc(PeriodEntity::getMonth);
        if (enterpriseId != null) {
            wrapper.eq(PeriodEntity::getEnterpriseId, enterpriseId);
        }
        return periodService.list(wrapper);
    }
}
