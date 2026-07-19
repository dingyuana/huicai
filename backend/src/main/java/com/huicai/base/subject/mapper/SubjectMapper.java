package com.huicai.base.subject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.subject.entity.Subject;
import org.apache.ibatis.annotations.Select;

/**
 * 科目 Mapper
 */
public interface SubjectMapper extends BaseMapper<Subject> {

    /**
     * 统计所有科目记录数（忽略逻辑删除过滤），用于检测是否存在编码冲突
     */
    @Select("SELECT COUNT(*) FROM t_subject")
    Long selectCountPhysical();
}