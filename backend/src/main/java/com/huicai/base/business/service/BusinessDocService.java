package com.huicai.base.business.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.business.dto.BusinessDocDTO;
import com.huicai.base.business.dto.BusinessDocQueryDTO;
import com.huicai.base.business.dto.BusinessDocVO;

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
