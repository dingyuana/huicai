package com.huicai.sme.salary.service;

import com.huicai.sme.salary.dto.SalaryItemSaveDTO;
import com.huicai.sme.salary.vo.SalaryItemVO;

import java.util.List;

/** SPEC-P143（D-4）：薪资项目配置服务。 */
public interface SalaryItemService {

    List<SalaryItemVO> list();

    Long create(SalaryItemSaveDTO dto, Long userId);

    void update(Long id, SalaryItemSaveDTO dto, Long userId);

    void delete(Long id, Long userId);
}
