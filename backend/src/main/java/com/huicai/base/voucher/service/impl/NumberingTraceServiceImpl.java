package com.huicai.base.voucher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.ArapSettlementEntryEntity;
import com.huicai.base.business.mapper.ArapSettlementEntryMapper;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.voucher.dto.NumberingTraceVO;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.NumberingTraceService;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 编号关联追溯服务实现
 * 提供全链路双向追溯查询
 */
@Service
@RequiredArgsConstructor
public class NumberingTraceServiceImpl implements NumberingTraceService {

    private static final Logger log = LoggerFactory.getLogger(NumberingTraceServiceImpl.class);

    private final VoucherMapper voucherMapper;
    private final BusinessDocMapper businessDocMapper;
    private final InputInvoiceMapper inputInvoiceMapper;
    private final OutputInvoiceMapper outputInvoiceMapper;
    private final ArapSettlementMapper arapSettlementMapper;
    private final ArapSettlementEntryMapper settlementEntryMapper;
    private final VoucherService voucherService;

    @Override
    public NumberingTraceVO traceByNumber(String traceNo) {
        if (traceNo == null || traceNo.isBlank()) {
            throw new IllegalArgumentException("查询编号不能为空");
        }

        NumberingTraceVO result = new NumberingTraceVO();
        result.setTraceNo(traceNo);

        // 尝试匹配各实体类型
        NumberingTraceVO.TraceNode node = tryMatchByNumber(traceNo);
        if (node == null) {
            result.setTraceType("UNKNOWN");
            result.setUpstream(List.of());
            result.setDownstream(List.of());
            return result;
        }

        result.setTraceType(node.getNodeType());

        // 下游链路（从当前节点向凭证追溯）
        result.setDownstream(buildDownstreamChain(node, traceNo));

        // 上游链路（从当前节点向前追溯）
        result.setUpstream(buildUpstreamChain(node, traceNo));

        return result;
    }

    /**
     * 尝试按编号匹配实体
     */
    private NumberingTraceVO.TraceNode tryMatchByNumber(String traceNo) {
        // 1. 先查凭证
        LambdaQueryWrapper<VoucherEntity> voucherQ = new LambdaQueryWrapper<>();
        voucherQ.eq(VoucherEntity::getVoucherNo, traceNo);
        VoucherEntity voucher = voucherMapper.selectOne(voucherQ);
        if (voucher != null) {
            return buildVoucherNode(voucher);
        }

        // 2. 查业务单据（P34: 应收/应付已合并到业务单据）
        LambdaQueryWrapper<BusinessDocEntity> docQ = new LambdaQueryWrapper<>();
        docQ.eq(BusinessDocEntity::getDocNo, traceNo)
            .or().eq(BusinessDocEntity::getVoucherNo, traceNo)
            .or().eq(BusinessDocEntity::getInvoiceNo, traceNo);
        BusinessDocEntity doc = businessDocMapper.selectOne(docQ);
        if (doc != null) {
            return buildDocNode(doc);
        }

        // 3. 查进项发票
        LambdaQueryWrapper<InputInvoiceEntity> invQ = new LambdaQueryWrapper<>();
        invQ.eq(InputInvoiceEntity::getInvoiceNo, traceNo);
        InputInvoiceEntity inv = inputInvoiceMapper.selectOne(invQ);
        if (inv != null) {
            return buildInputInvoiceNode(inv);
        }

        // 5. 查核销单
        LambdaQueryWrapper<ArapSettlementEntity> setQ = new LambdaQueryWrapper<>();
        setQ.eq(ArapSettlementEntity::getSettlementNo, traceNo)
            .or().eq(ArapSettlementEntity::getVoucherNo, traceNo);
        ArapSettlementEntity set = arapSettlementMapper.selectOne(setQ);
        if (set != null) {
            return buildSettlementNode(set);
        }

        return null;
    }

    // ==================== 节点构建器 ====================

