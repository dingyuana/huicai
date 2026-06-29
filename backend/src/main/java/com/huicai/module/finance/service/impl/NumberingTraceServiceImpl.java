package com.huicai.module.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import com.huicai.module.arap.entity.ArapSettlementEntryEntity;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.mapper.ArapSettlementEntryMapper;
import com.huicai.module.arap.mapper.ArapSettlementMapper;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.finance.dto.NumberingTraceVO;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.NumberingTraceService;
import com.huicai.module.finance.service.VoucherService;
import com.huicai.module.tax.entity.InputInvoiceEntity;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.InputInvoiceMapper;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
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
    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;
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

        // 2. 查业务单据
        LambdaQueryWrapper<BusinessDocEntity> docQ = new LambdaQueryWrapper<>();
        docQ.eq(BusinessDocEntity::getDocNo, traceNo);
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

        // 4. 查应收单（P33: 增加 receivableNo 匹配）
        LambdaQueryWrapper<com.huicai.module.arap.entity.ReceivableEntity> recQ = new LambdaQueryWrapper<>();
        recQ.eq(com.huicai.module.arap.entity.ReceivableEntity::getDocNo, traceNo)
            .or().eq(com.huicai.module.arap.entity.ReceivableEntity::getVoucherNo, traceNo)
            .or().eq(com.huicai.module.arap.entity.ReceivableEntity::getReceivableNo, traceNo);
        com.huicai.module.arap.entity.ReceivableEntity rec = receivableMapper.selectOne(recQ);
        if (rec != null) {
            return buildReceivableNode(rec);
        }

        // 5. 查应付单（P33: 增加 payableNo 匹配）
        LambdaQueryWrapper<PayableEntity> payQ = new LambdaQueryWrapper<>();
        payQ.eq(PayableEntity::getDocNo, traceNo)
            .or().eq(PayableEntity::getVoucherNo, traceNo)
            .or().eq(PayableEntity::getPayableNo, traceNo);
        PayableEntity pay = payableMapper.selectOne(payQ);
        if (pay != null) {
            return buildPayableNode(pay);
        }

        // 6. 查核销单
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

    private NumberingTraceVO.TraceNode buildReceivableNode(com.huicai.module.arap.entity.ReceivableEntity r) {
        NumberingTraceVO.TraceNode node = new NumberingTraceVO.TraceNode();
        node.setNodeType("RECEIVABLE");
        node.setNodeNo(r.getReceivableNo() != null ? r.getReceivableNo() : r.getDocNo());
        node.setSummary(r.getSummary());
        node.setAmount(r.getAmount());
        node.setStatus(r.getStatus());
        node.setVoucherNo(r.getVoucherNo());
        node.setInvoiceNo(r.getInvoiceNo());  // P33: 填充发票编号
        node.setCreatedAt(r.getCreatedAt());
        return node;
    }

    private NumberingTraceVO.TraceNode buildPayableNode(PayableEntity p) {
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
        if (isSales) {
            // 销售发票 → 应收单（P33: 通过 invoiceNo 或 invoiceId 查找）
            LambdaQueryWrapper<com.huicai.module.arap.entity.ReceivableEntity> q = new LambdaQueryWrapper<>();
            q.eq(com.huicai.module.arap.entity.ReceivableEntity::getInvoiceNo, invoiceNo);
            com.huicai.module.arap.entity.ReceivableEntity rec = receivableMapper.selectOne(q);
            if (rec != null) {
                return buildReceivableNode(rec);
            }
        } else {
            // 采购发票 → 应付单（通过 invoiceNo 字段）
            LambdaQueryWrapper<PayableEntity> q = new LambdaQueryWrapper<>();
            q.eq(PayableEntity::getInvoiceNo, invoiceNo);
            PayableEntity pay = payableMapper.selectOne(q);
            if (pay != null) {
                return buildPayableNode(pay);
            }
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
        // 应收/应付单 → 核销明细 → 核销单 → 凭证
        if (isReceivable) {
            LambdaQueryWrapper<com.huicai.module.arap.entity.ReceivableEntity> rq = new LambdaQueryWrapper<>();
            rq.eq(com.huicai.module.arap.entity.ReceivableEntity::getDocNo, docNo);
            com.huicai.module.arap.entity.ReceivableEntity rec = receivableMapper.selectOne(rq);
            if (rec != null) {
                // 通过 receivableId 找核销明细
                LambdaQueryWrapper<ArapSettlementEntryEntity> eq = new LambdaQueryWrapper<>();
                eq.eq(ArapSettlementEntryEntity::getReceivableId, rec.getId());
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
        } else {
            LambdaQueryWrapper<PayableEntity> pq = new LambdaQueryWrapper<>();
            pq.eq(PayableEntity::getDocNo, docNo);
            PayableEntity pay = payableMapper.selectOne(pq);
            if (pay != null) {
                LambdaQueryWrapper<ArapSettlementEntryEntity> eq = new LambdaQueryWrapper<>();
                eq.eq(ArapSettlementEntryEntity::getPayableId, pay.getId());
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
        if (isReceivable) {
            // P33: 应收单 → 销售发票（通过 receivableNo 查找应收单，再通过 invoiceNo 查找销售发票）
            LambdaQueryWrapper<com.huicai.module.arap.entity.ReceivableEntity> q = new LambdaQueryWrapper<>();
            q.eq(com.huicai.module.arap.entity.ReceivableEntity::getReceivableNo, docNo)
                .or().eq(com.huicai.module.arap.entity.ReceivableEntity::getDocNo, docNo);
            com.huicai.module.arap.entity.ReceivableEntity rec = receivableMapper.selectOne(q);
            if (rec != null && rec.getInvoiceNo() != null) {
                // 通过 invoiceNo 查找销售发票
                LambdaQueryWrapper<OutputInvoiceEntity> iq = new LambdaQueryWrapper<>();
                iq.eq(OutputInvoiceEntity::getInvoiceNo, rec.getInvoiceNo());
                OutputInvoiceEntity inv = outputInvoiceMapper.selectOne(iq);
                if (inv != null) return buildOutputInvoiceNode(inv);
            }
        } else {
            LambdaQueryWrapper<PayableEntity> q = new LambdaQueryWrapper<>();
            q.eq(PayableEntity::getDocNo, docNo);
            PayableEntity pay = payableMapper.selectOne(q);
            if (pay != null && pay.getInvoiceNo() != null) {
                LambdaQueryWrapper<InputInvoiceEntity> iq = new LambdaQueryWrapper<>();
                iq.eq(InputInvoiceEntity::getInvoiceNo, pay.getInvoiceNo());
                InputInvoiceEntity inv = inputInvoiceMapper.selectOne(iq);
                if (inv != null) return buildInputInvoiceNode(inv);
            }
        }
        return null;
    }
    private NumberingTraceVO.TraceNode buildSettlementUpstream(String settlementNo) {
        // 核销单 → 核销明细 → 应收单/应付单
        LambdaQueryWrapper<ArapSettlementEntity> q = new LambdaQueryWrapper<>();
        q.eq(ArapSettlementEntity::getSettlementNo, settlementNo);
        ArapSettlementEntity s = arapSettlementMapper.selectOne(q);
        if (s != null) {
            // 通过 settlementId 找核销明细
            LambdaQueryWrapper<ArapSettlementEntryEntity> eq = new LambdaQueryWrapper<>();
            eq.eq(ArapSettlementEntryEntity::getSettlementId, s.getId());
            ArapSettlementEntryEntity entry = settlementEntryMapper.selectOne(eq);
            if (entry != null) {
                if (entry.getReceivableId() != null) {
                    LambdaQueryWrapper<com.huicai.module.arap.entity.ReceivableEntity> rq = new LambdaQueryWrapper<>();
                    rq.eq(com.huicai.module.arap.entity.ReceivableEntity::getId, entry.getReceivableId());
                    com.huicai.module.arap.entity.ReceivableEntity rec = receivableMapper.selectOne(rq);
                    if (rec != null) return buildReceivableNode(rec);
                }
                if (entry.getPayableId() != null) {
                    LambdaQueryWrapper<PayableEntity> pq = new LambdaQueryWrapper<>();
                    pq.eq(PayableEntity::getId, entry.getPayableId());
                    PayableEntity pay = payableMapper.selectOne(pq);
                    if (pay != null) return buildPayableNode(pay);
                }
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
