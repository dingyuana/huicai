package com.huicai.module.tax.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.tax.entity.InputInvoiceEntity;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.entity.TaxDeclarationEntity;
import com.huicai.module.tax.entity.TaxTypeEntity;
import com.huicai.module.tax.mapper.InputInvoiceMapper;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import com.huicai.module.tax.mapper.TaxDeclarationMapper;
import com.huicai.module.tax.mapper.TaxTypeMapper;
import com.huicai.module.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaxServiceImpl implements TaxService {

    private final TaxTypeMapper taxTypeMapper;
    private final InputInvoiceMapper inputMapper;
    private final OutputInvoiceMapper outputMapper;
    private final TaxDeclarationMapper declarationMapper;

    // ========== 税种 ==========
    @Override
    public IPage<TaxTypeEntity> pageQueryTaxType(String keyword, Integer current, Integer size) {
        Page<TaxTypeEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<TaxTypeEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(TaxTypeEntity::getCode, keyword)
                    .or().like(TaxTypeEntity::getName, keyword));
        }
        wrapper.orderByAsc(TaxTypeEntity::getCode);
        return taxTypeMapper.selectPage(page, wrapper);
    }

    @Override
    public List<TaxTypeEntity> listAllTaxTypes() {
        return taxTypeMapper.selectList(new LambdaQueryWrapper<TaxTypeEntity>()
                .eq(TaxTypeEntity::getIsActive, true)
                .orderByAsc(TaxTypeEntity::getCode));
    }

    @Override
    public TaxTypeEntity createTaxType(TaxTypeEntity entity) {
        if (entity.getIsActive() == null) entity.setIsActive(true);
        taxTypeMapper.insert(entity);
        return entity;
    }

    @Override
    public TaxTypeEntity updateTaxType(TaxTypeEntity entity) {
        TaxTypeEntity existing = taxTypeMapper.selectById(entity.getId());
        if (existing == null) {
            throw new BusinessException("税种不存在");
        }
        existing.setName(entity.getName());
        existing.setRate(entity.getRate());
        existing.setTaxCategory(entity.getTaxCategory());
        existing.setIsActive(entity.getIsActive());
        existing.setRemark(entity.getRemark());
        taxTypeMapper.updateById(existing);
        return existing;
    }

    @Override
    public void deleteTaxType(Long id) {
        taxTypeMapper.deleteById(id);
    }

    // ========== 进项发票 ==========
    @Override
    public IPage<InputInvoiceEntity> pageQueryInput(String vendorName, String period, String certStatus,
                                                     Integer current, Integer size) {
        Page<InputInvoiceEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<InputInvoiceEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(vendorName)) {
            wrapper.like(InputInvoiceEntity::getVendorName, vendorName);
        }
        if (StrUtil.isNotBlank(period)) {
            wrapper.eq(InputInvoiceEntity::getPeriod, period);
        }
        if (StrUtil.isNotBlank(certStatus)) {
            wrapper.eq(InputInvoiceEntity::getCertificationStatus, certStatus);
        }
        wrapper.orderByDesc(InputInvoiceEntity::getInvoiceDate);
        return inputMapper.selectPage(page, wrapper);
    }

    @Override
    public InputInvoiceEntity createInput(InputInvoiceEntity entity) {
        // 校验金额
        if (entity.getAmount() == null || entity.getTaxRate() == null) {
            throw new BusinessException("金额和税率不能为空");
        }
        if (entity.getTaxAmount() == null) {
            entity.setTaxAmount(entity.getAmount().multiply(entity.getTaxRate())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (entity.getTotalAmount() == null) {
            entity.setTotalAmount(entity.getAmount().add(entity.getTaxAmount()));
        }
        if (entity.getCertificationStatus() == null) {
            entity.setCertificationStatus("UNCERTIFIED");
        }
        if (entity.getPeriod() == null) {
            entity.setPeriod(String.format("%04d%02d",
                    entity.getInvoiceDate().getYear(),
                    entity.getInvoiceDate().getMonthValue()));
        }
        inputMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InputInvoiceEntity certify(Long id, String deductionPeriod) {
        InputInvoiceEntity entity = inputMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("发票不存在");
        }
        if (!"UNCERTIFIED".equals(entity.getCertificationStatus())) {
            throw new BusinessException("当前状态不可认证");
        }
        entity.setCertificationStatus("CERTIFIED");
        entity.setCertifiedDate(LocalDate.now());
        entity.setDeductionPeriod(deductionPeriod != null ? deductionPeriod :
                String.format("%04d%02d", LocalDate.now().getYear(), LocalDate.now().getMonthValue()));
        entity.setDeductionAmount(entity.getTaxAmount());
        inputMapper.updateById(entity);
        return entity;
    }

    @Override
    public Map<String, Object> inputSummary(String period) {
        return inputMapper.summaryByPeriod(period);
    }

    @Override
    public List<Map<String, Object>> inputByTaxRate(String period) {
        return inputMapper.byTaxRate(period);
    }

    // ========== 销项发票 ==========
    @Override
    public IPage<OutputInvoiceEntity> pageQueryOutput(String customerName, String period, String status,
                                                       Integer current, Integer size) {
        Page<OutputInvoiceEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<OutputInvoiceEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(customerName)) {
            wrapper.like(OutputInvoiceEntity::getCustomerName, customerName);
        }
        if (StrUtil.isNotBlank(period)) {
            wrapper.eq(OutputInvoiceEntity::getPeriod, period);
        }
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(OutputInvoiceEntity::getStatus, status);
        }
        wrapper.orderByDesc(OutputInvoiceEntity::getInvoiceDate);
        return outputMapper.selectPage(page, wrapper);
    }

    @Override
    public OutputInvoiceEntity createOutput(OutputInvoiceEntity entity) {
        if (entity.getAmount() == null || entity.getTaxRate() == null) {
            throw new BusinessException("金额和税率不能为空");
        }
        if (entity.getTaxAmount() == null) {
            entity.setTaxAmount(entity.getAmount().multiply(entity.getTaxRate())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (entity.getTotalAmount() == null) {
            entity.setTotalAmount(entity.getAmount().add(entity.getTaxAmount()));
        }
        if (entity.getStatus() == null) {
            entity.setStatus("DRAFT");
        }
        if (entity.getPeriod() == null) {
            entity.setPeriod(String.format("%04d%02d",
                    entity.getInvoiceDate().getYear(),
                    entity.getInvoiceDate().getMonthValue()));
        }
        outputMapper.insert(entity);
        return entity;
    }

    @Override
    public Map<String, Object> outputSummary(String period) {
        return outputMapper.summaryByPeriod(period);
    }

    @Override
    public List<Map<String, Object>> outputByTaxRate(String period) {
        return outputMapper.byTaxRate(period);
    }

    // ========== 增值税计算 ==========
    @Override
    public Map<String, Object> calculateVat(String period) {
        Map<String, Object> output = outputMapper.summaryByPeriod(period);
        Map<String, Object> input = inputMapper.summaryByPeriod(period);
        BigDecimal outputTax = toBigDecimal(output == null ? null : output.get("tax"));
        BigDecimal inputTax = toBigDecimal(input == null ? null : input.get("deductible"));
        BigDecimal payable = outputTax.subtract(inputTax);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("period", period);
        result.put("outputTax", outputTax);
        result.put("inputTax", inputTax);
        result.put("payableTax", payable);
        // 附加税: 城建税7% + 教育费附加3% + 地方教育费附加2% = 12%
        if (payable.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal surcharge = payable.multiply(new BigDecimal("0.12"))
                    .setScale(2, RoundingMode.HALF_UP);
            result.put("surcharge", surcharge);
            result.put("totalPayable", payable.add(surcharge));
        } else {
            result.put("surcharge", BigDecimal.ZERO);
            result.put("totalPayable", BigDecimal.ZERO);
            result.put("note", "留抵税额(下期继续抵扣)");
        }
        return result;
    }

    // ========== 申报 ==========
    @Override
    public IPage<TaxDeclarationEntity> pageQueryDeclaration(String status, Integer current, Integer size) {
        Page<TaxDeclarationEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<TaxDeclarationEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(TaxDeclarationEntity::getStatus, status);
        }
        wrapper.orderByDesc(TaxDeclarationEntity::getCreatedAt);
        return declarationMapper.selectPage(page, wrapper);
    }

    @Override
    public TaxDeclarationEntity createDeclaration(TaxDeclarationEntity entity) {
        if (entity.getStatus() == null) entity.setStatus("DRAFT");
        declarationMapper.insert(entity);
        return entity;
    }

    @Override
    public TaxDeclarationEntity submitDeclaration(Long id) {
        TaxDeclarationEntity entity = declarationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("申报记录不存在");
        }
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }
        entity.setStatus("SUBMITTED");
        declarationMapper.updateById(entity);
        return entity;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return new BigDecimal(o.toString());
    }
}
