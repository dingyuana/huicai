package com.huicai.sme.arap.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.constant.ArapStatus;
import com.huicai.sme.arap.entity.*;
import com.huicai.base.business.entity.ArapSettlementEntryEntity;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.mapper.ArapSettlementEntryMapper;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.sme.arap.mapper.PrepaymentMapper;
import com.huicai.sme.arap.service.ArapSettlementService;
import com.huicai.sme.arap.service.PrepaymentService;
import com.huicai.base.voucher.constant.VoucherType;
import com.huicai.base.voucher.constant.BusinessDocStatus;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.sme.arap.vo.PrepaymentVO;
import com.huicai.common.context.EnterpriseContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 预付款/预收款服务实现 — 管理预付账款完整生命周期.
 */
@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 类级事务（P102/M5b，2026-10-03）：走租户表的路径必须有事务，否则
 * TenantRlsInitializer 切面不触发、app.enterprise_id 设不进去，
 * 应用以非超级用户连接时 RLS 会把本企业数据也过滤掉（读 0 行）。
 * 方法级 @Transactional 优先级更高，不受此影响。
 */
@Transactional
public class PrepaymentServiceImpl implements PrepaymentService {

    private static final long DEFAULT_USER_ID = 1L;

    private static final String SUBJECT_PREPAY = "1123";
    private static final String SUBJECT_PAYABLE = "2202";
    private static final String SUBJECT_RECEIVABLE = "1122";
    private static final String SUBJECT_PREPAID_RECEIPT = "2203";

    private final PrepaymentMapper prepaymentMapper;
    private final BusinessDocMapper businessDocMapper;
    private final ArapSettlementService settlementService;
    private final ArapSettlementMapper settlementMapper;
    private final ArapSettlementEntryMapper settlementEntryMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final SubjectMapper subjectMapper;
    // P102 批次 6：出参 VO 需要补全单据号/供应商名/客户名（跨表，非 t_prepayment 本表列）
    private final VendorMapper vendorMapper;
    private final CustomerMapper customerMapper;

    @Override
    public IPage<PrepaymentEntity> pageQuery(Long vendorId, Long customerId, String status, String scope,
                                             LocalDate startDate, LocalDate endDate, Integer current, Integer size) {
        Page<PrepaymentEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<PrepaymentEntity> wrapper = new LambdaQueryWrapper<PrepaymentEntity>()
                .eq(vendorId != null, PrepaymentEntity::getVendorId, vendorId)
                .eq(customerId != null, PrepaymentEntity::getCustomerId, customerId)
                .eq(StrUtil.isNotBlank(status), PrepaymentEntity::getStatus, status)
                .orderByDesc(PrepaymentEntity::getCreatedAt);
        if (StrUtil.isNotBlank(scope) && "completed".equals(scope)) {
            wrapper.in(PrepaymentEntity::getStatus, "CONFIRMED");
        } else if (StrUtil.isNotBlank(scope) && "pending".equals(scope)) {
            wrapper.notIn(PrepaymentEntity::getStatus, "CONFIRMED");
        }
        if (startDate != null) wrapper.ge(PrepaymentEntity::getTxDate, startDate);
        if (endDate != null) wrapper.le(PrepaymentEntity::getTxDate, endDate);
        return prepaymentMapper.selectPage(page, wrapper);
    }

