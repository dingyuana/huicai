package com.huicai.agency.client.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_contract")
public class ContractEntity extends BaseEntity {
    private Long enterpriseId;
    private Long agencyId;
    private String contractNo;
    private LocalDate startDate;
    private LocalDate endDate;
    private String contractType;
    private BigDecimal amount;
    private String status;
    private Boolean renewalNoticeSent;
}