    private NumberingTraceVO.TraceNode buildVoucherNode(VoucherEntity v) {
        NumberingTraceVO.TraceNode node = new NumberingTraceVO.TraceNode();
        node.setNodeType("VOUCHER");
        node.setNodeNo(v.getVoucherNo());
        node.setSummary(v.getSummary());
        node.setStatus(v.getStatus());
        node.setVoucherNo(v.getVoucherNo());
        node.setCreatedAt(v.getCreatedAt());
        return node;
    }

    private NumberingTraceVO.TraceNode buildDocNode(BusinessDocEntity d) {
        NumberingTraceVO.TraceNode node = new NumberingTraceVO.TraceNode();
        node.setNodeType("BUSINESS_DOC");
        node.setNodeNo(d.getDocNo());
        node.setSummary(d.getSummary());
        node.setAmount(d.getAmount());
        node.setStatus(d.getStatus());
        node.setVoucherNo(d.getVoucherNo());
        node.setCreatedAt(d.getCreatedAt());
        return node;
    }

    private NumberingTraceVO.TraceNode buildInputInvoiceNode(InputInvoiceEntity inv) {
        NumberingTraceVO.TraceNode node = new NumberingTraceVO.TraceNode();
        node.setNodeType("INPUT_INVOICE");
        node.setNodeNo(inv.getInvoiceNo());
        node.setSummary("进项发票: " + (inv.getVendorName() != null ? inv.getVendorName() : ""));
        node.setAmount(inv.getTotalAmount());
        node.setStatus(inv.getCertificationStatus());
        node.setVoucherNo(inv.getVoucherNo());
        node.setCreatedAt(inv.getCreatedAt());
        return node;
    }

    private NumberingTraceVO.TraceNode buildOutputInvoiceNode(OutputInvoiceEntity inv) {
        NumberingTraceVO.TraceNode node = new NumberingTraceVO.TraceNode();
        node.setNodeType("OUTPUT_INVOICE");
        node.setNodeNo(inv.getInvoiceNo());
        node.setSummary("销项发票: " + (inv.getCustomerName() != null ? inv.getCustomerName() : ""));
        node.setAmount(inv.getTotalAmount());
        node.setStatus(inv.getStatus());
        node.setVoucherNo(inv.getVoucherNo());
        node.setInvoiceNo(inv.getInvoiceNo());
        node.setCreatedAt(inv.getCreatedAt());
        return node;
    }

    private NumberingTraceVO.TraceNode buildReceivableNode(BusinessDocEntity r) {
        NumberingTraceVO.TraceNode node = new NumberingTraceVO.TraceNode();
        node.setNodeType("RECEIVABLE");
        node.setNodeNo(r.getDocNo());
        node.setSummary(r.getSummary());
        node.setAmount(r.getAmount());
        node.setStatus(r.getStatus());
        node.setVoucherNo(r.getVoucherNo());
        node.setInvoiceNo(r.getInvoiceNo());
        node.setCreatedAt(r.getCreatedAt());
        return node;
    }

    private NumberingTraceVO.TraceNode buildPayableNode(BusinessDocEntity p) {
        NumberingTraceVO.TraceNode node = new NumberingTraceVO.TraceNode();
        node.setNodeType("PAYABLE");
        node.setNodeNo(p.getDocNo());
        node.setSummary(p.getSummary());
        node.setAmount(p.getAmount());
        node.setStatus(p.getStatus());
        node.setVoucherNo(p.getVoucherNo());
        node.setCreatedAt(p.getCreatedAt());
        return node;
    }

    private NumberingTraceVO.TraceNode buildSettlementNode(ArapSettlementEntity s) {
        NumberingTraceVO.TraceNode node = new NumberingTraceVO.TraceNode();
        node.setNodeType("SETTLEMENT");
        node.setNodeNo(s.getSettlementNo());
        node.setSummary("核销单: " + s.getSettlementType());
        node.setAmount(s.getTotalAmount());
        node.setStatus(s.getStatus());
        node.setVoucherNo(s.getVoucherNo());
        node.setCreatedAt(s.getCreatedAt());
        return node;
    }

    // ==================== 下游链路（向凭证方向） ====================

