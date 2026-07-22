package com.huicai.base.system.service;

import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.model.dto.ImportResult;
import com.huicai.base.system.model.dto.SubjectCreateDTO;
import com.huicai.base.system.model.dto.SubjectUpdateDTO;
import com.huicai.base.system.model.vo.SubjectTreeVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
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

    /**
     * 从 Excel 批量导入科目
     *
     * @param file 上传的 Excel 文件 (.xlsx)
     * @return 导入结果
     */
    ImportResult importFromExcel(MultipartFile file);

    /**
     * 创建科目导入模板(.xlsx)
     *
     * @return 模板文件输入流
     */
    InputStream createExportTemplate();
}