package com.huicai.base.voucher.service;

import com.huicai.base.voucher.dto.vo.AuxiliaryLedgerRowVO;
import com.huicai.base.voucher.dto.vo.LedgerRowVO;
import com.huicai.base.voucher.dto.vo.QuantityAmountLedgerRowVO;
import com.huicai.base.voucher.dto.vo.SubjectBalanceRowVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 账簿查询服务
 */
public interface LedgerService {

    /**
     * 科目余额表：按期间列出所有末级科目的期初/借方/贷方/期末余额
     * T5: 可选过滤参数 — includeZero(含零余额科目,默认false) / includeNoMovement(含无发生额科目,默认false) / subjectCodePrefix(科目编码前缀)
     */
    List<SubjectBalanceRowVO> subjectBalance(String period, boolean includeZero, boolean includeNoMovement, String subjectCodePrefix);

    /**
     * 科目余额表：默认行为（不含零余额、不含无发生额、不按编码前缀过滤），兼容旧调用
     */
    List<SubjectBalanceRowVO> subjectBalance(String period);

    /**
     * 总分类账：按科目+期间，展示该科目的逐笔发生及余额
     */
    List<LedgerRowVO> generalLedger(Long subjectId, String period);

    /**
     * 总分类账（T8）：includeUnposted=false（默认）只含 POSTED 凭证；true 含全部状态
     */
    List<LedgerRowVO> generalLedger(Long subjectId, String period, boolean includeUnposted);

    /**
     * 明细账：按科目+期间+日期范围（可选），展示逐笔分录明细（期初行+滚动余额+voucherNo/voucherDate）
     * startDate/endDate 为 null 时退化为按期间过滤
     */
    List<LedgerRowVO> subsidiaryLedger(Long subjectId, String period, java.time.LocalDate startDate, java.time.LocalDate endDate);

    /**
     * 明细账（T8）：includeUnposted=false（默认）只含 POSTED 凭证；true 含全部状态
     */
    List<LedgerRowVO> subsidiaryLedger(Long subjectId, String period, java.time.LocalDate startDate, java.time.LocalDate endDate, boolean includeUnposted);

    /**
     * 辅助核算账：按核算维度（customer/vendor/department/project/employee）+ 期间查询各科目余额
     * dimensionValue 为空时按维度值全量分组
     */
    List<AuxiliaryLedgerRowVO> auxiliaryLedger(String dimensionType, String period, Long dimensionValue);

    /**
     * 多栏式明细账：按父科目编码+期间，列出所有子科目的期初余额/本期借/贷/期末余额
     * 用于展示如 管理费用(6602) → 办公费、差旅费、工资 等子科目明细
     */
    List<SubjectBalanceRowVO> multiColumnLedger(String parentSubjectCode, String period);

    /**
     * 数量金额式账簿：按科目编码+期间，列出各凭证分录的数量、单价、金额
     * 用于原材料、库存商品等存货科目的数量金额核算
     */
    List<QuantityAmountLedgerRowVO> quantityAmountLedger(String subjectCode, String period);
}