    @Override
    public PrepaymentEntity getById(Long id) {
        PrepaymentEntity entity = prepaymentMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("预付款记录不存在: " + id);
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrepaymentEntity create(PrepaymentEntity entity) {
        // P106 / AGENTS §4.5 第 34 条：原为 `if (tenantId == null) setTenantId(DEFAULT_TENANT_ID /* = 1L */)`。
        // t_prepayment 同时存在 tenant_id（V5 建表遗留）与 enterprise_id（V105 补，NOT NULL DEFAULT 1），
        // 而 RLS 的 enterprise_policy 只读 enterprise_id ⇒ 写死 1 会让两列永久不一致：
        // 任何按 tenant_id 的统计/索引都基于错误数据，且与被 RLS 放行的 enterprise_id 相互矛盾。
        // 该列全库无任何读取方（rg 仅命中原这一行），故此处直接与 enterprise_id 同源：
        // 有上下文时两列同为上下文企业；无上下文时两列都落 DB DEFAULT 1，仍然一致。
        // ⚠️ 不得改成「无上下文即抛异常」—— TenantRlsInitializer:65-69 的 null-return 语义不变（SPEC §1.2 L1-3）。
        entity.setTenantId(EnterpriseContextHolder.get());
        // P0-fix: 原为 `if (entity.getStatus() == null) entity.setStatus(ArapStatus.DRAFT)`。
        // PrepaymentController#create 直收 @RequestBody PrepaymentEntity（违反铁律 #13），
        // 只在 null 时兜底 ⇒ 客户端可指定任意 status。
        // 比本轮已修的 5 处更危险：t_prepayment **没有任何 status CHECK 约束**（实测 CHECK 数为 0）
        // ⇒ ①DB 层零兜底，连 "__GARBAGE__" 这种乱码都能落库（已由测试实证）；
        // ②客户端可 POST status=APPLIED / SETTLED **一步跳过** confirm()（DRAFT→CONFIRMED）
        // 这次人工确认（铁律 #1）。
        // 改为无条件强制 DRAFT —— 创建态是唯一合法起点。
        entity.setStatus(ArapStatus.DRAFT);
        // P102 批次 6：不变量 settled + unsettled == amount 必须对**任何调用方**成立。
        // 原实现是 `if (unsettled == null) unsettled = amount`，即**无视已传入的 settledAmount**
        // ⇒ 任何传了 settled>0 的调用方都会拿到 amount != settled + unsettled 的不一致记录，
        // 而后续 applyToPayable/BadDebtService 都是在此基础上做加减（AGENTS §4.3「双列不一致」）。
        // 现状说明：现网两条创建路径都传 settled=ZERO（AutoGenerationService:780,820；
        // HTTP 路径 PrepaymentCreateDTO 刻意不含 settledAmount），所以此前**没有被触发** ——
        // 这是个尚未被踩到的陷阱，而不是已发生的错账。改为按不变量推导，顺手封掉它。
        if (entity.getSettledAmount() == null) entity.setSettledAmount(BigDecimal.ZERO);
        if (entity.getUnsettledAmount() == null) {
            entity.setUnsettledAmount(entity.getAmount().subtract(entity.getSettledAmount()));
        }
        if (entity.getTxDate() == null) entity.setTxDate(LocalDate.now());
        prepaymentMapper.insert(entity);
        log.info("预付款创建: id={}, vendorId={}, customerId={}, amount={}, status={}",
                entity.getId(), entity.getVendorId(), entity.getCustomerId(), entity.getAmount(), entity.getStatus());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrepaymentEntity confirm(Long id) {
        PrepaymentEntity entity = getById(id);
        if (!ArapStatus.isDraft(entity.getStatus())) {
            throw new BusinessException("仅草稿(DRAFT)状态的预付款可确认, 当前状态: " + entity.getStatus());
        }
        entity.setStatus(ArapStatus.CONFIRMED);
        prepaymentMapper.updateById(entity);
        log.info("预付款确认: id={}, vendorId={}, customerId={}, amount={}", id, entity.getVendorId(), entity.getCustomerId(), entity.getAmount());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrepaymentEntity applyToPayable(Long prepayId, Long payableId, BigDecimal applyAmount,
                                           String period, Long userId, String summary) {
        // 1. 校验预付款
        PrepaymentEntity prepay = getById(prepayId);
        if (!ArapStatus.isConfirmed(prepay.getStatus())) {
            throw new BusinessException("预付款状态必须为 CONFIRMED, 当前: " + prepay.getStatus());
        }
        if (prepay.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("预付款已全额核销, 无可抵扣余额");
        }

        // 2. 校验应付单（P34: 查询 BusinessDocEntity 替代 PayableEntity）
        BusinessDocEntity payable = businessDocMapper.selectById(payableId);
        if (payable == null) {
            throw new BusinessException("应付单不存在: " + payableId);
        }
        if (!prepay.getVendorId().equals(payable.getSupplierId())) {
            throw new BusinessException("预付款与应付单的供应商不一致: prepay.vendorId="
                    + prepay.getVendorId() + ", payable.supplierId=" + payable.getSupplierId());
        }
        if (payable.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("应付单已结清, 无可抵扣余额");
        }

        // 3. 确定抵扣金额
        if (applyAmount == null || applyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            applyAmount = prepay.getUnsettledAmount().min(payable.getUnsettledAmount());
        }
        if (applyAmount.compareTo(prepay.getUnsettledAmount()) > 0) {
            throw new BusinessException("抵扣金额超过预付款未结余额: apply=" + applyAmount
                    + ", unsettled=" + prepay.getUnsettledAmount());
        }
        if (applyAmount.compareTo(payable.getUnsettledAmount()) > 0) {
            throw new BusinessException("抵扣金额超过应付单未结余额: apply=" + applyAmount
                    + ", unsettled=" + payable.getUnsettledAmount());
        }

        final BigDecimal finalApply = applyAmount;
        final Long operUserId = userId != null ? userId : DEFAULT_USER_ID;

        // 4. 更新预付款
        BigDecimal newSettled = prepay.getSettledAmount().add(finalApply);
        prepay.setSettledAmount(newSettled);
        prepay.setUnsettledAmount(prepay.getAmount().subtract(newSettled));
        if (prepay.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0) {
            prepay.setStatus(ArapStatus.APPLIED);
        }
        prepaymentMapper.updateById(prepay);

        // 5. 更新应付单（P34: 更新 BusinessDocEntity 替代 PayableEntity）
        BigDecimal payNewSettled = payable.getSettledAmount().add(finalApply);
        payable.setSettledAmount(payNewSettled);
        payable.setUnsettledAmount(payable.getAmount().subtract(payNewSettled));
        if (payable.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0) {
            // P0-fix: 原守卫 ArapStatus.isConfirmed() 即 "CONFIRMED".equals(status)，
            // 而 CONFIRMED 不在 chk_doc_status 允许集内 ⇒ 该分支<b>永远不可达</b>，
            // 预付款全额核销后单据静默停留在 APPROVED 而 unsettled=0，
            // 下游 status='FULLY_RECONCILED' 的查询漏掉它。
            // SETTLED 同样是 ArapStatus 串台到 t_business_doc 的非法值。
            payable.setStatus(BusinessDocStatus.FULLY_RECONCILED);
        }
        businessDocMapper.updateById(payable);

        // 6. 创建核销单 (ArapSettlement)
        String effectivePeriod = period != null ? period
                : LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        String effectiveSummary = summary != null ? summary : "预付冲应付";

        ArapSettlementEntity settlement = new ArapSettlementEntity();
        // 预付冲应付 = 付款方向。chk_settlement_type 仅允许 RECEIVE/PAY，
        // 原写 "PAYABLE" 会在 insert 时违约，流程运行时必失败。
        settlement.setSettlementType("PAY");
        settlement.setSettlementDate(LocalDate.now());
        settlement.setPeriod(effectivePeriod);
        settlement.setPartyId(prepay.getVendorId());
        settlement.setPartyType("VENDOR");
        settlement.setTotalAmount(finalApply);
        settlement.setDiscountAmount(BigDecimal.ZERO);
        settlement.setStatus(ArapStatus.DRAFT);
        String prefix = "YF";
        settlement.setSettlementNo(prefix + "-" + effectivePeriod + "-"
                + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase());
        settlement.setCreatedBy(operUserId);
        settlementMapper.insert(settlement);

        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettlementId(settlement.getId());
        entry.setPayableId(payableId);
        entry.setSettledAmount(finalApply);
        entry.setDiscountAmount(BigDecimal.ZERO);
        settlementEntryMapper.insert(entry);

        // 确认核销单 (更新应付已核销金额 — 我们已手动更新, 此处仅改状态)
        settlement.setStatus(ArapStatus.CONFIRMED);
        settlementMapper.updateById(settlement);

        // 7. 创建凭证 (借:应付账款 / 贷:预付账款)
        String voucherNo = voucherNoService.generateNextNo(effectivePeriod, VoucherType.FK);
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(effectivePeriod);
        voucher.setVoucherTypeId(VoucherType.FK);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(effectiveSummary);
        voucher.setTotalDebit(finalApply);
        voucher.setTotalCredit(finalApply);
        voucher.setCreatedBy(operUserId);
        voucherMapper.insert(voucher);

        // 查找科目
        Long payableSubjectId = findSubjectIdByCode(SUBJECT_PAYABLE);
        Long prepaySubjectId = findSubjectIdByCode(SUBJECT_PREPAY);

        // 分录 1: 借 应付账款
        VoucherEntryEntity entryDr = new VoucherEntryEntity();
        entryDr.setVoucherId(voucher.getId());
        entryDr.setSubjectId(payableSubjectId);
        entryDr.setDebit(finalApply);
        entryDr.setCredit(BigDecimal.ZERO);
        entryDr.setSummary(effectiveSummary);
        entryDr.setSortOrder(1);
        voucherEntryMapper.insert(entryDr);

        // 分录 2: 贷 预付账款
        VoucherEntryEntity entryCr = new VoucherEntryEntity();
        entryCr.setVoucherId(voucher.getId());
        entryCr.setSubjectId(prepaySubjectId);
        entryCr.setDebit(BigDecimal.ZERO);
        entryCr.setCredit(finalApply);
        entryCr.setSummary(effectiveSummary);
        entryCr.setSortOrder(2);
        voucherEntryMapper.insert(entryCr);

        voucherMapper.updateById(voucher);

        // 关联核销单与凭证
        settlement.setVoucherId(voucher.getId());
        settlementMapper.updateById(settlement);

        log.info("预付冲应付完成: prepayId={}, payableId={}, amount={}, voucherId={}, settlementId={}",
                prepayId, payableId, finalApply, voucher.getId(), settlement.getId());

        return prepay;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrepaymentEntity applyToReceivable(Long prepayId, Long receivableId, BigDecimal applyAmount,
                                              String period, Long userId, String summary) {
        PrepaymentEntity prepay = getById(prepayId);
        if (!ArapStatus.isConfirmed(prepay.getStatus())) {
            throw new BusinessException("预收款状态必须为 CONFIRMED, 当前: " + prepay.getStatus());
        }
        if (prepay.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("预收款已全额核销, 无可抵扣余额");
        }

        // P34: 查询 BusinessDocEntity 替代 ReceivableEntity
        BusinessDocEntity receivable = businessDocMapper.selectById(receivableId);
        if (receivable == null) {
            throw new BusinessException("应收单不存在: " + receivableId);
        }
        if (!Objects.equals(prepay.getCustomerId(), receivable.getCustomerId())) {
            throw new BusinessException("预收款与应收单的客户不一致: prepay.customerId="
                    + prepay.getCustomerId() + ", receivable.customerId=" + receivable.getCustomerId());
        }
        if (receivable.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("应收单已结清, 无可抵扣余额");
        }

        if (applyAmount == null || applyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            applyAmount = prepay.getUnsettledAmount().min(receivable.getUnsettledAmount());
        }
        if (applyAmount.compareTo(prepay.getUnsettledAmount()) > 0) {
            throw new BusinessException("抵扣金额超过预收款未结余额: apply=" + applyAmount
                    + ", unsettled=" + prepay.getUnsettledAmount());
        }
        if (applyAmount.compareTo(receivable.getUnsettledAmount()) > 0) {
            throw new BusinessException("抵扣金额超过应收单未结余额: apply=" + applyAmount
                    + ", unsettled=" + receivable.getUnsettledAmount());
        }

        final BigDecimal finalApply = applyAmount;
        final Long operUserId = userId != null ? userId : DEFAULT_USER_ID;

        BigDecimal newSettled = prepay.getSettledAmount().add(finalApply);
        prepay.setSettledAmount(newSettled);
        prepay.setUnsettledAmount(prepay.getAmount().subtract(newSettled));
        if (prepay.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0) {
            prepay.setStatus(ArapStatus.APPLIED);
        }
        prepaymentMapper.updateById(prepay);

        // P34: 更新 BusinessDocEntity 替代 ReceivableEntity
        BigDecimal recNewSettled = receivable.getSettledAmount().add(finalApply);
        receivable.setSettledAmount(recNewSettled);
        receivable.setUnsettledAmount(receivable.getAmount().subtract(recNewSettled));
        if (receivable.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0) {
            // P0-fix: 同上 —— isConfirmed 守卫对 t_business_doc 恒为 false，SETTLED 非法
            receivable.setStatus(BusinessDocStatus.FULLY_RECONCILED);
        }
        businessDocMapper.updateById(receivable);

        String effectivePeriod = period != null ? period
                : LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        String effectiveSummary = summary != null ? summary : "预收冲应收";

        ArapSettlementEntity settlement = new ArapSettlementEntity();
        // 预收冲应收 = 收款方向。chk_settlement_type 仅允许 RECEIVE/PAY，
        // 原写 "RECEIVABLE" 会在 insert 时违约，流程运行时必失败。
        settlement.setSettlementType("RECEIVE");
        settlement.setSettlementDate(LocalDate.now());
        settlement.setPeriod(effectivePeriod);
        settlement.setPartyId(prepay.getCustomerId());
        settlement.setPartyType("CUSTOMER");
        settlement.setTotalAmount(finalApply);
        settlement.setDiscountAmount(BigDecimal.ZERO);
        settlement.setStatus(ArapStatus.DRAFT);
        String prefix = "YS";
        settlement.setSettlementNo(prefix + "-" + effectivePeriod + "-"
                + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase());
        settlement.setCreatedBy(operUserId);
        settlementMapper.insert(settlement);

        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettlementId(settlement.getId());
        entry.setReceivableId(receivableId);
        entry.setSettledAmount(finalApply);
        entry.setDiscountAmount(BigDecimal.ZERO);
        settlementEntryMapper.insert(entry);

        settlement.setStatus(ArapStatus.CONFIRMED);
        settlementMapper.updateById(settlement);

        String voucherNo = voucherNoService.generateNextNo(effectivePeriod, VoucherType.SK);
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(effectivePeriod);
        voucher.setVoucherTypeId(VoucherType.SK);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(effectiveSummary);
        voucher.setTotalDebit(finalApply);
        voucher.setTotalCredit(finalApply);
        voucher.setCreatedBy(operUserId);
        voucherMapper.insert(voucher);

        Long prepaidReceiptSubjectId = findSubjectIdByCode(SUBJECT_PREPAID_RECEIPT);
        Long receivableSubjectId = findSubjectIdByCode(SUBJECT_RECEIVABLE);

        VoucherEntryEntity entryDr = new VoucherEntryEntity();
        entryDr.setVoucherId(voucher.getId());
        entryDr.setSubjectId(prepaidReceiptSubjectId);
        entryDr.setDebit(finalApply);
        entryDr.setCredit(BigDecimal.ZERO);
        entryDr.setSummary(effectiveSummary);
        entryDr.setSortOrder(1);
        voucherEntryMapper.insert(entryDr);

        VoucherEntryEntity entryCr = new VoucherEntryEntity();
        entryCr.setVoucherId(voucher.getId());
        entryCr.setSubjectId(receivableSubjectId);
        entryCr.setDebit(BigDecimal.ZERO);
        entryCr.setCredit(finalApply);
        entryCr.setSummary(effectiveSummary);
        entryCr.setSortOrder(2);
        voucherEntryMapper.insert(entryCr);

        voucherMapper.updateById(voucher);

        settlement.setVoucherId(voucher.getId());
        settlementMapper.updateById(settlement);

        log.info("预收冲应收完成: prepayId={}, receivableId={}, amount={}, voucherId={}, settlementId={}",
                prepayId, receivableId, finalApply, voucher.getId(), settlement.getId());

        return prepay;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id, Long userId, String reason) {
        PrepaymentEntity prepay = getById(id);
        if (!ArapStatus.isConfirmed(prepay.getStatus()) && !ArapStatus.APPLIED.equals(prepay.getStatus())) {
            throw new BusinessException("仅 CONFIRMED 或 APPLIED 状态的预付款可反冲, 当前: " + prepay.getStatus());
        }
        if (StrUtil.isBlank(reason)) {
            throw new BusinessException("反冲必须填写原因");
        }

        // 恢复未结金额
        prepay.setSettledAmount(BigDecimal.ZERO);
        prepay.setUnsettledAmount(prepay.getAmount());
        prepay.setStatus(ArapStatus.REVERSED);
        prepay.setUpdatedAt(LocalDate.now());
        prepaymentMapper.updateById(prepay);

        log.info("预付款反冲: id={}, vendorId={}, customerId={}, amount={}, reason={}", id, prepay.getVendorId(),
                prepay.getCustomerId(), prepay.getAmount(), reason);
    }

    @Override
    public List<PrepaymentEntity> getOpenPrepayments(Long vendorId) {
        return prepaymentMapper.selectList(
                new LambdaQueryWrapper<PrepaymentEntity>()
                        .eq(PrepaymentEntity::getVendorId, vendorId)
                        .gt(PrepaymentEntity::getUnsettledAmount, BigDecimal.ZERO)
                        .eq(PrepaymentEntity::getStatus, ArapStatus.CONFIRMED)
                        .orderByAsc(PrepaymentEntity::getCreatedAt)
        );
    }

    @Override
    public List<PrepaymentEntity> getOpenPrepaymentsForCustomer(Long customerId) {
        return prepaymentMapper.selectList(
                new LambdaQueryWrapper<PrepaymentEntity>()
                        .eq(PrepaymentEntity::getCustomerId, customerId)
                        .gt(PrepaymentEntity::getUnsettledAmount, BigDecimal.ZERO)
                        .eq(PrepaymentEntity::getStatus, ArapStatus.CONFIRMED)
                        .orderByAsc(PrepaymentEntity::getCreatedAt)
        );
    }

    // ================= P102 批次 6：出参面 VO 视图层 =================

    @Override
    public IPage<PrepaymentVO> pageView(Long vendorId, Long customerId, String status, String scope,
                                        LocalDate startDate, LocalDate endDate, Integer current, Integer size) {
        // 刻意复用 pageQuery：过滤条件只存在于 LambdaQueryWrapper 一处，不复制进 SQL。
        IPage<PrepaymentEntity> page = pageQuery(vendorId, customerId, status, scope,
                startDate, endDate, current, size);
        return PrepaymentVO.from(page, lookupNames(page == null ? List.of() : page.getRecords()));
    }

    @Override
    public PrepaymentVO viewOf(PrepaymentEntity entity) {
        return entity == null ? null
                : PrepaymentVO.from(entity, lookupNames(List.of(entity)));
    }

    @Override
    public List<PrepaymentVO> viewList(List<PrepaymentEntity> list) {
        return list == null ? null : PrepaymentVO.from(list, lookupNames(list));
    }

    /**
     * 一次查齐三张关联表的 id → 展示名（每页 3 条 SQL，不是 3×N）。
     *
     * <p>为什么用 MP 的 selectList 而不是手写 JOIN：
     * ① 表名/列名已用 information_schema 核实（t_vendor.name / t_customer.name / t_business_doc.doc_no），
     *    但 MP 顺带带上逻辑删除与租户条件，手写 SQL 容易漏（AGENTS §4.2 第 7 条）；
     * ② 返回强类型 Entity，不存在 Map key 大小写问题（AGENTS §4.3 第 17 条）。
     *
     * <p>⚠️ 名称查不到就<b>返回 null</b>，不做「未知供应商」之类兜底字符串 ——
     * 兜底会把「主数据缺失」伪装成正常数据，正是 §4.5 第 25 条说的那类假绿。
     */
    private PrepaymentVO.Names lookupNames(List<PrepaymentEntity> list) {
        java.util.Set<Long> docIds = new java.util.LinkedHashSet<>();
        java.util.Set<Long> vendorIds = new java.util.LinkedHashSet<>();
        java.util.Set<Long> customerIds = new java.util.LinkedHashSet<>();
        for (PrepaymentEntity e : list) {
            if (e.getDocId() != null) {
                docIds.add(e.getDocId());
            }
            if (e.getVendorId() != null) {
                vendorIds.add(e.getVendorId());
            }
            if (e.getCustomerId() != null) {
                customerIds.add(e.getCustomerId());
            }
        }
        java.util.Map<Long, String> docNos = new java.util.HashMap<>();
        if (!docIds.isEmpty()) {
            for (BusinessDocEntity d : businessDocMapper.selectBatchIds(docIds)) {
                docNos.put(d.getId(), d.getDocNo());
            }
        }
        java.util.Map<Long, String> vendorNames = new java.util.HashMap<>();
        if (!vendorIds.isEmpty()) {
            for (VendorEntity v : vendorMapper.selectBatchIds(vendorIds)) {
                vendorNames.put(v.getId(), v.getName());
            }
        }
        java.util.Map<Long, String> customerNames = new java.util.HashMap<>();
        if (!customerIds.isEmpty()) {
            for (CustomerEntity c : customerMapper.selectBatchIds(customerIds)) {
                customerNames.put(c.getId(), c.getName());
            }
        }
        return new PrepaymentVO.Names(docNos, vendorNames, customerNames);
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
