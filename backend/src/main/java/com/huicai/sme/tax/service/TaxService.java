package com.huicai.sme.tax.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.sme.tax.dto.BatchOperationResult;
import com.huicai.sme.tax.entity.TaxDeclarationEntity;
import com.huicai.sme.tax.entity.TaxTypeEntity;

import java.util.List;
import java.util.Map;

public interface TaxService {
    // 税种
    IPage<TaxTypeEntity> pageQueryTaxType(String keyword, Integer current, Integer size);
    List<TaxTypeEntity> listAllTaxTypes();
    TaxTypeEntity createTaxType(TaxTypeEntity entity);
    TaxTypeEntity updateTaxType(TaxTypeEntity entity);
    void deleteTaxType(Long id);

    // 进项发票
    IPage<InputInvoiceEntity> pageQueryInput(String vendorName, String period, String certStatus, Integer current, Integer size);
    InputInvoiceEntity createInput(InputInvoiceEntity entity);
    InputInvoiceEntity certify(Long id, String deductionPeriod);

    /**
     * P57: 进项发票勾选申报抵扣（认证后→已申报）。
     * 仅 certificationStatus=CERTIFIED 且 declaredStatus=UNDECLARED 可申报。
     */
    InputInvoiceEntity declareDeduction(Long id, String declaredPeriod, Long userId);
    Map<String, Object> inputSummary(String period);
    List<Map<String, Object>> inputByTaxRate(String period);

    // 销项发票
    IPage<OutputInvoiceEntity> pageQueryOutput(String customerName, String period, String status, String invoiceType, Integer current, Integer size);
    OutputInvoiceEntity getOutputById(Long id);
    OutputInvoiceEntity createOutput(OutputInvoiceEntity entity);
    void deleteOutput(Long id);
    /** CONFIRMED → 生成凭证(含科目分录) → VOUCHERED */
    void generateVoucherFromInvoice(Long invoiceId, Long userId);
    Map<String, Object> outputSummary(String period);
    Map<String, Object> outputSummaryAll();
    List<Map<String, Object>> outputByTaxRate(String period);

    // 增值税计算
    Map<String, Object> calculateVat(String period);

    // 申报
    IPage<TaxDeclarationEntity> pageQueryDeclaration(String status, Integer current, Integer size);
    TaxDeclarationEntity createDeclaration(TaxDeclarationEntity entity);
    TaxDeclarationEntity submitDeclaration(Long id);
    /** P18-1: 审批通过 (SUBMITTED → APPROVED) */
    TaxDeclarationEntity approveDeclaration(Long id, String approver);
    /** P18-1: 驳回 (SUBMITTED → REJECTED) */
    TaxDeclarationEntity rejectDeclaration(Long id, String approver, String reason);

    /** P1: 申报审批通过后自动生成缴税凭证 (APPROVED → voucher created) */
    void generateVoucherFromDeclaration(Long declarationId, Long userId);

    /**
     * 批量生成凭证（多张发票合并生成一张凭证）
     */
    String batchGenerateVoucherFromInvoices(List<Long> invoiceIds, Long userId, Boolean sameCustomer);

    // 销项发票批量操作（P56：best-effort 模式，单条失败不影响其他）
    BatchOperationResult batchSubmitForReview(List<Long> ids, Long userId);
    BatchOperationResult batchConfirm(List<Long> ids, Long userId);
    BatchOperationResult batchReject(List<Long> ids, Long userId, String reason);
    BatchOperationResult batchRevert(List<Long> ids, Long userId);
    BatchOperationResult batchMarkVouchered(List<Long> ids, Long userId);
    BatchOperationResult batchVoid(List<Long> ids, Long userId, String reason);
    BatchOperationResult batchReverse(List<Long> ids, Long userId, String reason);
}