    private List<NumberingTraceVO.TraceNode> buildDownstreamChain(NumberingTraceVO.TraceNode currentNode, String traceNo) {
        List<NumberingTraceVO.TraceNode> chain = new ArrayList<>();
        chain.add(currentNode);

        String nodeType = currentNode.getNodeType();

        switch (nodeType) {
            case "OUTPUT_INVOICE" -> {
                // 销售发票 → 应收单 → 核销单 → 凭证
                chain.add(buildNextDownstreamForInvoice(traceNo, true));
            }
            case "INPUT_INVOICE" -> {
                // 采购发票 → 应付单 → 核销单 → 凭证
                chain.add(buildNextDownstreamForInvoice(traceNo, false));
            }
            case "BUSINESS_DOC" -> {
                // 业务单据 → 凭证
                chain.add(buildVoucherForDoc(traceNo));
            }
            case "RECEIVABLE" -> {
                // 应收单 → 核销单 → 凭证
                chain.add(buildSettlementAndVoucher(traceNo, true));
            }
            case "PAYABLE" -> {
                // 应付单 → 核销单 → 凭证
                chain.add(buildSettlementAndVoucher(traceNo, false));
            }
            case "SETTLEMENT" -> {
                // 核销单 → 凭证
                chain.add(buildVoucherForSettlement(traceNo));
            }
            case "VOUCHER" -> {
                // 已是凭证，无下游
            }
        }

        // 过滤 null
        chain.removeIf(n -> n == null);
        return chain;
    }

    private NumberingTraceVO.TraceNode buildNextDownstreamForInvoice(String invoiceNo, boolean isSales) {
        // P34: 发票 → 业务单据（应收/应付已合并到业务单据）
        String docType = isSales ? "INVOICE_OUT" : "INVOICE_IN";
        LambdaQueryWrapper<BusinessDocEntity> q = new LambdaQueryWrapper<>();
        q.eq(BusinessDocEntity::getInvoiceNo, invoiceNo)
          .eq(BusinessDocEntity::getDocType, docType);
        BusinessDocEntity doc = businessDocMapper.selectOne(q);
        if (doc != null) {
            return buildDocNode(doc);
        }
        return null;
    }

    private NumberingTraceVO.TraceNode buildVoucherForDoc(String docNo) {
        LambdaQueryWrapper<BusinessDocEntity> q = new LambdaQueryWrapper<>();
        q.eq(BusinessDocEntity::getDocNo, docNo);
        BusinessDocEntity doc = businessDocMapper.selectOne(q);
        if (doc != null && doc.getVoucherNo() != null) {
            LambdaQueryWrapper<VoucherEntity> vq = new LambdaQueryWrapper<>();
            vq.eq(VoucherEntity::getVoucherNo, doc.getVoucherNo());
            VoucherEntity v = voucherMapper.selectOne(vq);
            if (v != null) return buildVoucherNode(v);
        }
        return null;
    }

    private NumberingTraceVO.TraceNode buildSettlementAndVoucher(String docNo, boolean isReceivable) {
        // P34: 业务单据 → 核销明细 → 核销单 → 凭证
        String docType = isReceivable ? "INVOICE_OUT" : "INVOICE_IN";
        LambdaQueryWrapper<BusinessDocEntity> dq = new LambdaQueryWrapper<>();
        dq.eq(BusinessDocEntity::getDocNo, docNo)
          .eq(BusinessDocEntity::getDocType, docType);
        BusinessDocEntity doc = businessDocMapper.selectOne(dq);
        if (doc != null) {
            LambdaQueryWrapper<ArapSettlementEntryEntity> eq = new LambdaQueryWrapper<>();
            eq.eq(ArapSettlementEntryEntity::getBusinessDocId, doc.getId());
            ArapSettlementEntryEntity entry = settlementEntryMapper.selectOne(eq);
            if (entry != null) {
                LambdaQueryWrapper<ArapSettlementEntity> sq = new LambdaQueryWrapper<>();
                sq.eq(ArapSettlementEntity::getId, entry.getSettlementId());
                ArapSettlementEntity settlement = arapSettlementMapper.selectOne(sq);
                if (settlement != null && settlement.getVoucherNo() != null) {
                    LambdaQueryWrapper<VoucherEntity> vq = new LambdaQueryWrapper<>();
                    vq.eq(VoucherEntity::getVoucherNo, settlement.getVoucherNo());
                    VoucherEntity v = voucherMapper.selectOne(vq);
                    if (v != null) return buildVoucherNode(v);
                }
            }
        }
        return null;
    }

