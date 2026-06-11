package com.huicai.module.finance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.finance.dto.BusinessDocDTO;
import com.huicai.module.finance.dto.BusinessDocQueryDTO;
import com.huicai.module.finance.dto.BusinessDocVO;

import java.util.List;

public interface BusinessDocService {
    IPage<BusinessDocVO> pageQuery(BusinessDocQueryDTO queryDTO);
    BusinessDocVO getDetail(Long id);
    BusinessDocVO create(BusinessDocDTO dto, Long userId);
    BusinessDocVO update(BusinessDocDTO dto, Long userId);
    void delete(Long id);
    void submit(Long id, Long userId);
    void approve(Long id, Long userId);
    void reject(Long id, Long userId);
    BusinessDocVO generateVoucher(Long id, Long userId);
    BusinessDocVO reverse(Long id, Long userId);
    String generateDocNo(String docType, String period);
}
