package com.huicai.base.masterdata.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.masterdata.entity.VendorEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商出参 VO（P102 出参面 DTO 化 **批次 2/9**）
 *
 * <p><b>字段集与前端 {@code frontend/src/api/modules/arap.ts} 的
 * {@code interface Vendor} 逐字段一致</b>（由 {@code MasterDataVoContractTest} 锁死）：
 * {@code id/code/name/contactPerson/phone/email/address/taxNo/bankName/bankAccount/
 * creditLimit/creditDays/subjectId/isActive/remark}。
 *
 * <p>⚠️ 本 VO 与 {@link CustomerVO} 字段集相同但<b>刻意不合并为一个类</b>：
 * 客户与供应商是两种业务对象，将来任一方加字段（如客户的「客户分级」、供应商的
 * 「供应商类别」）不应污染另一方。共享基类会诱使后来者图省事把两者字段并在一起。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class VendorVO {

    private Long id;
    private String code;
    private String name;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String taxNo;
    private String bankName;
    private String bankAccount;
    private BigDecimal creditLimit;
    private Integer creditDays;
    private Long subjectId;
    private Boolean isActive;
    private String remark;

    public static VendorVO from(VendorEntity e) {
        if (e == null) {
            return null;
        }
        VendorVO vo = new VendorVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setContactPerson(e.getContactPerson());
        vo.setPhone(e.getPhone());
        vo.setEmail(e.getEmail());
        vo.setAddress(e.getAddress());
        vo.setTaxNo(e.getTaxNo());
        vo.setBankName(e.getBankName());
        vo.setBankAccount(e.getBankAccount());
        vo.setCreditLimit(e.getCreditLimit());
        vo.setCreditDays(e.getCreditDays());
        vo.setSubjectId(e.getSubjectId());
        vo.setIsActive(e.getIsActive());
        vo.setRemark(e.getRemark());
        return vo;
    }

    public static List<VendorVO> from(List<VendorEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(VendorVO::from).collect(java.util.stream.Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<VendorVO> from(IPage<VendorEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VendorVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}