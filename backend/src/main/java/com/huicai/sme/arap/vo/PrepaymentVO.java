package com.huicai.sme.arap.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.arap.entity.PrepaymentEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * 预付款/预收款出参 VO（P102 批次 6）。
 *
 * <p><b>为何要有这个 VO</b>：不是「铁律 #13 的形式合规」，而是它修掉了一个<b>真实的前端缺陷</b>。
 * {@code PrepaymentList.vue} 一直读 4 个字段，而 {@code t_prepayment} <b>一个都没有</b>：
 * <pre>
 *   前端字段         真实来源                                        修复前
 *   prepayNo       → doc_id → t_business_doc.doc_no   （跨表，非本表列）  恒空白
 *   vendorName     → vendor_id → t_vendor.name        （跨表，非本表列）  恒空白
 *   customerName   → customer_id → t_customer.name    （跨表，非本表列）  恒空白
 *   appliedAmount  → settled_amount                   （语义同，名字不同）恒 0
 * </pre>
 * ⇒ 该页面的「单据编号 / 供应商 / 客户 / 已核销金额」四列<b>一直是空的</b>。
 *
 * <p><b>三个跨表字段由 Service 批量补全</b>（每页 3 条 SQL，非 N+1），
 * 过滤条件仍走 {@code PrepaymentService#pageQuery} 的 LambdaQueryWrapper ——
 * <b>不把过滤条件复制进自定义 SQL</b>，避免两套事实来源各自漂移。
 *
 * <p>⚠️ <b>prepayNo 有一个诚实的限制</b>：它来自 {@code doc_id → t_business_doc.doc_no}，
 * 而 {@code doc_id} 只有「银行流水自动生成预付款」那条路径会写（AutoGenerationService:775,815）；
 * <b>手工建单（PrepaymentCreateDTO）刻意不含 docId</b>（那是流程后写字段）⇒
 * 手工建的预付款这一列<b>仍然为 null</b>。这是数据来源的客观事实，不靠前端兜底字符串掩盖。
 * 真要「每条预付款都有编号」，需按铁律 #9 给 t_prepayment 补 xxx_no 冗余列（需 Flyway 迁移），已登记待办。
 */
@Data
public class PrepaymentVO {

    private Long id;

    /** 单据编号：来自 {@code doc_id → t_business_doc.doc_no}；手工建单时为 null（见类注释） */
    private String prepayNo;

    private Long vendorId;
    private String vendorName;

    private Long customerId;
    private String customerName;

    /** 预付款/预收款金额 */
    private BigDecimal amount;

    /** 已核销金额（前端原叫 appliedAmount，本名是真实列名 settled_amount） */
    private BigDecimal settledAmount;

    /** 未核销金额 */
    private BigDecimal unsettledAmount;

    private String period;
    private LocalDate txDate;
    private String summary;
    private String status;
    private String sourceDocType;
    private Long sourceDocId;

    public static PrepaymentVO from(PrepaymentEntity e, String prepayNo,
                                    String vendorName, String customerName) {
        if (e == null) {
            return null;
        }
        PrepaymentVO vo = new PrepaymentVO();
        vo.setId(e.getId());
        vo.setPrepayNo(prepayNo);
        vo.setVendorId(e.getVendorId());
        vo.setVendorName(vendorName);
        vo.setCustomerId(e.getCustomerId());
        vo.setCustomerName(customerName);
        vo.setAmount(e.getAmount());
        vo.setSettledAmount(e.getSettledAmount());
        vo.setUnsettledAmount(e.getUnsettledAmount());
        vo.setPeriod(e.getPeriod());
        vo.setTxDate(e.getTxDate());
        vo.setSummary(e.getSummary());
        vo.setStatus(e.getStatus());
        vo.setSourceDocType(e.getSourceDocType());
        vo.setSourceDocId(e.getSourceDocId());
        return vo;
    }

    /** 单条补全 */
    public static PrepaymentVO from(PrepaymentEntity e, Names names) {
        if (e == null) {
            return null;
        }
        return from(e,
                names.docNos().get(e.getDocId()),
                names.vendorNames().get(e.getVendorId()),
                names.customerNames().get(e.getCustomerId()));
    }

    /** 批量补全：一次查齐三张表的名字，避免逐行查（AGENTS §4.3 N+1） */
    public static List<PrepaymentVO> from(List<PrepaymentEntity> list, Names names) {
        if (list == null) {
            return null;
        }
        return list.stream().map(e -> from(e, names)).collect(Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<PrepaymentVO> from(IPage<PrepaymentEntity> page, Names names) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PrepaymentVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords(), names));
        return out;
    }

    /**
     * 三张关联表的 id → 展示名，一次查齐。
     * key 为 null 时 {@link #from} 取不到值即输出 null，<b>不做兜底字符串</b>。
     */
    public record Names(
            Map<Long, String> docNos,
            Map<Long, String> vendorNames,
            Map<Long, String> customerNames) {
    }
}