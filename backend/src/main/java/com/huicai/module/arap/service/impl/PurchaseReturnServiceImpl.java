package com.huicai.module.arap.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.PurchaseReturnEntity;
import com.huicai.module.arap.entity.VendorEntity;
import com.huicai.module.arap.mapper.PurchaseReturnMapper;
import com.huicai.module.arap.mapper.VendorMapper;
import com.huicai.module.arap.service.PurchaseReturnService;
import com.huicai.module.finance.constant.VoucherType;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseReturnServiceImpl implements PurchaseReturnService {

    private static final long DEFAULT_USER_ID = 1L;

    private static final String SUBJECT_PAYABLE = "2202";       // 应付账款
    private static final String SUBJECT_MATERIAL = "1403";      // 原材料
    private static final String SUBJECT_TAX_OUT = "22210105";   // 应交税费——进项税额转出

    private final PurchaseReturnMapper returnMapper;
    private final VendorMapper vendorMapper;
    private final BusinessDocMapper businessDocMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final SubjectMapper subjectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnVO createReturn(PurchaseReturnRequest request) {
        // 1. 查找原始应付单
        List<BusinessDocEntity> originals = businessDocMapper.selectList(
            new LambdaQueryWrapper<BusinessDocEntity>()
                .eq(BusinessDocEntity::getDocNo, request.originalDocNo())
                .eq(BusinessDocEntity::getSupplierId, request.vendorId())
                .last("LIMIT 1")
        );
        if (originals.isEmpty()) {
            // 不强制关联，允许独立退货
            return createIndependentReturn(request);
        }
        BusinessDocEntity original = originals.get(0);

        // 2. 校验退货金额 ≤ 应付单未清金额
        if (request.returnAmount().compareTo(original.getUnsettledAmount()) > 0) {
            throw new BusinessException("退货金额超过应付单未清余额: 退货="
                + request.returnAmount() + ", 未清=" + original.getUnsettledAmount());
        }

        // 3. 创建退货记录
        PurchaseReturnEntity entity = new PurchaseReturnEntity();
        entity.setReturnNo(generateReturnNo());
        entity.setVendorId(request.vendorId());
        entity.setOriginalDocNo(request.originalDocNo());
        entity.setOriginalDocId(original.getId());
        entity.setReturnAmount(request.returnAmount());
        entity.setTaxAmount(request.taxAmount() != null ? request.taxAmount() : BigDecimal.ZERO);
        entity.setReason(request.reason());
        entity.setStatus("DRAFT");
        entity.setCreatedBy(DEFAULT_USER_ID);
        returnMapper.insert(entity);

        // 4. 更新原始应付单未清金额
        BigDecimal newUnsettled = original.getUnsettledAmount().subtract(request.returnAmount());
        original.setUnsettledAmount(newUnsettled);
        original.setSettledAmount(original.getSettledAmount().add(request.returnAmount()));
        businessDocMapper.updateById(original);

        // 5. 自动确认并生成凭证
        confirmAndGenerateVoucher(entity);

        // 6. 返回
        String vendorName = lookupVendorName(request.vendorId());
        return buildVO(entity, vendorName);
    }

    private PurchaseReturnVO createIndependentReturn(PurchaseReturnRequest request) {
        PurchaseReturnEntity entity = new PurchaseReturnEntity();
        entity.setReturnNo(generateReturnNo());
        entity.setVendorId(request.vendorId());
        entity.setOriginalDocNo(request.originalDocNo());
        entity.setReturnAmount(request.returnAmount());
        entity.setTaxAmount(request.taxAmount() != null ? request.taxAmount() : BigDecimal.ZERO);
        entity.setReason(request.reason());
        entity.setStatus("DRAFT");
        entity.setCreatedBy(DEFAULT_USER_ID);
        returnMapper.insert(entity);

        confirmAndGenerateVoucher(entity);

        String vendorName = lookupVendorName(request.vendorId());
        return buildVO(entity, vendorName);
    }

    private void confirmAndGenerateVoucher(PurchaseReturnEntity entity) {
        // 确认状态
        entity.setStatus("CONFIRMED");
        returnMapper.updateById(entity);

        // 生成凭证
        String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String voucherNo = voucherNoService.generateNextNo(period, VoucherType.ZZ);
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(VoucherType.ZZ);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("采购退货: " + (entity.getReason() != null ? entity.getReason() : "退货"));
        voucher.setTotalDebit(entity.getReturnAmount());
        voucher.setTotalCredit(entity.getReturnAmount());
        voucher.setCreatedBy(DEFAULT_USER_ID);
        voucherMapper.insert(voucher);

        BigDecimal netAmount = entity.getReturnAmount().subtract(entity.getTaxAmount());

        // 查找科目
        Long payableSubjectId = findSubjectIdByCode(SUBJECT_PAYABLE);
        Long materialSubjectId = findSubjectIdByCode(SUBJECT_MATERIAL);
        Long taxOutSubjectId = findSubjectIdByCode(SUBJECT_TAX_OUT);

        // 分录 1: 借 应付账款（退货金额）
        VoucherEntryEntity entryDr = new VoucherEntryEntity();
        entryDr.setVoucherId(voucher.getId());
        entryDr.setSubjectId(payableSubjectId);
        entryDr.setDebit(entity.getReturnAmount());
        entryDr.setCredit(BigDecimal.ZERO);
        entryDr.setSummary(entity.getReason());
        entryDr.setSortOrder(1);
        voucherEntryMapper.insert(entryDr);

        // 分录 2: 贷 原材料（不含税金额）
        VoucherEntryEntity entryCr1 = new VoucherEntryEntity();
        entryCr1.setVoucherId(voucher.getId());
        entryCr1.setSubjectId(materialSubjectId);
        entryCr1.setDebit(BigDecimal.ZERO);
        entryCr1.setCredit(netAmount);
        entryCr1.setSummary(entity.getReason());
        entryCr1.setSortOrder(2);
        voucherEntryMapper.insert(entryCr1);

        // 分录 3: 贷 应交税费——进项税额转出（税额）
        if (entity.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            VoucherEntryEntity entryCr2 = new VoucherEntryEntity();
            entryCr2.setVoucherId(voucher.getId());
            entryCr2.setSubjectId(taxOutSubjectId);
            entryCr2.setDebit(BigDecimal.ZERO);
            entryCr2.setCredit(entity.getTaxAmount());
            entryCr2.setSummary(entity.getReason());
            entryCr2.setSortOrder(3);
            voucherEntryMapper.insert(entryCr2);
        }

        voucherMapper.updateById(voucher);

        // 关联凭证
        entity.setVoucherId(voucher.getId());
        entity.setVoucherNo(voucherNo);
        entity.setStatus("VOUCHERED");
        entity.setUpdatedAt(LocalDateTime.now());
        returnMapper.updateById(entity);

        log.info("采购退货凭证生成: returnId={}, voucherId={}, voucherNo={}",
            entity.getId(), voucher.getId(), voucherNo);
    }

    @Override
    public PurchaseReturnVO getById(Long id) {
        PurchaseReturnEntity entity = returnMapper.selectById(id);
        if (entity == null) throw new BusinessException("采购退货记录不存在: " + id);
        String vendorName = lookupVendorName(entity.getVendorId());
        return buildVO(entity, vendorName);
    }

    @Override
    public List<PurchaseReturnVO> listReturns() {
        List<PurchaseReturnEntity> entities = returnMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PurchaseReturnEntity>()
                .orderByDesc(PurchaseReturnEntity::getCreatedAt));
        List<PurchaseReturnVO> result = new ArrayList<>();
        for (PurchaseReturnEntity e : entities) {
            result.add(buildVO(e, lookupVendorName(e.getVendorId())));
        }
        return result;
    }

    private String generateReturnNo() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "TH" + date + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase();
    }

    private String lookupVendorName(Long vendorId) {
        if (vendorId == null) return null;
        VendorEntity v = vendorMapper.selectById(vendorId);
        return v != null ? v.getName() : null;
    }

    private PurchaseReturnVO buildVO(PurchaseReturnEntity entity, String vendorName) {
        return new PurchaseReturnVO(
            entity.getId(), entity.getReturnNo(), entity.getVendorId(),
            vendorName, entity.getOriginalDocNo(),
            entity.getReturnAmount(), entity.getTaxAmount(),
            entity.getReason(), entity.getStatus(),
            entity.getVoucherId(), entity.getVoucherNo()
        );
    }

    private Long findSubjectIdByCode(String code) {
        List<Subject> list = subjectMapper.selectList(
            new LambdaQueryWrapper<Subject>().eq(Subject::getCode, code).last("LIMIT 1"));
        if (list.isEmpty()) {
            throw new BusinessException("科目编码不存在: " + code + ", 请先配置科目");
        }
        return list.get(0).getId();
    }
}