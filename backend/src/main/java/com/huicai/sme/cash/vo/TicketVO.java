package com.huicai.sme.cash.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.cash.entity.TicketEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * TicketVO —— 由 t_ticket 的真实列生成（P102 批次 7）。
 *
 * <p>字段集 = 前端 ticket.ts#Ticket 已声明的 12 个字段，逐个核对：
 * ① DB 有该列；② Entity 有该字段 —— 缺一即停，不会漏前端字段也不会凭空暴露。
 *
 * <p>⚠️ 前端此前全是 <code>Promise&lt;any&gt;</code>（无任何契约），本轮补类型后由
 * MasterDataVoContractTest 逐字段锁死 VO↔interface。
 */
@Data
public class TicketVO {

    private Long id;
    private String ticketNo;
    private String ticketType;
    private BigDecimal amount;
    private Long bankId;
    private String payee;
    private String drawer;
    private LocalDate issueDate;
    private LocalDate expireDate;
    private String status;
    private String remark;
    private LocalDateTime createdAt;

    public static TicketVO from(TicketEntity e) {
        if (e == null) {
            return null;
        }
        TicketVO vo = new TicketVO();
        vo.setId(e.getId());
        if (e.getId() != null) vo.setId(e.getId());
        if (e.getTicketNo() != null) vo.setTicketNo(e.getTicketNo());
        if (e.getTicketType() != null) vo.setTicketType(e.getTicketType());
        if (e.getAmount() != null) vo.setAmount(e.getAmount());
        if (e.getBankId() != null) vo.setBankId(e.getBankId());
        if (e.getPayee() != null) vo.setPayee(e.getPayee());
        if (e.getDrawer() != null) vo.setDrawer(e.getDrawer());
        if (e.getIssueDate() != null) vo.setIssueDate(e.getIssueDate());
        if (e.getExpireDate() != null) vo.setExpireDate(e.getExpireDate());
        if (e.getStatus() != null) vo.setStatus(e.getStatus());
        if (e.getRemark() != null) vo.setRemark(e.getRemark());
        if (e.getCreatedAt() != null) vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<TicketVO> from(List<TicketEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(TicketVO::from).collect(Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<TicketVO> from(IPage<TicketEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TicketVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}
