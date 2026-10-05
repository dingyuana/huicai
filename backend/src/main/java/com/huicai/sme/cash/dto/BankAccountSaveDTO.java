package com.huicai.sme.cash.dto;

import com.huicai.sme.cash.entity.BankAccountEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 银行账户新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p><b>保留 {@code balance}</b>：它虽是「余额」，但新增时<b>就是期初余额</b>
 * （业务输入，且 {@code BankAccountServiceImpl#create} 会给它默认 0）；
 * 而 {@code update} <b>刻意不拷贝 balance</b>（余额只能由流水累积）⇒
 * 保留字段 + 更新路径不写，两者不冲突。
 *
 * <p>剔除 {@code createdAt/updatedAt/deleted} 与幽灵字段
 * {@code createdBy}/{@code updatedBy}（Entity 上 {@code exist = false}）。
 */
@Data
public class BankAccountSaveDTO {

    @NotBlank(message = "银行账号不能为空")
    @Size(max = 64, message = "银行账号长度不能超过 64")
    private String accountNo;

    @NotBlank(message = "账户名称不能为空")
    @Size(max = 128, message = "账户名称长度不能超过 128")
    private String accountName;

    @Size(max = 128, message = "开户行过长")
    private String bankName;

    @Size(max = 8, message = "币种长度不能超过 8")
    private String currency;

    private Long subjectId;

    /** 期初余额（仅新增有意义；更新路径不写） */
    private BigDecimal balance;

    private Boolean isActive;

    @Size(max = 500, message = "备注过长")
    private String remark;

    public BankAccountEntity toEntity() {
        BankAccountEntity e = new BankAccountEntity();
        e.setAccountNo(accountNo);
        e.setAccountName(accountName);
        e.setBankName(bankName);
        e.setCurrency(currency);
        e.setSubjectId(subjectId);
        e.setBalance(balance);
        e.setIsActive(isActive);
        e.setRemark(remark);
        return e;
    }
}
