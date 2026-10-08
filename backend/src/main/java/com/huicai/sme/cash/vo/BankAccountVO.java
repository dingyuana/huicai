package com.huicai.sme.cash.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.cash.entity.BankAccountEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * BankAccountVO —— 由 t_bank_account 的真实列生成（批次 5）。
 *
 * <p>字段集 = 前端 bankAccount.ts#BankAccountVO 已声明的 10 个字段，逐个核对：
 * ① 前端要而 DB 无列 → 直接停（本批已确保无）；② 前端要而 Entity 无字段 → 直接停。
 * 故本 VO 既不会漏前端字段，也不会凭空暴露前端不用的列。
 *
 * <p>刻意不外露：（无）
 */
@Data
public class BankAccountVO {

    private Long id;
    private String accountNo;
    private String accountName;
    private String bankName;
    private String currency;
    private Long subjectId;
    private BigDecimal balance;
    private Boolean isActive;
    private String remark;
    private LocalDateTime createdAt;

    public static BankAccountVO from(BankAccountEntity e) {
        if (e == null) {
            return null;
        }
        BankAccountVO vo = new BankAccountVO();
        vo.setId(e.getId());
        if (e.getId() != null) vo.setId(e.getId());
        if (e.getAccountNo() != null) vo.setAccountNo(e.getAccountNo());
        if (e.getAccountName() != null) vo.setAccountName(e.getAccountName());
        if (e.getBankName() != null) vo.setBankName(e.getBankName());
        if (e.getCurrency() != null) vo.setCurrency(e.getCurrency());
        if (e.getSubjectId() != null) vo.setSubjectId(e.getSubjectId());
        if (e.getBalance() != null) vo.setBalance(e.getBalance());
        if (e.getIsActive() != null) vo.setIsActive(e.getIsActive());
        if (e.getRemark() != null) vo.setRemark(e.getRemark());
        if (e.getCreatedAt() != null) vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<BankAccountVO> from(List<BankAccountEntity> list) {
        return list == null ? null : list.stream().map(BankAccountVO::from).collect(Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<BankAccountVO> from(IPage<BankAccountEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<BankAccountVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}