    private NumberingTraceVO.TraceNode buildVoucherForSettlement(String settlementNo) {
        LambdaQueryWrapper<ArapSettlementEntity> q = new LambdaQueryWrapper<>();
        q.eq(ArapSettlementEntity::getSettlementNo, settlementNo);
        ArapSettlementEntity settlement = arapSettlementMapper.selectOne(q);
        if (settlement != null && settlement.getVoucherNo() != null) {
            LambdaQueryWrapper<VoucherEntity> vq = new LambdaQueryWrapper<>();
            vq.eq(VoucherEntity::getVoucherNo, settlement.getVoucherNo());
            VoucherEntity v = voucherMapper.selectOne(vq);
            if (v != null) return buildVoucherNode(v);
        }
        return null;
    }

    // ==================== 上游链路（向前追溯） ====================

    private List<NumberingTraceVO.TraceNode> buildUpstreamChain(NumberingTraceVO.TraceNode currentNode, String traceNo) {
        List<NumberingTraceVO.TraceNode> chain = new ArrayList<>();

        String nodeType = currentNode.getNodeType();

        switch (nodeType) {
            case "VOUCHER" -> {
                // 凭证 → 核销单 / 业务单据 / 发票
                chain.add(buildNextUpstreamForVoucher(traceNo));
            }
            case "RECEIVABLE" -> {
                // 应收单 → 销售发票（通过 invoiceNo）
                chain.add(buildInvoiceUpstream(traceNo, true));
            }
            case "PAYABLE" -> {
                // 应付单 → 采购发票（通过 invoiceNo）
                chain.add(buildInvoiceUpstream(traceNo, false));
            }
            case "SETTLEMENT" -> {
                // 核销单 → 应收单/应付单
                chain.add(buildSettlementUpstream(traceNo));
            }
            case "BUSINESS_DOC" -> {
                // 业务单据 → 发票
                chain.add(buildDocUpstream(traceNo));
            }
            case "OUTPUT_INVOICE", "INPUT_INVOICE" -> {
                // 发票是最上游，无上游
            }
        }

        chain.removeIf(n -> n == null);
        return chain;
    }

    private NumberingTraceVO.TraceNode buildNextUpstreamForVoucher(String voucherNo) {
        // 查凭证的 sourceDocType
        LambdaQueryWrapper<VoucherEntity> q = new LambdaQueryWrapper<>();
        q.eq(VoucherEntity::getVoucherNo, voucherNo);
        VoucherEntity v = voucherMapper.selectOne(q);
        if (v == null || v.getSourceDocNo() == null) return null;

        String srcType = v.getSourceDocType();
        String srcNo = v.getSourceDocNo();

        return switch (srcType) {
            case "BUSINESS_DOC" -> buildDocNode(findDocByNo(srcNo));
            case "SETTLEMENT" -> buildSettlementNode(findSettlementByNo(srcNo));
            case "OUTPUT_INVOICE" -> buildOutputInvoiceNode(findOutputInvByNo(srcNo));  // P33: 改为销售发票
            case "INPUT_INVOICE" -> buildInputInvoiceNode(findInputInvByNo(srcNo));
            case "TAX_DECLARATION" -> null; // 税务申报无上游
            default -> null;
        };
    }

