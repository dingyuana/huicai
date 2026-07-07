package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.tax.entity.InputInvoiceEntity;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.InputInvoiceMapper;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class InvoiceDedupUtil {

    private final InputInvoiceMapper inputInvoiceMapper;
    private final OutputInvoiceMapper outputInvoiceMapper;

    /**
     * 跨表查询发票号是否已存在（同时查 t_input_invoice + t_output_invoice）
     *
     * @param invoiceNos 待检查的发票号码集合
     * @return 已存在的发票号码集合
     */
    public Set<String> findExisting(Collection<String> invoiceNos) {
        if (invoiceNos == null || invoiceNos.isEmpty()) return Collections.emptySet();
        Set<String> result = new HashSet<>();

        List<InputInvoiceEntity> inputInvoices = inputInvoiceMapper.selectList(
                new LambdaQueryWrapper<InputInvoiceEntity>()
                        .in(InputInvoiceEntity::getInvoiceNo, invoiceNos));
        for (InputInvoiceEntity inv : inputInvoices) {
            if (inv.getInvoiceNo() != null) result.add(inv.getInvoiceNo());
        }

        List<OutputInvoiceEntity> outputInvoices = outputInvoiceMapper.selectList(
                new LambdaQueryWrapper<OutputInvoiceEntity>()
                        .in(OutputInvoiceEntity::getInvoiceNo, invoiceNos));
        for (OutputInvoiceEntity inv : outputInvoices) {
            if (inv.getInvoiceNo() != null) result.add(inv.getInvoiceNo());
        }

        return result;
    }

    /**
     * 检查单张发票号是否重复，重复时抛 BusinessException(409)
     */
    public void checkDuplicateOrThrow(String invoiceNo) {
        if (StrUtil.isBlank(invoiceNo)) return;
        Set<String> existing = findExisting(Collections.singletonList(invoiceNo));
        if (!existing.isEmpty()) {
            throw BusinessException.conflict("发票号 " + invoiceNo + " 已存在");
        }
    }
}