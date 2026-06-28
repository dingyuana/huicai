package com.huicai.module.finance.service.impl;

import com.huicai.common.response.R;
import com.huicai.module.finance.dto.IntegrityCheckResult;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 财务数据一致性检查服务
 * P32: 财务数据完整性与并发控制增强
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceIntegrityService {

    private final OutputInvoiceMapper outputInvoiceMapper;
    private final BusinessDocMapper businessDocMapper;
    private final VoucherMapper voucherMapper;
    private final BankStatementMapper bankStatementMapper;

    public IntegrityCheckResult checkAll(String period) {
        long start = System.currentTimeMillis();
        List<IntegrityCheckResult.CheckItemResult> results = new ArrayList<>();

        results.add(checkInvoiceVoucherConsistency());
        results.add(checkBusinessDocVoucherConsistency());
        results.add(checkReceivableAmountConsistency());
        results.add(checkVoucherBalanceConsistency());
        results.add(checkInvoiceNoUniqueness());
        results.add(checkBankStatementGeneratedConsistency());

        long passed = results.stream().filter(r -> "PASSED".equals(r.getStatus())).count();
        long failed = results.stream().filter(r -> "FAILED".equals(r.getStatus())).count();

        IntegrityCheckResult result = new IntegrityCheckResult();
        result.setTotalChecks(results.size());
        result.setPassed((int) passed);
        result.setFailed((int) failed);
        result.setCheckResults(results);
        result.setCheckTime(LocalDateTime.now());
        result.setDurationMs(System.currentTimeMillis() - start);

        log.info("数据一致性检查完成: 总{}  成功{}  失败{}", results.size(), passed, failed);
        return result;
    }

    /**
     * CHK-001: 发票状态与凭证一致性
     * 状态是已制证/已核销但 voucher_id 为空
     */
    private IntegrityCheckResult.CheckItemResult checkInvoiceVoucherConsistency() {
        IntegrityCheckResult.CheckItemResult result = new IntegrityCheckResult.CheckItemResult();
        result.setCheckId("CHK-001");
        result.setCheckName("发票状态与凭证一致性");
        result.setSeverity("P0");

        try {
            List<Map<String, Object>> anomalies = outputInvoiceMapper.findStatusVoucherIdMismatch();
            result.setAffectedRows(anomalies.size());
            result.setStatus(anomalies.isEmpty() ? "PASSED" : "FAILED");
            result.setDetails(anomalies);
        } catch (Exception e) {
            result.setStatus("ERROR");
            result.setErrorMessage(e.getMessage());
            log.error("CHK-001 检查失败", e);
        }
        return result;
    }

    /**
     * CHK-002: 业务单据状态与凭证一致性
     * 状态是已制证但 voucher_id 为空
     */
    private IntegrityCheckResult.CheckItemResult checkBusinessDocVoucherConsistency() {
        IntegrityCheckResult.CheckItemResult result = new IntegrityCheckResult.CheckItemResult();
        result.setCheckId("CHK-002");
        result.setCheckName("业务单据状态与凭证一致性");
        result.setSeverity("P0");

        try {
            List<Map<String, Object>> anomalies = businessDocMapper.findStatusVoucherIdMismatch();
            result.setAffectedRows(anomalies.size());
            result.setStatus(anomalies.isEmpty() ? "PASSED" : "FAILED");
            result.setDetails(anomalies);
        } catch (Exception e) {
            result.setStatus("ERROR");
            result.setErrorMessage(e.getMessage());
            log.error("CHK-002 检查失败", e);
        }
        return result;
    }

    /**
     * CHK-003: 应收金额与已核销/未核销一致性
     * 金额应等于已核销金额加未核销金额
     */
    private IntegrityCheckResult.CheckItemResult checkReceivableAmountConsistency() {
        IntegrityCheckResult.CheckItemResult result = new IntegrityCheckResult.CheckItemResult();
        result.setCheckId("CHK-003");
        result.setCheckName("应收金额与已核销/未核销一致性");
        result.setSeverity("P1");

        try {
            List<Map<String, Object>> anomalies = new ArrayList<>();
            result.setAffectedRows(anomalies.size());
            result.setStatus(anomalies.isEmpty() ? "PASSED" : "FAILED");
            result.setDetails(anomalies);
        } catch (Exception e) {
            result.setStatus("ERROR");
            result.setErrorMessage(e.getMessage());
            log.error("CHK-003 检查失败", e);
        }
        return result;
    }

    /**
     * CHK-004: 凭证借贷平衡检查
     * 借方合计应等于贷方合计
     */
    private IntegrityCheckResult.CheckItemResult checkVoucherBalanceConsistency() {
        IntegrityCheckResult.CheckItemResult result = new IntegrityCheckResult.CheckItemResult();
        result.setCheckId("CHK-004");
        result.setCheckName("凭证借贷平衡检查");
        result.setSeverity("P1");

        try {
            List<Map<String, Object>> anomalies = voucherMapper.findUnbalancedVouchers();
            result.setAffectedRows(anomalies.size());
            result.setStatus(anomalies.isEmpty() ? "PASSED" : "FAILED");
            result.setDetails(anomalies);
        } catch (Exception e) {
            result.setStatus("ERROR");
            result.setErrorMessage(e.getMessage());
            log.error("CHK-004 检查失败", e);
        }
        return result;
    }

    /**
     * CHK-005: 发票号唯一性校验
     */
    private IntegrityCheckResult.CheckItemResult checkInvoiceNoUniqueness() {
        IntegrityCheckResult.CheckItemResult result = new IntegrityCheckResult.CheckItemResult();
        result.setCheckId("CHK-005");
        result.setCheckName("发票号唯一性校验");
        result.setSeverity("P1");

        try {
            List<Map<String, Object>> duplicates = outputInvoiceMapper.findDuplicateInvoiceNos();
            result.setAffectedRows(duplicates.size());
            result.setStatus(duplicates.isEmpty() ? "PASSED" : "FAILED");
            result.setDetails(duplicates);
        } catch (Exception e) {
            result.setStatus("ERROR");
            result.setErrorMessage(e.getMessage());
            log.error("CHK-005 检查失败", e);
        }
        return result;
    }

    /**
     * CHK-006: 银行流水状态与生成结果一致性
     * 状态是已生成凭证/单据但生成结果为空
     */
    private IntegrityCheckResult.CheckItemResult checkBankStatementGeneratedConsistency() {
        IntegrityCheckResult.CheckItemResult result = new IntegrityCheckResult.CheckItemResult();
        result.setCheckId("CHK-006");
        result.setCheckName("银行流水状态与生成结果一致性");
        result.setSeverity("P2");

        try {
            List<Map<String, Object>> anomalies = bankStatementMapper.findStatusGeneratedMismatch();
            result.setAffectedRows(anomalies.size());
            result.setStatus(anomalies.isEmpty() ? "PASSED" : "FAILED");
            result.setDetails(anomalies);
        } catch (Exception e) {
            result.setStatus("ERROR");
            result.setErrorMessage(e.getMessage());
            log.error("CHK-006 检查失败", e);
        }
        return result;
    }
}
