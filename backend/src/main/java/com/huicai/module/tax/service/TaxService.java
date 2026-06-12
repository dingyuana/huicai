package com.huicai.module.tax.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.tax.entity.InputInvoiceEntity;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.entity.TaxDeclarationEntity;
import com.huicai.module.tax.entity.TaxTypeEntity;

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
    Map<String, Object> inputSummary(String period);
    List<Map<String, Object>> inputByTaxRate(String period);

    // 销项发票
    IPage<OutputInvoiceEntity> pageQueryOutput(String customerName, String period, String status, Integer current, Integer size);
    OutputInvoiceEntity createOutput(OutputInvoiceEntity entity);
    Map<String, Object> outputSummary(String period);
    List<Map<String, Object>> outputByTaxRate(String period);

    // 增值税计算
    Map<String, Object> calculateVat(String period);

    // 申报
    IPage<TaxDeclarationEntity> pageQueryDeclaration(String status, Integer current, Integer size);
    TaxDeclarationEntity createDeclaration(TaxDeclarationEntity entity);
    TaxDeclarationEntity submitDeclaration(Long id);
}