    private NumberingTraceVO.TraceNode buildInvoiceUpstream(String docNo, boolean isReceivable) {
        // P34: 业务单据 → 发票（应收/应付已合并到业务单据）
        String docType = isReceivable ? "INVOICE_OUT" : "INVOICE_IN";
        LambdaQueryWrapper<BusinessDocEntity> q = new LambdaQueryWrapper<>();
        q.eq(BusinessDocEntity::getDocNo, docNo)
          .eq(BusinessDocEntity::getDocType, docType);
        BusinessDocEntity doc = businessDocMapper.selectOne(q);
        if (doc != null && doc.getInvoiceNo() != null) {
            if (isReceivable) {
                LambdaQueryWrapper<OutputInvoiceEntity> iq = new LambdaQueryWrapper<>();
                iq.eq(OutputInvoiceEntity::getInvoiceNo, doc.getInvoiceNo());
                OutputInvoiceEntity inv = outputInvoiceMapper.selectOne(iq);
                if (inv != null) return buildOutputInvoiceNode(inv);
            } else {
                LambdaQueryWrapper<InputInvoiceEntity> iq = new LambdaQueryWrapper<>();
                iq.eq(InputInvoiceEntity::getInvoiceNo, doc.getInvoiceNo());
                InputInvoiceEntity inv = inputInvoiceMapper.selectOne(iq);
                if (inv != null) return buildInputInvoiceNode(inv);
            }
        }
        return null;
    }
    private NumberingTraceVO.TraceNode buildSettlementUpstream(String settlementNo) {
        // P34: 核销单 → 核销明细 → 业务单据（应收/应付已合并到业务单据）
        LambdaQueryWrapper<ArapSettlementEntity> q = new LambdaQueryWrapper<>();
        q.eq(ArapSettlementEntity::getSettlementNo, settlementNo);
        ArapSettlementEntity s = arapSettlementMapper.selectOne(q);
        if (s != null) {
            LambdaQueryWrapper<ArapSettlementEntryEntity> eq = new LambdaQueryWrapper<>();
            eq.eq(ArapSettlementEntryEntity::getSettlementId, s.getId());
            ArapSettlementEntryEntity entry = settlementEntryMapper.selectOne(eq);
            if (entry != null && entry.getBusinessDocId() != null) {
                LambdaQueryWrapper<BusinessDocEntity> dq = new LambdaQueryWrapper<>();
                dq.eq(BusinessDocEntity::getId, entry.getBusinessDocId());
                BusinessDocEntity doc = businessDocMapper.selectOne(dq);
                if (doc != null) return buildDocNode(doc);
            }
        }
        return null;
    }

    private NumberingTraceVO.TraceNode buildDocUpstream(String docNo) {
        // 业务单据 → 发票（通过 docNo 关联）
        LambdaQueryWrapper<InputInvoiceEntity> iq = new LambdaQueryWrapper<>();
        iq.eq(InputInvoiceEntity::getDocNo, docNo);
        InputInvoiceEntity inv = inputInvoiceMapper.selectOne(iq);
        if (inv != null) return buildInputInvoiceNode(inv);
        return null;
    }

    // ==================== 辅助方法 ====================

    private BusinessDocEntity findDocByNo(String docNo) {
        LambdaQueryWrapper<BusinessDocEntity> q = new LambdaQueryWrapper<>();
        q.eq(BusinessDocEntity::getDocNo, docNo);
        return businessDocMapper.selectOne(q);
    }

    private ArapSettlementEntity findSettlementByNo(String docNo) {
        LambdaQueryWrapper<ArapSettlementEntity> q = new LambdaQueryWrapper<>();
        q.eq(ArapSettlementEntity::getSettlementNo, docNo);
        return arapSettlementMapper.selectOne(q);
    }

    private InputInvoiceEntity findInputInvByNo(String invoiceNo) {
        LambdaQueryWrapper<InputInvoiceEntity> q = new LambdaQueryWrapper<>();
        q.eq(InputInvoiceEntity::getInvoiceNo, invoiceNo);
        return inputInvoiceMapper.selectOne(q);
    }

    private OutputInvoiceEntity findOutputInvByNo(String invoiceNo) {
        LambdaQueryWrapper<OutputInvoiceEntity> q = new LambdaQueryWrapper<>();
        q.eq(OutputInvoiceEntity::getInvoiceNo, invoiceNo);
        return outputInvoiceMapper.selectOne(q);
    }
}
