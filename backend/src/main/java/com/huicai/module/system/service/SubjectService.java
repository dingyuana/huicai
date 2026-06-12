package com.huicai.module.system.service;

import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.model.dto.SubjectCreateDTO;
import com.huicai.module.system.model.dto.SubjectUpdateDTO;
import com.huicai.module.system.model.vo.SubjectTreeVO;

import java.util.List;

/**
 * 科目 Service
 */
public interface SubjectService {

    /**
     * 获取科目树(全量)
     */
    List<SubjectTreeVO> getTree();

    /**
     * 新增科目
     */
    Subject create(SubjectCreateDTO dto);

    /**
     * 修改科目
     */
    Subject update(Long id, SubjectUpdateDTO dto);

    /**
     * 删除科目
     */
    void delete(Long id);

    /**
     * 获取科目详情
     */
    Subject getById(Long id);

    /**
     * 一键导入国家标准会计科目
     * @return 导入的科目数量
     */
    int importStandard();
}