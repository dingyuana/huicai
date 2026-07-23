package com.huicai.agency.client.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.agency.client.dto.ContractCreateDTO;
import com.huicai.agency.client.dto.ContractVO;
import com.huicai.agency.client.dto.RenewalReminderVO;

import java.util.List;

public interface ContractService {
    ContractVO create(ContractCreateDTO dto);
    ContractVO getById(Long id);
    IPage<ContractVO> page(int page, int size);
    List<RenewalReminderVO> getRenewalReminders();
    ContractVO renew(Long id);
}
