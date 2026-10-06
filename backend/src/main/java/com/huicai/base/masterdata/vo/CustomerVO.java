package com.huicai.base.masterdata.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.masterdata.entity.CustomerEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 客户出参 VO（P102 出参面 DTO 化 **批次 2/9**）
 *
 * <p><b>字段集与前端 {@code frontend/src/api/modules/arap.ts} 的
 * {@code interface Customer} 逐字段一致</b>（由 {@code MasterDataVoContractTest} 锁死）：
 * {@code id/code/name/contactPerson/phone/email/address/taxNo/bankName/bankAccount/
 * creditLimit/creditDays/subjectId/isActive/remark}。
 *
 * <p><b>刻意不外露</b>（逐个有依据）：{@code createdAt}/{@code updatedAt}（审计列，前端不渲染）、
 * {@code deleted}（逻辑删除位，属内部状态）、{@code enterpriseId}（租户内部维度，
 * 前端经 {@code X-Enterprise-Id} 切换，不该在响应里回显租户号）。
 *
 * <p><b>⚠️ 与 VO 化的关系</b>：VO 不只是「类型换名」。它让
 * ①出参不再随 Entity 加字段而自动扩张（Entity 加一个字段，前端就多收一个未知字段）；
 * ②服务端内部字段（审计/租户/逻辑删除）有了一个**显式拒绝外露**的位置。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 * @see AGENTS.md §4.5 第 30 条（新增 VO 的覆盖率税已由扫描式测试预付）
 */
@Data
public class CustomerVO {

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

    public static CustomerVO from(CustomerEntity e) {
        if (e == null) {
            return null;
        }
        CustomerVO vo = new CustomerVO();
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

    public static List<CustomerVO> from(List<CustomerEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(CustomerVO::from).collect(java.util.stream.Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<CustomerVO> from(IPage<CustomerEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<CustomerVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}