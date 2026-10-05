package com.huicai.base.masterdata.dto;

import com.huicai.base.masterdata.entity.VendorEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 供应商新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p>与 {@code CustomerSaveDTO} 对称（表结构同形）。剔除
 * {@code createdAt/updatedAt/deleted}；{@code code}/{@code name} NOT NULL ⇒ 必填。
 */
@Data
public class VendorSaveDTO {

    @NotBlank(message = "供应商编码不能为空")
    @Size(max = 64, message = "供应商编码长度不能超过 64")
    private String code;

    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 128, message = "供应商名称长度不能超过 128")
    private String name;

    @Size(max = 64, message = "联系人长度不能超过 64")
    private String contactPerson;

    @Size(max = 32, message = "电话长度不能超过 32")
    private String phone;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(max = 255, message = "地址过长")
    private String address;

    @Size(max = 64, message = "税号长度不能超过 64")
    private String taxNo;

    @Size(max = 128, message = "开户行过长")
    private String bankName;

    @Size(max = 64, message = "银行账号长度不能超过 64")
    private String bankAccount;

    private BigDecimal creditLimit;

    private Integer creditDays;

    private Long subjectId;

    private Boolean isActive;

    @Size(max = 500, message = "备注过长")
    private String remark;

    public VendorEntity toEntity() {
        VendorEntity e = new VendorEntity();
        e.setCode(code);
        e.setName(name);
        e.setContactPerson(contactPerson);
        e.setPhone(phone);
        e.setEmail(email);
        e.setAddress(address);
        e.setTaxNo(taxNo);
        e.setBankName(bankName);
        e.setBankAccount(bankAccount);
        e.setCreditLimit(creditLimit);
        e.setCreditDays(creditDays);
        e.setSubjectId(subjectId);
        e.setIsActive(isActive);
        e.setRemark(remark);
        return e;
    }
}
