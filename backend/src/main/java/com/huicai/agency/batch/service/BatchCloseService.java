package com.huicai.agency.batch.service;

import com.huicai.agency.batch.dto.BatchResultVO;

import java.util.List;

public interface BatchCloseService {
    BatchResultVO closePeriods(List<Long> enterpriseIds, String period);
}
