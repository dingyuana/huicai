package com.huicai.sme.arap.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.constant.BusinessDocStatus;
import com.huicai.sme.arap.dto.BusinessDocDTO;
import com.huicai.sme.arap.dto.BusinessDocQueryDTO;
import com.huicai.sme.arap.dto.BusinessDocVO;
import com.huicai.sme.arap.entity.BusinessDocEntity;
import com.huicai.sme.arap.entity.BusinessDocEntryEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.base.voucher.entity.VoucherTemplateLineEntity;
import com.huicai.sme.arap.mapper.BusinessDocEntryMapper;
import com.huicai.sme.arap.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.sme.arap.service.BusinessDocService;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.base.voucher.service.VoucherTemplateService;
import com.huicai.sme.arap.service.TemplateMatcher;
import com.huicai.common.util.TemplateEngine;
import com.huicai.common.util.TemplateContext;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.system.service.VoucherTypeService;
import com.huicai.sme.tax.entity.OutputInvoiceEntity;
import com.huicai.sme.tax.mapper.OutputInvoiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessDocServiceImpl implements BusinessDocService {

    private static final String DOC_NO_REDIS_PREFIX = "doc:no:";
    private static final Map<String, String> DOC_TYPE_CODE = Map.of(
            "RECEIPT", "SK", "PAYMENT", "FK", "EXPENSE", "BX",
            "INVOICE_IN", "FPR", "INVOICE_OUT", "FPS", "OTHER_RECEIVABLE", "QTY", "OTHER_PAYABLE", "QTF"
    );

    /** 付/收 标识: 需要"付"前缀的单据类型 */
    private static final java.util.Set<String> SUPPLIER_DOC_TYPES = java.util.Set.of(
            "PAYMENT", "EXPENSE", "INVOICE_IN", "OTHER_PAYABLE"
    );

    /**
     * 业务单据 → 凭证 科目映射 (硬编码降级).
     * 格式: docType → [{"debit": "科目代码", "credit": "科目代码"}]
     */
    private static List<String[]> pair(String... codes) {
        return java.util.Collections.singletonList(codes);
    }

    private static final Map<String, List<String[]>> DOC_VOUCHER_SUBJECTS = Map.ofEntries(
            Map.entry("RECEIPT",            pair("1002", "1122")),
            Map.entry("PAYMENT",            pair("2202", "1002")),
            Map.entry("EXPENSE",            pair("6602", "1002")),
            Map.entry("INVOICE_IN",         pair("1403", "2202")),
            Map.entry("INVOICE_OUT",        pair("1122", "6001")),
            Map.entry("OTHER_RECEIVABLE",   pair("1221", "1002")),
            Map.entry("OTHER_PAYABLE",      pair("1002", "2241"))
    );

    private final BusinessDocMapper docMapper;
    private final BusinessDocEntryMapper docEntryMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final StringRedisTemplate redisTemplate;
    private final PeriodService periodService;
    private final SubjectMapper subjectMapper;
    private final VoucherTypeService voucherTypeService;
    private final CustomerMapper customerMapper;
    private final VendorMapper vendorMapper;
    private final UserMapper userMapper;
    private final TemplateMatcher templateMatcher;
    private final VoucherTemplateService voucherTemplateService;
    private final OutputInvoiceMapper outputInvoiceMapper;

    @Override
    public IPage<BusinessDocVO> pageQuery(BusinessDocQueryDTO q) {
        Page<BusinessDocEntity> page = new Page<>(q.getCurrent(), q.getSize());
        LambdaQueryWrapper<BusinessDocEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(q.getDocType()), BusinessDocEntity::getDocType, q.getDocType())
                .in(q.getDocTypes() != null && !q.getDocTypes().isEmpty(), BusinessDocEntity::getDocType, q.getDocTypes())
                .eq(StrUtil.isNotBlank(q.getStatus()), BusinessDocEntity::getStatus, q.getStatus())
                .eq(StrUtil.isNotBlank(q.getPeriod()), BusinessDocEntity::getPeriod, q.getPeriod())
                .and(StrUtil.isNotBlank(q.getKeyword()), w -> w
                        .like(BusinessDocEntity::getDocNo, q.getKeyword())
                        .or().like(BusinessDocEntity::getVoucherNo, q.getKeyword())
                        .or().like(BusinessDocEntity::getInvoiceNo, q.getKeyword())
                        .or().like(BusinessDocEntity::getSummary, q.getKeyword()))
                .eq(StrUtil.isNotBlank(q.getVoucherNo()), BusinessDocEntity::getVoucherNo, q.getVoucherNo())
                .orderByDesc(BusinessDocEntity::getDocDate)
                .orderByDesc(BusinessDocEntity::getId);
        IPage<BusinessDocEntity> entityPage = docMapper.selectPage(page, wrapper);

        IPage<BusinessDocVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        List<BusinessDocEntity> entities = entityPage.getRecords();
        List<BusinessDocVO> vos = entities.stream().map(BusinessDocVO::fromEntity).toList();
        populatePartyNames(vos);
        populateUserNames(vos);
        populateVoucherNos(vos);
        populateInvoiceStatuses(vos);
        for (int i = 0; i < entities.size(); i++) {
            vos.get(i).setEnrichedSummary(enrichSummary(entities.get(i)));
        }
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public BusinessDocVO getDetail(Long id) {
        BusinessDocEntity entity = docMapper.selectById(id);
        if (entity == null) throw BusinessException.notFound("单据不存在");
        BusinessDocVO vo = BusinessDocVO.fromEntity(entity);
        List<BusinessDocEntryEntity> entries = docEntryMapper.selectByDocId(id);
        vo.setEntries(entries.stream().map(BusinessDocVO::fromEntryEntity).toList());
        for (BusinessDocVO.EntryVO e : vo.getEntries()) {
            if (e.getSubjectId() != null) {
                Subject s = subjectMapper.selectById(e.getSubjectId());
                if (s != null) {
                    e.setSubjectCode(s.getCode());
                    e.setSubjectName(s.getName());
                }
            }
        }
        populatePartyNames(List.of(vo));
        populateUserNames(List.of(vo));
        populateVoucherNos(List.of(vo));
        populateInvoiceStatuses(List.of(vo));
        vo.setEnrichedSummary(enrichSummary(entity));
        populateSettlementAmounts(vo, entity);
        return vo;
    }

    private void populateSettlementAmounts(BusinessDocVO vo, BusinessDocEntity entity) {
        // P34: 结算金额直接来自业务单据自身字段（INVOICE_OUT / INVOICE_IN）
        // BusinessDocVO.fromEntity() 已读取 entity 的 settledAmount/unsettledAmount
        // 这里确保 VO 中的值同步
        if (entity.getSettledAmount() != null) {
            vo.setSettledAmount(entity.getSettledAmount());
        }
        if (entity.getUnsettledAmount() != null) {
            vo.setUnsettledAmount(entity.getUnsettledAmount());
        }
    }

    @Override
    @Transactional
    public BusinessDocVO create(BusinessDocDTO dto, Long userId) {
        validatePeriodOpen(dto.getPeriod());
        if (dto.getEntries() == null || dto.getEntries().isEmpty()) {
            throw BusinessException.badRequest("单据至少需要1条分录");
        }
        BusinessDocEntity entity = BusinessDocDTO.toEntity(dto);
        if (StrUtil.isBlank(entity.getDocNo())) {
            entity.setDocNo(generateDocNo(entity.getDocType(), entity.getPeriod()));
        }
        if (StrUtil.isBlank(entity.getStatus())) {
            entity.setStatus(BusinessDocStatus.DRAFT);
        }
        if (StrUtil.isBlank(entity.getSource())) {
            entity.setSource("MANUAL");
        }
        // 新单据未核销金额 = 总金额（P34 核销工作台筛选条件）
        if (entity.getSettledAmount() == null) {
            entity.setSettledAmount(BigDecimal.ZERO);
        }
        if (entity.getUnsettledAmount() == null) {
            entity.setUnsettledAmount(entity.getAmount());
        }
        entity.setCreatedBy(userId);
        docMapper.insert(entity);

        for (int i = 0; i < dto.getEntries().size(); i++) {
            BusinessDocEntryEntity e = BusinessDocDTO.toEntryEntity(entity.getId(), dto.getEntries().get(i), i + 1);
            docEntryMapper.insert(e);
        }
        log.info("创建业务单据: id={}, docNo={}, type={}", entity.getId(), entity.getDocNo(), entity.getDocType());
        return getDetail(entity.getId());
    }

    @Override
    @Transactional
    public BusinessDocVO update(BusinessDocDTO dto, Long userId) {
        if (dto.getId() == null) throw BusinessException.badRequest("更新时单据ID不能为空");
        BusinessDocEntity entity = docMapper.selectById(dto.getId());
        if (entity == null) throw BusinessException.notFound("单据不存在");
        if (!"DRAFT".equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅草稿状态单据可修改");
        }
        validatePeriodOpen(entity.getPeriod());
        entity.setDocType(dto.getDocType());
        entity.setDocDate(dto.getDocDate());
        entity.setPeriod(dto.getPeriod());
        entity.setAmount(dto.getAmount());
        // 可选字段: dto 缺省时保留原值, 避免前端 loadDoc 漏字段导致数据被清空
        if (dto.getSupplierId() != null) entity.setSupplierId(dto.getSupplierId());
        if (dto.getCustomerId() != null) entity.setCustomerId(dto.getCustomerId());
        if (dto.getApplicantId() != null) entity.setApplicantId(dto.getApplicantId());
        if (dto.getDeptId() != null) entity.setDeptId(dto.getDeptId());
        if (dto.getSummary() != null) entity.setSummary(dto.getSummary());
        if (StrUtil.isNotBlank(dto.getAttachmentIds())) entity.setAttachmentIds(dto.getAttachmentIds());
        entity.setUpdatedBy(userId);
        docMapper.updateById(entity);

        docEntryMapper.deleteByDocId(entity.getId());
        for (int i = 0; i < dto.getEntries().size(); i++) {
            BusinessDocEntryEntity e = BusinessDocDTO.toEntryEntity(entity.getId(), dto.getEntries().get(i), i + 1);
            docEntryMapper.insert(e);
        }
        return getDetail(entity.getId());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        BusinessDocEntity entity = docMapper.selectById(id);
        if (entity == null) throw BusinessException.notFound("单据不存在");
        if (!"DRAFT".equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅草稿状态单据可删除");
        }
        docMapper.deleteById(id);
        docEntryMapper.deleteByDocId(id);
    }

    @Override
    @Transactional
    public void submit(Long id, Long userId) {
        BusinessDocEntity entity = getValid(id);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅草稿状态可提交");
        }
        entity.setStatus(BusinessDocStatus.SUBMITTED);
        entity.setSubmittedBy(userId);
        entity.setSubmittedAt(LocalDateTime.now());
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void approve(Long id, Long userId) {
        BusinessDocEntity entity = getValid(id);
        if (!"SUBMITTED".equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅已提交状态可审批");
        }
        entity.setStatus(BusinessDocStatus.APPROVED);
        entity.setApprovedBy(userId);
        entity.setApprovedAt(LocalDateTime.now());
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void reject(Long id, Long userId) {
        BusinessDocEntity entity = getValid(id);
        if (!"SUBMITTED".equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅已提交状态可驳回");
        }
        entity.setStatus(BusinessDocStatus.REJECTED);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(entity);
    }

    @Override
    @Transactional
    public BusinessDocVO generateVoucher(Long id, Long userId) {
        BusinessDocEntity entity = getValid(id);
        // P34: INVOICE_OUT（销售发票应收单）已合并到业务单据体系，允许从单据列表生成凭证
        if (!"APPROVED".equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅已审批状态可生成凭证");
        }
        if (entity.getVoucherId() != null) {
            throw BusinessException.badRequest("该单据已生成凭证");
        }

        // 1. 按模板生成（配置驱动）
        TemplateContext ctx = new TemplateContext()
                .setSource("BUSINESS_DOC")
                .setBusinessType(entity.getDocType())
                .setAmount(entity.getAmount())
                .setPeriod(entity.getPeriod());
        // 设置客户/供应商名称（§91 B 方案：通过 customerId/supplierId 关联查 name，软失败）
        if (entity.getCustomerId() != null) {
            CustomerEntity customer = customerMapper.selectById(entity.getCustomerId());
            if (customer != null && StrUtil.isNotBlank(customer.getName())) {
                ctx.setCustomerName(customer.getName());
            }
        }
        if (entity.getSupplierId() != null) {
            VendorEntity vendor = vendorMapper.selectById(entity.getSupplierId());
            if (vendor != null && StrUtil.isNotBlank(vendor.getName())) {
                ctx.setVendorName(vendor.getName());
            }
        }
        ctx.getVariables().put("docNo", entity.getDocNo());

        VoucherTemplateEntity template = templateMatcher.match(ctx);
        if (template != null) {
            List<VoucherTemplateLineEntity> tplLines = voucherTemplateService.getLines(template.getId());
            if (tplLines != null && !tplLines.isEmpty()) {
                return generateFromTemplate(entity, template, tplLines, ctx, userId);
            }
        }

        // 2. 降级: 硬编码科目映射
        List<String[]> subjectPairs = DOC_VOUCHER_SUBJECTS.get(entity.getDocType());
        if (subjectPairs == null || subjectPairs.isEmpty()) {
            throw BusinessException.badRequest("未找到 " + entity.getDocType() + " 对应的凭证科目映射, 请先配置模板");
        }

        List<BusinessDocEntryEntity> docEntries = docEntryMapper.selectByDocId(id);
        String enrichedSummary = enrichSummary(entity);

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNoService.generateNextNo(entity.getPeriod(), 1L));
        voucher.setPeriod(entity.getPeriod());
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(enrichedSummary);
        voucher.setTotalDebit(BigDecimal.ZERO);
        voucher.setTotalCredit(BigDecimal.ZERO);
        voucher.setCreatedBy(userId);
        // 新增：溯源字段（业务单据 → 凭证）
        voucher.setSourceDocType("BUSINESS_DOC");
        voucher.setSourceDocNo(entity.getDocNo());
        voucherMapper.insert(voucher);

        BigDecimal totalD = BigDecimal.ZERO;
        BigDecimal totalC = BigDecimal.ZERO;
        int sortOrder = 1;
        for (BusinessDocEntryEntity docEntry : docEntries) {
            BigDecimal amount = docEntry.getAmount();
            for (String[] pair : subjectPairs) {
                String debitSubjectCode = pair[0];
                String creditSubjectCode = pair.length > 1 ? pair[1] : null;

                Subject debitSubj = findSubjectByCode(debitSubjectCode);
                Subject creditSubj = creditSubjectCode != null ? findSubjectByCode(creditSubjectCode) : null;

                if (debitSubj != null) {
                    VoucherEntryEntity ve = new VoucherEntryEntity();
                    ve.setVoucherId(voucher.getId());
                    ve.setSubjectId(debitSubj.getId());
                    ve.setDebit(amount);
                    ve.setCredit(BigDecimal.ZERO);
                    ve.setSummary(docEntry.getSummary() != null ? docEntry.getSummary() : enrichedSummary);
                    ve.setAssistJson(docEntry.getAssistJson());
                    ve.setSortOrder(sortOrder++);
                    voucherEntryMapper.insert(ve);
                    totalD = totalD.add(amount);
                }
                if (creditSubj != null) {
                    VoucherEntryEntity ve = new VoucherEntryEntity();
                    ve.setVoucherId(voucher.getId());
                    ve.setSubjectId(creditSubj.getId());
                    ve.setDebit(BigDecimal.ZERO);
                    ve.setCredit(amount);
                    ve.setSummary(docEntry.getSummary() != null ? docEntry.getSummary() : enrichedSummary);
                    ve.setAssistJson(docEntry.getAssistJson());
                    ve.setSortOrder(sortOrder++);
                    voucherEntryMapper.insert(ve);
                    totalC = totalC.add(amount);
                }
            }
        }

        voucher.setTotalDebit(totalD);
        voucher.setTotalCredit(totalC);
        voucherMapper.updateById(voucher);

        entity.setVoucherId(voucher.getId());
        entity.setVoucherNo(voucher.getVoucherNo());
        entity.setStatus(BusinessDocStatus.VOUCHERED);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(entity);

        log.info("单据生成凭证: docId={}, voucherId={}, voucherNo={}",
                id, voucher.getId(), voucher.getVoucherNo());
        return getDetail(id);
    }

    @Override
    @Transactional
    public BusinessDocVO reverse(Long id, Long userId) {
        BusinessDocEntity entity = getValid(id);
        if (!"VOUCHERED".equals(entity.getStatus()) && !"APPROVED".equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅已审批或已生成凭证单据可红冲");
        }
        if (entity.getVoucherId() == null) {
            throw BusinessException.badRequest("单据未生成凭证, 无需红冲");
        }
        BusinessDocEntity reverse = new BusinessDocEntity();
        reverse.setDocNo(generateDocNo(entity.getDocType(), entity.getPeriod()));
        reverse.setDocType(entity.getDocType());
        reverse.setDocDate(entity.getDocDate());
        reverse.setPeriod(entity.getPeriod());
        reverse.setAmount(entity.getAmount().negate());
        reverse.setStatus(BusinessDocStatus.DRAFT);
        reverse.setSupplierId(entity.getSupplierId());
        reverse.setCustomerId(entity.getCustomerId());
        reverse.setApplicantId(entity.getApplicantId());
        reverse.setDeptId(entity.getDeptId());
        reverse.setSummary("红冲自 " + entity.getDocNo());
        reverse.setReversedFrom(entity.getId());
        reverse.setSource("MANUAL");
        reverse.setCreatedBy(userId);
        docMapper.insert(reverse);
        List<BusinessDocEntryEntity> srcEntries = docEntryMapper.selectByDocId(entity.getId());
        for (int i = 0; i < srcEntries.size(); i++) {
            BusinessDocEntryEntity src = srcEntries.get(i);
            BusinessDocEntryEntity dup = new BusinessDocEntryEntity();
            dup.setDocId(reverse.getId());
            dup.setExpenseType(src.getExpenseType());
            dup.setSubjectId(src.getSubjectId());
            dup.setAmount(src.getAmount().negate());
            dup.setInvoiceNo(src.getInvoiceNo());
            dup.setAssistJson(src.getAssistJson());
            dup.setSummary("红冲: " + (src.getSummary() != null ? src.getSummary() : ""));
            dup.setSortOrder(i + 1);
            docEntryMapper.insert(dup);
        }
        return getDetail(reverse.getId());
    }

    /**
     * 按模板生成凭证（替代硬编码 DOC_VOUCHER_SUBJECTS）.
     */
    private BusinessDocVO generateFromTemplate(BusinessDocEntity entity,
                                                VoucherTemplateEntity template,
                                                List<VoucherTemplateLineEntity> tplLines,
                                                TemplateContext ctx,
                                                Long userId) {
        List<BusinessDocEntryEntity> docEntries = docEntryMapper.selectByDocId(entity.getId());
        String enrichedSummary = enrichSummary(entity);

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNoService.generateNextNo(entity.getPeriod(), 1L));
        voucher.setPeriod(entity.getPeriod());
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(enrichedSummary);
        voucher.setTemplateId(template.getId());
        voucher.setCreatedBy(userId);
        // 新增：溯源字段（业务单据 → 凭证）
        voucher.setSourceDocType("BUSINESS_DOC");
        voucher.setSourceDocNo(entity.getDocNo());
        voucherMapper.insert(voucher);

        int sortOrder = 1;
        for (BusinessDocEntryEntity docEntry : docEntries) {
            BigDecimal amt = docEntry.getAmount() != null ? docEntry.getAmount() : entity.getAmount();
            // 用 TemplateContext 设当前分录金额
            ctx.setAmount(amt);
            if (docEntry.getSummary() != null) ctx.setSummary(docEntry.getSummary());
            else ctx.setSummary(enrichedSummary);

            for (VoucherTemplateLineEntity tplLine : tplLines) {
                BigDecimal dr = TemplateEngine.renderAmount(tplLine.getDrAmountTemplate(), ctx);
                BigDecimal cr = TemplateEngine.renderAmount(tplLine.getCrAmountTemplate(), ctx);
                if (dr == null) dr = BigDecimal.ZERO;
                if (cr == null) cr = BigDecimal.ZERO;

                if ("debit".equals(tplLine.getDirection())) {
                    if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) > 0) { dr = cr; cr = BigDecimal.ZERO; }
                    else { cr = BigDecimal.ZERO; }
                } else if ("credit".equals(tplLine.getDirection())) {
                    if (cr.compareTo(BigDecimal.ZERO) == 0 && dr.compareTo(BigDecimal.ZERO) > 0) { cr = dr; dr = BigDecimal.ZERO; }
                    else { dr = BigDecimal.ZERO; }
                }
                if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) == 0) continue;

                String summary = TemplateEngine.renderSummary(tplLine.getSummaryTemplate(), ctx);

                VoucherEntryEntity ve = new VoucherEntryEntity();
                ve.setVoucherId(voucher.getId());
                ve.setSubjectId(tplLine.getSubjectId());
                ve.setDebit(dr); ve.setCredit(cr);
                ve.setSummary(summary);
                ve.setAssistJson(docEntry.getAssistJson());
                ve.setSortOrder(sortOrder++);
                voucherEntryMapper.insert(ve);
            }
        }

        // 更新借贷合计
        List<VoucherEntryEntity> allEntries = voucherEntryMapper.selectByVoucherId(voucher.getId());
        BigDecimal totalD = BigDecimal.ZERO, totalC = BigDecimal.ZERO;
        for (VoucherEntryEntity e : allEntries) {
            if (e.getDebit() != null) totalD = totalD.add(e.getDebit());
            if (e.getCredit() != null) totalC = totalC.add(e.getCredit());
        }
        BigDecimal maxAmt = totalD.max(totalC);
        voucher.setTotalDebit(maxAmt);
        voucher.setTotalCredit(maxAmt);
        voucherMapper.updateById(voucher);

        entity.setVoucherId(voucher.getId());
        entity.setVoucherNo(voucher.getVoucherNo());
        entity.setStatus(BusinessDocStatus.VOUCHERED);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(entity);

        log.info("单据模板制证: docId={}, voucherId={}, templateId={}", entity.getId(), voucher.getId(), template.getId());
        return getDetail(entity.getId());
    }

    @Override
    public String generateDocNo(String docType, String period) {
        String typeCode = DOC_TYPE_CODE.getOrDefault(docType, "QT");
        String key = DOC_NO_REDIS_PREFIX + period + ":" + docType;
        Long serial = redisTemplate.opsForValue().increment(key);
        if (serial == null) serial = 1L;
        return typeCode + period + String.format("%04d", serial);
    }

    private Subject findSubjectByCode(String code) {
        Subject s = subjectMapper.selectOne(
                new LambdaQueryWrapper<Subject>()
                        .eq(Subject::getCode, code)
                        .last("LIMIT 1"));
        if (s == null) {
            throw BusinessException.badRequest("科目不存在: " + code + ", 请先在科目管理中初始化");
        }
        return s;
    }

    private String enrichSummary(BusinessDocEntity entity) {
        String base = entity.getSummary() != null && !entity.getSummary().isBlank()
                ? entity.getSummary()
                : "生成自单据 " + entity.getDocNo();
        String partyName = resolvePartyName(entity);
        if (partyName != null && !partyName.isBlank()) {
            String prefix = SUPPLIER_DOC_TYPES.contains(entity.getDocType()) ? "付" : "收";
            base = prefix + partyName + "-" + base;
        }
        // P32: 发票号后缀，用于审计追溯，支持 SQL LIKE '%发票号%' 查询
        if (entity.getInvoiceNo() != null && !entity.getInvoiceNo().isBlank()) {
            base += "[" + entity.getInvoiceNo() + "]";
        }
        return base;
    }

    private String resolvePartyName(BusinessDocEntity entity) {
        if (entity.getSupplierId() != null) {
            VendorEntity v = vendorMapper.selectById(entity.getSupplierId());
            if (v != null) return v.getName();
        }
        if (entity.getCustomerId() != null) {
            CustomerEntity c = customerMapper.selectById(entity.getCustomerId());
            if (c != null) return c.getName();
        }
        return null;
    }

    private BusinessDocEntity getValid(Long id) {
        BusinessDocEntity e = docMapper.selectById(id);
        if (e == null) throw BusinessException.notFound("单据不存在");
        return e;
    }

    private void validatePeriodOpen(String period) {
        PeriodEntity p = periodService.lambdaQuery()
                .eq(PeriodEntity::getPeriodCode, period).one();
        if (p == null) throw BusinessException.badRequest("会计期间不存在: " + period);
        if ("closed".equals(p.getStatus()) || "locked".equals(p.getStatus())) {
            throw BusinessException.badRequest("会计期间不可操作: " + period);
        }
    }

    private void populatePartyNames(List<BusinessDocVO> vos) {
        if (vos.isEmpty()) return;
        Map<Long, String> customerMap = Collections.emptyMap();
        Map<Long, String> vendorMap = Collections.emptyMap();
        List<Long> customerIds = vos.stream().map(BusinessDocVO::getCustomerId).filter(java.util.Objects::nonNull).distinct().toList();
        List<Long> vendorIds = vos.stream().map(BusinessDocVO::getSupplierId).filter(java.util.Objects::nonNull).distinct().toList();
        if (!customerIds.isEmpty()) {
            customerMap = customerMapper.selectBatchIds(customerIds).stream()
                    .collect(Collectors.toMap(CustomerEntity::getId, CustomerEntity::getName));
        }
        if (!vendorIds.isEmpty()) {
            vendorMap = vendorMapper.selectBatchIds(vendorIds).stream()
                    .collect(Collectors.toMap(VendorEntity::getId, VendorEntity::getName));
        }
        for (BusinessDocVO vo : vos) {
            if (vo.getCustomerId() != null) vo.setCustomerName(customerMap.get(vo.getCustomerId()));
            if (vo.getSupplierId() != null) vo.setSupplierName(vendorMap.get(vo.getSupplierId()));
        }
    }

    private void populateUserNames(List<BusinessDocVO> vos) {
        if (vos.isEmpty()) return;
        List<Long> userIds = vos.stream()
                .flatMap(vo -> java.util.stream.Stream.of(vo.getCreatedBy(), vo.getSubmittedBy(), vo.getApprovedBy()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) return;
        Map<Long, String> userNameMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, this::resolveUserDisplayName));
        for (BusinessDocVO vo : vos) {
            if (vo.getCreatedBy() != null) vo.setCreatedByName(userNameMap.get(vo.getCreatedBy()));
            if (vo.getSubmittedBy() != null) vo.setSubmittedByName(userNameMap.get(vo.getSubmittedBy()));
            if (vo.getApprovedBy() != null) vo.setApprovedByName(userNameMap.get(vo.getApprovedBy()));
        }
    }

    /**
     * 批量填充凭证号: 根据 voucherId 查询 t_voucher 获取 voucherNo
     */
    private void populateVoucherNos(List<BusinessDocVO> vos) {
        if (vos.isEmpty()) return;
        List<Long> voucherIds = vos.stream()
                .map(BusinessDocVO::getVoucherId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (voucherIds.isEmpty()) return;
        Map<Long, VoucherEntity> voucherMap = voucherMapper.selectBatchIds(voucherIds).stream()
                .collect(Collectors.toMap(VoucherEntity::getId, v -> v));
        for (BusinessDocVO vo : vos) {
            if (vo.getVoucherId() != null) {
                VoucherEntity v = voucherMap.get(vo.getVoucherId());
                if (v != null) {
                    vo.setVoucherNo(v.getVoucherNo());
                    vo.setVoucherStatus(v.getStatus());  // P2: 关联凭证状态
                }
            }
        }

        // 兜底：voucherId 为空但 invoiceId 有值的业务单据，从发票表查 voucherId/voucherNo 回填
        for (BusinessDocVO vo : vos) {
            if (vo.getVoucherId() == null && vo.getInvoiceId() != null) {
                OutputInvoiceEntity inv = outputInvoiceMapper.selectById(vo.getInvoiceId());
                if (inv != null && inv.getVoucherNo() != null) {
                    vo.setVoucherNo(inv.getVoucherNo());
                    // 如果 voucherId 也是 null，尝试从 voucher 表反向查找
                    if (inv.getVoucherId() != null) {
                        vo.setVoucherId(inv.getVoucherId());
                    }
                    // P2: 关联凭证状态
                    if (inv.getVoucherId() != null) {
                        VoucherEntity v = voucherMapper.selectById(inv.getVoucherId());
                        if (v != null) {
                            vo.setVoucherStatus(v.getStatus());
                        }
                    } else {
                        // 兜底：通过 voucherNo 查找
                        VoucherEntity v = voucherMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                                .eq(VoucherEntity::getVoucherNo, inv.getVoucherNo())
                                .last("LIMIT 1"));
                        if (v != null) {
                            vo.setVoucherStatus(v.getStatus());
                        }
                    }
                }
            }
        }
    }

    /** P2: 回填业务单据关联的发票状态 */
    private void populateInvoiceStatuses(List<BusinessDocVO> vos) {
        for (BusinessDocVO vo : vos) {
            if (vo.getInvoiceNo() != null) {
                OutputInvoiceEntity inv = outputInvoiceMapper.selectOne(
                        new LambdaQueryWrapper<OutputInvoiceEntity>()
                                .eq(OutputInvoiceEntity::getInvoiceNo, vo.getInvoiceNo())
                                .last("LIMIT 1"));
                if (inv != null) {
                    vo.setInvoiceStatus(inv.getStatus());
                }
            }
        }
    }

    private String resolveUserDisplayName(UserEntity user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) return user.getRealName();
        if (user.getNickname() != null && !user.getNickname().isBlank()) return user.getNickname();
        return user.getUsername();
    }
}
