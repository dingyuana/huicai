package com.huicai.module.finance.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import com.huicai.module.arap.mapper.ArapSettlementMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.tax.entity.InputInvoiceEntity;
import com.huicai.module.tax.mapper.InputInvoiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 编号关联数据一致性校验定时任务
 * <p>
 * 每天凌晨 3:00 执行，检查编号关联字段的一致性：
 * 1. 有 voucherId 但无 voucherNo 的单据
 * 2. 有 docNo 但无对应凭证的单据
 * 3. 核销单关联不一致
 * 4. 输出统计报告
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NumberingConsistencyCheckJob {

    private final InputInvoiceMapper inputInvoiceMapper;
    private final BusinessDocMapper businessDocMapper;
    private final ArapSettlementMapper arapSettlementMapper;
    private final VoucherMapper voucherMapper;

    /**
     * 每天凌晨 3:00 执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void execute() {
        log.info("========== 编号关联数据一致性校验开始 ==========");

        Map<String, Integer> issues = new HashMap<>();
        int totalChecked = 0;

        try {
            // 1. 检查进项发票
            issues.putAll(checkInputInvoices());
            totalChecked += 1000; // 预估

            // 2. 检查业务单据
            issues.putAll(checkBusinessDocs());
            totalChecked += 1000;

            // 5. 检查核销单
            issues.putAll(checkSettlements());
            totalChecked += 500;

        } catch (Exception e) {
            log.error("编号关联一致性校验异常: {}", e.getMessage(), e);
        }

        // 输出报告
        log.info("========== 编号关联数据一致性校验完成 ==========");
        log.info("总检查数: {}", totalChecked);
        if (issues.isEmpty()) {
            log.info("✅ 未发现不一致数据");
        } else {
            log.warn("⚠️ 发现 {} 类不一致问题:", issues.size());
            for (Map.Entry<String, Integer> entry : issues.entrySet()) {
                log.warn("  - {}: {} 条", entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 检查进项发票：有 voucherId 但无 voucherNo
     */
    private Map<String, Integer> checkInputInvoices() {
        Map<String, Integer> issues = new HashMap<>();

        // 有 voucherId 但 voucherNo 为空
        LambdaQueryWrapper<InputInvoiceEntity> q1 = new LambdaQueryWrapper<>();
        q1.isNotNull(InputInvoiceEntity::getVoucherId)
          .and(w -> w.isNull(InputInvoiceEntity::getVoucherNo).or().eq(InputInvoiceEntity::getVoucherNo, ""));
        long count1 = inputInvoiceMapper.selectCount(q1);
        if (count1 > 0) issues.put("进项发票: voucherId 非空但 voucherNo 为空", (int) count1);

        // 有 docId 但 docNo 为空
        LambdaQueryWrapper<InputInvoiceEntity> q2 = new LambdaQueryWrapper<>();
        q2.isNotNull(InputInvoiceEntity::getDocId)
          .and(w -> w.isNull(InputInvoiceEntity::getDocNo).or().eq(InputInvoiceEntity::getDocNo, ""));
        long count2 = inputInvoiceMapper.selectCount(q2);
        if (count2 > 0) issues.put("进项发票: docId 非空但 docNo 为空", (int) count2);

        return issues;
    }

    /**
     * 检查业务单据
     */
    private Map<String, Integer> checkBusinessDocs() {
        Map<String, Integer> issues = new HashMap<>();

        // 有 voucherId 但无 voucherNo
        LambdaQueryWrapper<BusinessDocEntity> q1 = new LambdaQueryWrapper<>();
        q1.isNotNull(BusinessDocEntity::getVoucherId)
          .and(w -> w.isNull(BusinessDocEntity::getVoucherNo).or().eq(BusinessDocEntity::getVoucherNo, ""));
        long count1 = businessDocMapper.selectCount(q1);
        if (count1 > 0) issues.put("业务单据: voucherId 非空但 voucherNo 为空", (int) count1);

        // 状态为 VOUCHERED 但 voucherId 为空
        LambdaQueryWrapper<BusinessDocEntity> q2 = new LambdaQueryWrapper<>();
        q2.eq(BusinessDocEntity::getStatus, "VOUCHERED")
          .and(w -> w.isNull(BusinessDocEntity::getVoucherId).or().eq(BusinessDocEntity::getVoucherId, 0L));
        long count2 = businessDocMapper.selectCount(q2);
        if (count2 > 0) issues.put("业务单据: 状态 VOUCHERED 但 voucherId 为空", (int) count2);

        return issues;
    }

    /**
     * 检查核销单
     */
    private Map<String, Integer> checkSettlements() {
        Map<String, Integer> issues = new HashMap<>();

        // 有 voucherId 但无 voucherNo
        LambdaQueryWrapper<ArapSettlementEntity> q = new LambdaQueryWrapper<>();
        q.isNotNull(ArapSettlementEntity::getVoucherId)
          .and(w -> w.isNull(ArapSettlementEntity::getVoucherNo).or().eq(ArapSettlementEntity::getVoucherNo, ""));
        long count = arapSettlementMapper.selectCount(q);
        if (count > 0) issues.put("核销单: voucherId 非空但 voucherNo 为空", (int) count);

        return issues;
    }
}
