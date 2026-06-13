package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.dto.BusinessDocDTO;
import com.huicai.module.finance.dto.BusinessDocQueryDTO;
import com.huicai.module.finance.dto.BusinessDocVO;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.BusinessDocEntryEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.BusinessDocService;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.system.entity.PeriodEntity;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import com.huicai.module.system.service.PeriodService;
import com.huicai.module.system.service.VoucherTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessDocServiceImpl implements BusinessDocService {

    private static final String DOC_NO_REDIS_PREFIX = "doc:no:";
    private static final Map<String, String> DOC_TYPE_CODE = Map.of(
            "RECEIPT", "SK", "PAYMENT", "FK", "EXPENSE", "BX",
            "INVOICE_IN", "FPR", "INVOICE_OUT", "FPS", "OTHER_RECEIVABLE", "QTY", "OTHER_PAYABLE", "QTF"
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

    @Override
    public IPage<BusinessDocVO> pageQuery(BusinessDocQueryDTO q) {
        Page<BusinessDocEntity> page = new Page<>(q.getCurrent(), q.getSize());
        LambdaQueryWrapper<BusinessDocEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(q.getDocType()), BusinessDocEntity::getDocType, q.getDocType())
                .eq(StrUtil.isNotBlank(q.getStatus()), BusinessDocEntity::getStatus, q.getStatus())
                .eq(StrUtil.isNotBlank(q.getPeriod()), BusinessDocEntity::getPeriod, q.getPeriod())
                .and(StrUtil.isNotBlank(q.getKeyword()), w -> w
                        .like(BusinessDocEntity::getDocNo, q.getKeyword())
                        .or().like(BusinessDocEntity::getSummary, q.getKeyword()))
                .orderByDesc(BusinessDocEntity::getDocDate)
                .orderByDesc(BusinessDocEntity::getId);
        IPage<BusinessDocEntity> entityPage = docMapper.selectPage(page, wrapper);

        IPage<BusinessDocVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(BusinessDocVO::fromEntity).toList());
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
        return vo;
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
            entity.setStatus("DRAFT");
        }
        if (StrUtil.isBlank(entity.getSource())) {
            entity.setSource("MANUAL");
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
        entity.setSupplierId(dto.getSupplierId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setApplicantId(dto.getApplicantId());
        entity.setDeptId(dto.getDeptId());
        entity.setSummary(dto.getSummary());
        entity.setAttachmentIds(dto.getAttachmentIds());
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
        entity.setStatus("SUBMITTED");
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
        entity.setStatus("APPROVED");
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
        entity.setStatus("REJECTED");
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(entity);
    }

    @Override
    @Transactional
    public BusinessDocVO generateVoucher(Long id, Long userId) {
        BusinessDocEntity entity = getValid(id);
        if (!"APPROVED".equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅已审批状态可生成凭证");
        }
        if (entity.getVoucherId() != null) {
            throw BusinessException.badRequest("该单据已生成凭证");
        }
        // 根据 docType 查找科目映射 (硬编码降级)
        List<String[]> subjectPairs = DOC_VOUCHER_SUBJECTS.get(entity.getDocType());
        if (subjectPairs == null || subjectPairs.isEmpty()) {
            throw BusinessException.badRequest("未找到 " + entity.getDocType() + " 对应的凭证科目映射, 请先配置模板");
        }

        List<BusinessDocEntryEntity> docEntries = docEntryMapper.selectByDocId(id);

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNoService.generateNextNo(entity.getPeriod(), 1L));
        voucher.setPeriod(entity.getPeriod());
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(entity.getSummary() != null ? entity.getSummary() : ("生成自单据 " + entity.getDocNo()));
        voucher.setTotalDebit(BigDecimal.ZERO);
        voucher.setTotalCredit(BigDecimal.ZERO);
        voucher.setCreatedBy(userId);
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
                    ve.setSummary(docEntry.getSummary() != null ? docEntry.getSummary() : entity.getSummary());
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
                    ve.setSummary(docEntry.getSummary() != null ? docEntry.getSummary() : entity.getSummary());
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
        entity.setStatus("VOUCHERED");
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
        reverse.setStatus("DRAFT");
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

    @Override
    public String generateDocNo(String docType, String period) {
        String typeCode = DOC_TYPE_CODE.getOrDefault(docType, "QT");
        String key = DOC_NO_REDIS_PREFIX + period + ":" + docType;
        Long serial = redisTemplate.opsForValue().increment(key);
        if (serial == null) serial = 1L;
        return typeCode + period + String.format("%04d", serial);
    }

    private Subject findSubjectByCode(String code) {
        return subjectMapper.selectOne(
                new LambdaQueryWrapper<Subject>()
                        .eq(Subject::getCode, code)
                        .last("LIMIT 1"));
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
        if ("CLOSED".equals(p.getStatus()) || "LOCKED".equals(p.getStatus())) {
            throw BusinessException.badRequest("会计期间不可操作: " + period);
        }
    }
}
