package com.huicai.sme.cash.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.cash.entity.TicketTransactionEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * TicketTransactionVO —— 由 t_ticket_transaction 的真实列生成（P102 批次 7）。
 *
 * <p>字段集 = 前端 ticket.ts#TicketTransaction 已声明的 10 个字段，逐个核对：
 * ① DB 有该列；② Entity 有该字段 —— 缺一即停，不会漏前端字段也不会凭空暴露。
 *
 * <p>⚠️ 前端此前全是 <code>Promise&lt;any&gt;</code>（无任何契约），本轮补类型后由
 * MasterDataVoContractTest 逐字段锁死 VO↔interface。
 */
@Data
public class TicketTransactionVO {

    private Long id;
    private Long ticketId;
    private String transType;
    private LocalDate transDate;
    private String recipient;
    private BigDecimal amount;
    private String remark;
    private Long operatorId;
    private Long voucherId;
    private LocalDateTime createdAt;

    public static TicketTransactionVO from(TicketTransactionEntity e) {
        if (e == null) {
            return null;
        }
        TicketTransactionVO vo = new TicketTransactionVO();
        vo.setId(e.getId());
        if (e.getId() != null) vo.setId(e.getId());
        if (e.getTicketId() != null) vo.setTicketId(e.getTicketId());
        if (e.getTransType() != null) vo.setTransType(e.getTransType());
        if (e.getTransDate() != null) vo.setTransDate(e.getTransDate());
        if (e.getRecipient() != null) vo.setRecipient(e.getRecipient());
        if (e.getAmount() != null) vo.setAmount(e.getAmount());
        if (e.getRemark() != null) vo.setRemark(e.getRemark());
        if (e.getOperatorId() != null) vo.setOperatorId(e.getOperatorId());
        if (e.getVoucherId() != null) vo.setVoucherId(e.getVoucherId());
        if (e.getCreatedAt() != null) vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<TicketTransactionVO> from(List<TicketTransactionEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(TicketTransactionVO::from).collect(Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<TicketTransactionVO> from(IPage<TicketTransactionEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TicketTransactionVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}
