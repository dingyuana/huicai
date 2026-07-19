package com.huicai.base.voucher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.voucher.dto.VoucherCreateDTO;
import com.huicai.base.voucher.dto.VoucherCreateDTO.EntryDTO;
import com.huicai.base.voucher.dto.VoucherQueryDTO;
import com.huicai.base.voucher.dto.VoucherTemplateVO;
import com.huicai.base.voucher.dto.VoucherVO;
import com.huicai.base.voucher.dto.VoucherVO.EntryVO;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.base.balance.mapper.SubjectBalanceMapper;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.base.voucher.service.VoucherStateMachineService;
import com.huicai.base.voucher.service.VoucherTemplateService;
import com.huicai.base.period.entity.PeriodEntity;
import com.huicai.module.system.entity.Subject;
import com.huicai.base.auth.entity.UserEntity;
import com.huicai.module.system.entity.VoucherTypeEntity;
import com.huicai.base.auth.mapper.UserMapper;
import com.huicai.base.period.service.PeriodService;
import com.huicai.module.system.service.SubjectService;
import com.huicai.module.system.service.VoucherTypeService;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 凭证 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, VoucherEntity> implements VoucherService {

    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final SubjectBalanceMapper subjectBalanceMapper;
    private final VoucherNoService voucherNoService;
    private final SubjectBalanceService subjectBalanceService;
    private final VoucherTypeService voucherTypeService;
    private final VoucherTemplateService voucherTemplateService;
    private final SubjectService subjectService;
    private final PeriodService periodService;
    private final UserMapper userMapper;
    private final VoucherStateMachineService voucherStateMachineService;
    private final OutputInvoiceMapper outputInvoiceMapper;
    private final BusinessDocMapper businessDocMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public IPage<VoucherVO> pageQuery(VoucherQueryDTO queryDTO) {
        Page<VoucherEntity> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        IPage<VoucherEntity> entityPage = voucherMapper.selectVoucherPage(
                page,
                queryDTO.getPeriod(),
                queryDTO.getStatus(),
                queryDTO.getVoucherTypeId(),
                queryDTO.getKeyword(),
                queryDTO.getVoucherNo(),
                queryDTO.getSourceDocNo()
        );

        IPage<VoucherVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        List<VoucherVO> voList = entityPage.getRecords().stream()
                .map(this::toVoucherVO)
                .collect(Collectors.toList());
        populateUserNames(voList);
        populateRelatedStatuses(voList);
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public VoucherVO getDetail(Long id) {
        VoucherEntity entity = voucherMapper.selectVoucherDetail(id);
        if (entity == null) {
            throw BusinessException.notFound("凭证不存在");
        }

        VoucherVO vo = toVoucherVO(entity);
        // 加载分录
        List<VoucherEntryEntity> entries = voucherEntryMapper.selectByVoucherId(id);
        vo.setEntries(entries.stream().map(this::toEntryVO).collect(Collectors.toList()));
        populateUserNames(List.of(vo));
        populateRelatedStatuses(List.of(vo));
        return vo;
    }

    @Override
    @Transactional
    public VoucherVO create(VoucherCreateDTO dto, Long userId) {
        // 校验期间是否可操作
        assertPeriodOpen(dto.getPeriod());

        // 校验摘要不可为空
        validateSummary(dto.getSummary());

        // 校验分录: 至少2条、借贷平衡、金额非负、末级科目
        validateEntries(dto.getEntries());
        validateLeafSubjects(dto.getEntries());
        // P1-4: 辅助核算强校验拦截
        validateAssistJson(dto.getEntries());

        // 生成凭证号
        String voucherNo = voucherNoService.generateNextNo(dto.getPeriod(), dto.getVoucherTypeId());

        // 创建凭证
        VoucherEntity entity = new VoucherEntity();
        entity.setVoucherNo(voucherNo);
        entity.setPeriod(dto.getPeriod());
        entity.setVoucherTypeId(dto.getVoucherTypeId());
        entity.setStatus("DRAFT");
        entity.setSource("MANUAL");
        entity.setSummary(dto.getSummary());
        entity.setAttachmentIds(dto.getAttachmentIds());
        entity.setCreatedBy(userId);

        // 计算总金额
        BigDecimal totalDebit = dto.getEntries().stream()
                .map(EntryDTO::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = dto.getEntries().stream()
                .map(EntryDTO::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        entity.setTotalDebit(totalDebit);
        entity.setTotalCredit(totalCredit);

        voucherMapper.insert(entity);

        // 创建分录
        List<VoucherEntryEntity> entries = buildEntries(entity.getId(), dto.getEntries());
        for (VoucherEntryEntity entry : entries) {
            voucherEntryMapper.insert(entry);
        }

        log.info("创建凭证: id={}, voucherNo={}, userId={}", entity.getId(), voucherNo, userId);
        return getDetail(entity.getId());
    }

    @Override
    @Transactional
    public VoucherVO update(VoucherCreateDTO dto, Long userId) {
        Long id = dto.getId();
        if (id == null) {
            throw BusinessException.badRequest("更新时凭证ID不能为空");
        }

        VoucherEntity entity = voucherMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("凭证不存在");
        }
        if (!"DRAFT".equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅草稿状态的凭证可修改");
        }

        // 校验期间是否可操作
        assertPeriodOpen(entity.getPeriod());

        // 校验摘要不可为空
        validateSummary(dto.getSummary());

        // 校验分录
        validateEntries(dto.getEntries());
        validateLeafSubjects(dto.getEntries());
        // P1-4: 辅助核算强校验拦截
        validateAssistJson(dto.getEntries());

        // 更新凭证主表
        entity.setSummary(dto.getSummary());
        entity.setAttachmentIds(dto.getAttachmentIds());
        entity.setUpdatedBy(userId);

        BigDecimal totalDebit = dto.getEntries().stream()
                .map(EntryDTO::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = dto.getEntries().stream()
                .map(EntryDTO::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        entity.setTotalDebit(totalDebit);
        entity.setTotalCredit(totalCredit);

        voucherMapper.updateById(entity);

        // 删除旧分录,重新插入
        voucherEntryMapper.deleteByVoucherId(id);
        List<VoucherEntryEntity> entries = buildEntries(id, dto.getEntries());
        for (VoucherEntryEntity entry : entries) {
            voucherEntryMapper.insert(entry);
        }

        log.info("更新凭证: id={}, userId={}", id, userId);
        return getDetail(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        VoucherEntity entity = voucherMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("凭证不存在");
        }
        if (!"DRAFT".equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅草稿状态的凭证可删除");
        }

        voucherMapper.deleteById(id);
        voucherEntryMapper.deleteByVoucherId(id);
        log.info("删除凭证: id={}", id);
    }

    @Override
    @Transactional
    public void submit(Long id, Long userId) {
        VoucherEntity entity = getValidVoucher(id);
        voucherStateMachineService.assertSubmittable(entity);
        assertPeriodOpen(entity.getPeriod());
        voucherMapper.batchUpdateStatus(Collections.singletonList(id), "SUBMITTED", userId);
        log.info("提交凭证: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public void batchSubmit(List<Long> ids, Long userId) {
        for (Long id : ids) {
            VoucherEntity entity = getValidVoucher(id);
            assertStatus(entity, "DRAFT");
            assertPeriodOpen(entity.getPeriod());
        }
        voucherMapper.batchUpdateStatus(ids, "SUBMITTED", userId);
        log.info("批量提交凭证: ids={}, userId={}", ids, userId);
    }

    @Override
    @Transactional
    public void audit(Long id, Long userId) {
        VoucherEntity entity = getValidVoucher(id);
        voucherStateMachineService.assertAuditable(entity);
        assertPeriodOpen(entity.getPeriod());
        voucherMapper.batchUpdateStatus(Collections.singletonList(id), "AUDITED", userId);
        log.info("审核凭证: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public void batchAudit(List<Long> ids, Long userId) {
        for (Long id : ids) {
            VoucherEntity entity = getValidVoucher(id);
            assertStatus(entity, "SUBMITTED");
            assertPeriodOpen(entity.getPeriod());
        }
        voucherMapper.batchUpdateStatus(ids, "AUDITED", userId);
        log.info("批量审核凭证: ids={}, userId={}", ids, userId);
    }

    @Override
    @Transactional
    public void reject(Long id, Long userId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw BusinessException.badRequest("驳回必须填写原因");
        }
        VoucherEntity entity = getValidVoucher(id);
        voucherStateMachineService.assertAuditable(entity);
        assertPeriodOpen(entity.getPeriod());
        entity.setStatus("DRAFT");
        entity.setRejectedReason(reason);
        entity.setUpdatedBy(userId);
        voucherMapper.updateById(entity);
        log.info("驳回凭证: id={}, userId={}, reason={}", id, userId, reason);
    }

    @Override
    @Transactional
    public void unpost(Long id, Long userId) {
        VoucherEntity entity = getValidVoucher(id);
        if (!"POSTED".equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅已记账凭证可反过账");
        }
        assertPeriodOpen(entity.getPeriod());
        voucherMapper.batchUpdateStatus(Collections.singletonList(id), "AUDITED", userId);
        log.info("反过账凭证: id={}, userId={}", id, userId);
    }

    @Override
    public VoucherTemplateVO getTemplateByVoucherType(Long voucherTypeId) {
        // 凭证类型与模板已解耦，不再自动加载模板。
        // 自动制证场景由模板匹配引擎（source + businessType + direction）接管。
        return null;
    }

    @Override
    @Transactional
    public void post(Long id, Long userId) {
        VoucherEntity entity = getValidVoucher(id);
        voucherStateMachineService.assertPostable(entity);
        assertPeriodOpen(entity.getPeriod());

        // 更新状态
        voucherMapper.batchUpdateStatus(Collections.singletonList(id), "POSTED", userId);

        // 更新科目余额
        List<VoucherEntryEntity> entries = voucherEntryMapper.selectByVoucherId(id);
        subjectBalanceService.updateBalanceOnPost(entity, entries);

        log.info("记账凭证: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public void close(Long id, Long userId) {
        VoucherEntity entity = getValidVoucher(id);
        voucherStateMachineService.assertClosable(entity);
        assertPeriodOpen(entity.getPeriod());
        entity.setStatus("CLOSED");
        entity.setUpdatedBy(userId);
        voucherMapper.updateById(entity);
        log.info("结账凭证: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public void batchPost(List<Long> ids, Long userId) {
        for (Long id : ids) {
            VoucherEntity entity = getValidVoucher(id);
            assertStatus(entity, "AUDITED");
            assertPeriodOpen(entity.getPeriod());
        }

        voucherMapper.batchUpdateStatus(ids, "POSTED", userId);

        for (Long id : ids) {
            VoucherEntity entity = getValidVoucher(id);
            List<VoucherEntryEntity> entries = voucherEntryMapper.selectByVoucherId(id);
            subjectBalanceService.updateBalanceOnPost(entity, entries);
        }

        log.info("批量记账凭证: ids={}, userId={}", ids, userId);
    }

    @Override
    @Transactional
    public VoucherVO reverse(Long id, Long userId) {
        VoucherEntity original = getValidVoucher(id);
        if (!"POSTED".equals(original.getStatus()) && !"AUDITED".equals(original.getStatus())) {
            throw BusinessException.badRequest("仅已审核或已记账的凭证可红冲");
        }

        // 检查是否已被红冲
        LambdaQueryWrapper<VoucherEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoucherEntity::getReversedFrom, id)
                .eq(VoucherEntity::getDeleted, 0);
        if (voucherMapper.selectCount(wrapper) > 0) {
            throw BusinessException.badRequest("该凭证已被红冲");
        }

        // 加载原分录
        List<VoucherEntryEntity> originalEntries = voucherEntryMapper.selectByVoucherId(id);

        // 生成红冲凭证号
        String reverseNo = voucherNoService.generateNextNo(original.getPeriod(), original.getVoucherTypeId());

        // 创建红冲凭证(金额取反)
        VoucherEntity reverseVoucher = new VoucherEntity();
        reverseVoucher.setVoucherNo(reverseNo);
        reverseVoucher.setPeriod(original.getPeriod());
        reverseVoucher.setVoucherTypeId(original.getVoucherTypeId());
        reverseVoucher.setStatus("DRAFT");
        reverseVoucher.setSource("REVERSAL");
        reverseVoucher.setSummary("红冲凭证: " + original.getVoucherNo());
        reverseVoucher.setTotalDebit(original.getTotalCredit());
        reverseVoucher.setTotalCredit(original.getTotalDebit());
        reverseVoucher.setCreatedBy(userId);
        reverseVoucher.setReversedFrom(id);
        voucherMapper.insert(reverseVoucher);

        // 创建红字分录(借贷互换)
        for (VoucherEntryEntity originalEntry : originalEntries) {
            VoucherEntryEntity entry = new VoucherEntryEntity();
            entry.setVoucherId(reverseVoucher.getId());
            entry.setSubjectId(originalEntry.getSubjectId());
            entry.setDebit(originalEntry.getCredit());
            entry.setCredit(originalEntry.getDebit());
            entry.setSummary("红冲: " + originalEntry.getSummary());
            entry.setAssistJson(originalEntry.getAssistJson());
            entry.setSortOrder(originalEntry.getSortOrder());
            voucherEntryMapper.insert(entry);
        }

        log.info("红冲凭证: originalId={}, reverseId={}, voucherNo={}, userId={}",
                id, reverseVoucher.getId(), reverseNo, userId);

        // P0: 凭证红冲级联 — 回写源单据状态
        cascadeReverseToSourceDocs(original, userId);

        return getDetail(reverseVoucher.getId());
    }

    /**
     * P0: 凭证红冲后级联回写源发票/业务单据状态为 REVERSED。
     */
    private void cascadeReverseToSourceDocs(VoucherEntity original, Long userId) {
        String sourceDocType = original.getSourceDocType();
        Long sourceDocId = original.getSourceDocId();
        String sourceDocNo = original.getSourceDocNo();

        if (sourceDocType == null || sourceDocId == null) {
            log.info("凭证无源单据信息，跳过级联: voucherId={}", original.getId());
            return;
        }

        switch (sourceDocType) {
            case "OUTPUT_INVOICE":
                // 1. 标记销售发票为 REVERSED
                OutputInvoiceEntity invoice = outputInvoiceMapper.selectById(sourceDocId);
                if (invoice != null && !"REVERSED".equals(invoice.getStatus())) {
                    invoice.setStatus("REVERSED");
                    invoice.setUpdatedBy(userId);
                    outputInvoiceMapper.updateById(invoice);
                    log.info("P0 凭证红冲级联: 发票已标记 REVERSED, invoiceId={}", sourceDocId);
                }
                // 2. 标记关联的业务单据为 REVERSED
                if (sourceDocNo != null) {
                    BusinessDocEntity doc = businessDocMapper.selectOne(
                            new LambdaQueryWrapper<BusinessDocEntity>()
                                    .eq(BusinessDocEntity::getInvoiceNo, sourceDocNo)
                                    .eq(BusinessDocEntity::getDocType, "INVOICE_OUT")
                                    .last("LIMIT 1"));
                    if (doc != null && !"REVERSED".equals(doc.getStatus())) {
                        doc.setStatus("REVERSED");
                        doc.setUpdatedBy(userId);
                        businessDocMapper.updateById(doc);
                        log.info("P0 凭证红冲级联: 业务单据已标记 REVERSED, docId={}", doc.getId());
                    }
                }
                break;

            case "BUSINESS_DOC":
                // 标记业务单据为 REVERSED
                BusinessDocEntity bdoc = businessDocMapper.selectById(sourceDocId);
                if (bdoc != null && !"REVERSED".equals(bdoc.getStatus())) {
                    bdoc.setStatus("REVERSED");
                    bdoc.setUpdatedBy(userId);
                    businessDocMapper.updateById(bdoc);
                    log.info("P0 凭证红冲级联: 业务单据已标记 REVERSED, docId={}", sourceDocId);
                }
                break;

            case "INPUT_INVOICE":
                log.info("P0 凭证红冲级联: 进项发票级联暂不处理, sourceDocId={}", sourceDocId);
                break;

            default:
                log.info("P0 凭证红冲级联: 未知源单据类型={}, 跳过", sourceDocType);
        }
    }

    // ===================== 内部方法 =====================

    /**
     * 校验分录: 至少2条、借贷平衡、金额非负、每条至少一方非零
     */
    private void validateEntries(List<EntryDTO> entries) {
        if (entries.size() < 2) {
            throw BusinessException.badRequest("凭证至少需要2条分录");
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (int i = 0; i < entries.size(); i++) {
            EntryDTO entry = entries.get(i);
            if (entry.getDebit().compareTo(BigDecimal.ZERO) < 0
                    || entry.getCredit().compareTo(BigDecimal.ZERO) < 0) {
                throw BusinessException.badRequest("分录金额不能为负数, 第" + (i + 1) + "条");
            }
            // 每条分录至少一方非零
            if (entry.getDebit().compareTo(BigDecimal.ZERO) == 0
                    && entry.getCredit().compareTo(BigDecimal.ZERO) == 0) {
                throw BusinessException.badRequest("第" + (i + 1) + "条分录借贷均为零, 无效");
            }
            totalDebit = totalDebit.add(entry.getDebit());
            totalCredit = totalCredit.add(entry.getCredit());
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw BusinessException.badRequest("借贷不平衡: 借方=" + totalDebit + ", 贷方=" + totalCredit);
        }
    }

    /**
     * 构建分录实体列表
     */
    private List<VoucherEntryEntity> buildEntries(Long voucherId, List<EntryDTO> entryDTOS) {
        List<VoucherEntryEntity> entries = new ArrayList<>();
        for (int i = 0; i < entryDTOS.size(); i++) {
            EntryDTO dto = entryDTOS.get(i);
            VoucherEntryEntity entry = new VoucherEntryEntity();
            entry.setVoucherId(voucherId);
            entry.setSubjectId(dto.getSubjectId());
            entry.setDebit(dto.getDebit());
            entry.setCredit(dto.getCredit());
            entry.setSummary(dto.getSummary());
            entry.setAssistJson(dto.getAssistJson());
            entry.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : i + 1);
            entries.add(entry);
        }
        return entries;
    }

    /**
     * 获取有效凭证(未被逻辑删除)
     */
    private VoucherEntity getValidVoucher(Long id) {
        VoucherEntity entity = voucherMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("凭证不存在");
        }
        return entity;
    }

    /**
     * 断言凭证状态
     */
    private void assertStatus(VoucherEntity entity, String expectedStatus) {
        if (!expectedStatus.equals(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "凭证状态不正确, 期望=" + expectedStatus + ", 当前=" + entity.getStatus());
        }
    }

    /**
     * 校验期间是否可操作（未关闭、未锁定）
     */
    private void assertPeriodOpen(String period) {
        LambdaQueryWrapper<PeriodEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PeriodEntity::getPeriodCode, period)
                .eq(PeriodEntity::getDeleted, 0);
        PeriodEntity periodEntity = periodService.getOne(wrapper);
        if (periodEntity == null) {
            throw BusinessException.badRequest("会计期间不存在: " + period);
        }
        if ("closed".equals(periodEntity.getStatus())) {
            throw BusinessException.badRequest("会计期间已结账, 不可操作: " + period);
        }
        if ("locked".equals(periodEntity.getStatus())) {
            throw BusinessException.badRequest("会计期间已锁定, 不可操作: " + period);
        }
    }

    /**
     * 校验分录科目是否为末级科目
     */
    private void validateLeafSubjects(List<EntryDTO> entries) {
        for (int i = 0; i < entries.size(); i++) {
            EntryDTO entry = entries.get(i);
            Subject subject = subjectService.getById(entry.getSubjectId());
            if (subject == null) {
                throw BusinessException.notFound("科目不存在: " + entry.getSubjectId());
            }
            if (!Boolean.TRUE.equals(subject.getIsLeaf())) {
                throw BusinessException.badRequest(
                        "第" + (i + 1) + "条分录科目非末级科目, 不可直接记账: " + subject.getCode() + " - " + subject.getName());
            }
        }
    }

    /**
     * P1-4: 辅助核算强校验拦截 — 若科目配置了 auxCalcType, 则 assistJson 必须包含对应字段.
     */
    private void validateAssistJson(List<EntryDTO> entries) {
        for (int i = 0; i < entries.size(); i++) {
            EntryDTO entry = entries.get(i);
            Subject subject = subjectService.getById(entry.getSubjectId());
            if (subject == null) continue;
            String auxCalcType = subject.getAuxCalcType();
            if (auxCalcType == null || auxCalcType.isBlank()) continue;

            String assistJson = entry.getAssistJson();
            if (assistJson == null || assistJson.isBlank()) {
                throw BusinessException.badRequest(
                        "第" + (i + 1) + "条分录科目(" + subject.getCode() + ")启用了[" + auxCalcType + "]辅助核算, 必须填写辅助核算信息");
            }

            String requiredField;
            switch (auxCalcType) {
                case "customer":   requiredField = "customerId"; break;
                case "vendor":     requiredField = "vendorId";   break;
                case "department": requiredField = "deptId";     break;
                case "project":    requiredField = "projectId";  break;
                case "employee":   requiredField = "employeeId"; break;
                default:
                    throw BusinessException.badRequest("不支持的辅助核算类型: " + auxCalcType);
            }

            try {
                Map<String, Object> assistMap = objectMapper.readValue(assistJson,
                        new TypeReference<Map<String, Object>>() {});
                Object val = assistMap.get(requiredField);
                if (val == null || (val instanceof String && ((String) val).isBlank())) {
                    throw BusinessException.badRequest(
                            "第" + (i + 1) + "条分录科目(" + subject.getCode() + ")启用了[" + auxCalcType + "]辅助核算, 缺少字段: " + requiredField);
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw BusinessException.badRequest(
                        "第" + (i + 1) + "条分录辅助核算JSON格式错误: " + e.getMessage());
            }
        }
    }

    /**
     * 凭证实体转VO(不含分录)
     */
    private VoucherVO toVoucherVO(VoucherEntity entity) {
        VoucherVO vo = new VoucherVO();
        vo.setId(entity.getId());
        vo.setVoucherNo(entity.getVoucherNo());
        vo.setPeriod(entity.getPeriod());
        vo.setVoucherTypeId(entity.getVoucherTypeId());
        vo.setStatus(entity.getStatus());
        vo.setTotalDebit(entity.getTotalDebit());
        vo.setTotalCredit(entity.getTotalCredit());
        vo.setSummary(entity.getSummary());
        vo.setSource(entity.getSource());
        vo.setAttachmentIds(entity.getAttachmentIds());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedBy(entity.getUpdatedBy());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setSubmittedBy(entity.getSubmittedBy());
        vo.setSubmittedAt(entity.getSubmittedAt());
        vo.setAuditedBy(entity.getAuditedBy());
        vo.setAuditedAt(entity.getAuditedAt());
        vo.setPostedBy(entity.getPostedBy());
        vo.setPostedAt(entity.getPostedAt());
        vo.setReversedFrom(entity.getReversedFrom());

        // 查询类型名称
        if (entity.getVoucherTypeId() != null) {
            VoucherTypeEntity type = voucherTypeService.getById(entity.getVoucherTypeId());
            if (type != null) {
                vo.setVoucherTypeName(type.getName());
                vo.setVoucherTypeCode(type.getCode());
            }
        }

        return vo;
    }

    /**
     * 分录实体转VO
     */
    private EntryVO toEntryVO(VoucherEntryEntity entity) {
        EntryVO vo = new EntryVO();
        vo.setId(entity.getId());
        vo.setSubjectId(entity.getSubjectId());
        vo.setDebit(entity.getDebit());
        vo.setCredit(entity.getCredit());
        vo.setSummary(entity.getSummary());
        vo.setAssistJson(entity.getAssistJson());
        vo.setSortOrder(entity.getSortOrder());

        // 查询科目编码和名称
        if (entity.getSubjectId() != null) {
            Subject subject = subjectService.getById(entity.getSubjectId());
            if (subject != null) {
                vo.setSubjectCode(subject.getCode());
                vo.setSubjectName(subject.getName());
            }
        }

        return vo;
    }

    /**
     * P2: 回填凭证关联的发票状态和业务单据状态
     */
    private void populateRelatedStatuses(List<VoucherVO> vos) {
        for (VoucherVO vo : vos) {
            if (vo.getSourceDocId() != null && "OUTPUT_INVOICE".equals(vo.getSourceDocType())) {
                OutputInvoiceEntity inv = outputInvoiceMapper.selectById(vo.getSourceDocId());
                if (inv != null) {
                    vo.setInvoiceStatus(inv.getStatus());
                }
            }
            if (vo.getSourceDocId() != null && "BUSINESS_DOC".equals(vo.getSourceDocType())) {
                BusinessDocEntity doc = businessDocMapper.selectById(vo.getSourceDocId());
                if (doc != null) {
                    vo.setBusinessDocStatus(doc.getStatus());
                }
            }
        }
    }

    /**
     * 校验凭证摘要不可为空
     */
    private void validateSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            throw BusinessException.badRequest("凭证摘要不能为空, 请填写【对方单位+业务性质】格式");
        }
    }

    /**
     * 回填制单人/提交人/审核人/记账人真实姓名
     */
    private void populateUserNames(List<VoucherVO> vos) {
        if (vos.isEmpty()) return;
        List<Long> userIds = vos.stream()
                .flatMap(vo -> java.util.stream.Stream.of(vo.getCreatedBy(), vo.getSubmittedBy(), vo.getAuditedBy(), vo.getPostedBy()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) return;
        Map<Long, String> userNameMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, this::resolveUserDisplayName));
        for (VoucherVO vo : vos) {
            if (vo.getCreatedBy() != null) vo.setCreatedByName(userNameMap.get(vo.getCreatedBy()));
            if (vo.getSubmittedBy() != null) vo.setSubmittedByName(userNameMap.get(vo.getSubmittedBy()));
            if (vo.getAuditedBy() != null) vo.setAuditedByName(userNameMap.get(vo.getAuditedBy()));
            if (vo.getPostedBy() != null) vo.setPostedByName(userNameMap.get(vo.getPostedBy()));
        }
    }

    private String resolveUserDisplayName(UserEntity user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) return user.getRealName();
        if (user.getNickname() != null && !user.getNickname().isBlank()) return user.getNickname();
        return user.getUsername();
    }
}
