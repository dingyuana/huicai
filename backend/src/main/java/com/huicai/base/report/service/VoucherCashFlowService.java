package com.huicai.base.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.report.entity.CashFlowRuleEntity;
import com.huicai.base.report.entity.VoucherCashFlowEntity;
import com.huicai.base.report.mapper.CashFlowRuleMapper;
import com.huicai.base.report.mapper.VoucherCashFlowMapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * P96 REQ-095：凭证级现金流量项目分配服务
 * 按 t_cash_flow_rule 规则自动分配流量类型，写入 t_voucher_cash_flow
 * 人工指定优先于规则（铁律 #1：人是唯一审核主体）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherCashFlowService {

    private final VoucherEntryMapper voucherEntryMapper;
    private final SubjectMapper subjectMapper;
    private final CashFlowRuleMapper cashFlowRuleMapper;
    private final VoucherCashFlowMapper voucherCashFlowMapper;
    private final VoucherMapper voucherMapper;

    /** 银行存款科目编码 */
    private static final String BANK_SUBJECT_CODE = "1002";

    /**
     * 为单个凭证分配现金流量项目
     * 先清除旧分配，再按规则重新分配
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignCashFlow(Long voucherId) {
        List<VoucherEntryEntity> entries = voucherEntryMapper.selectByVoucherId(voucherId);
        if (entries == null || entries.isEmpty()) return;

        // 清除旧分配
        voucherCashFlowMapper.deleteByVoucherId(voucherId);

        // 找到银行存款(1002)分录
        for (VoucherEntryEntity entry : entries) {
            Subject subject = subjectMapper.selectById(entry.getSubjectId());
            if (subject == null || !BANK_SUBJECT_CODE.equals(subject.getCode())) continue;

            // 确定流量方向：借方=现金流入，贷方=现金流出
            boolean isInflow = entry.getDebit() != null && entry.getDebit().compareTo(BigDecimal.ZERO) > 0;
            BigDecimal amount = isInflow ? entry.getDebit() : entry.getCredit();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) continue;

            // 找对方科目（同凭证的其他分录）
            String flowType = null;
            for (VoucherEntryEntity other : entries) {
                if (other.getId().equals(entry.getId())) continue;
                Subject otherSubject = subjectMapper.selectById(other.getSubjectId());
                if (otherSubject == null) continue;

                // 匹配规则：对方科目编码匹配 match_subject
                CashFlowRuleEntity rule = matchRule(otherSubject.getCode(), isInflow);
                if (rule != null) {
                    flowType = rule.getFlowType();
                    break;
                }
            }

            // 未匹配规则则降级：默认经营活动
            if (flowType == null) {
                flowType = isInflow ? "OPERATING_IN" : "OPERATING_OUT";
            }

            VoucherCashFlowEntity cf = new VoucherCashFlowEntity();
            cf.setVoucherId(voucherId);
            cf.setFlowType(flowType);
            cf.setAmount(amount);
            voucherCashFlowMapper.insert(cf);
        }
    }

    /**
     * 按对方科目编码匹配规则
     */
    private CashFlowRuleEntity matchRule(String subjectCode, boolean isInflow) {
        if (subjectCode == null) return null;
        LambdaQueryWrapper<CashFlowRuleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CashFlowRuleEntity::getIsActive, true)
               .like(CashFlowRuleEntity::getMatchSubject, subjectCode)
               .orderByDesc(CashFlowRuleEntity::getPriority)
               .last("LIMIT 1");
        CashFlowRuleEntity rule = cashFlowRuleMapper.selectOne(wrapper);
        if (rule == null) return null;
        // 校验方向一致性：FINANCING_IN/INVESTING_IN/OPERATING_IN 对应流入
        String type = rule.getFlowType();
        boolean ruleIsInflow = type != null && type.endsWith("_IN");
        return ruleIsInflow == isInflow ? rule : null;
    }

    /**
     * 按期间批量重建现金流量分配（人工触发）
     */
    @Transactional(rollbackFor = Exception.class)
    public int rebuildByPeriod(String period) {
        List<VoucherEntity> vouchers = voucherMapper.selectList(
                new LambdaQueryWrapper<VoucherEntity>()
                        .eq(VoucherEntity::getPeriod, period)
                        .eq(VoucherEntity::getStatus, "POSTED")
        );
        int count = 0;
        for (VoucherEntity v : vouchers) {
            assignCashFlow(v.getId());
            count++;
        }
        log.info("现金流量批量重建完成: period={}, count={}", period, count);
        return count;
    }
}
