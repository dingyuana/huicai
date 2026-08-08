package com.huicai.agency.tenant.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentPeriodVO {
    private String currentPeriod;
    private String startPeriod;
    private String hasDataPeriod;
}
