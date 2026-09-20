package com.huicai.base.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.system.entity.Subject;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 科目 Mapper
 */
public interface SubjectMapper extends BaseMapper<Subject> {

    /**
     * 统计所有科目记录数（忽略逻辑删除过滤），用于检测是否存在编码冲突
     */
    @Select("SELECT COUNT(*) FROM t_subject")
    Long selectCountPhysical();

    /**
     * 获取现金/银行类科目编码集合（用于凭证类别限制校验）
     * 包括库存现金(1001)、银行存款(1002)、其他货币资金(1012)等
     */
    @Select("SELECT DISTINCT code FROM t_subject WHERE deleted = 0 AND (code LIKE '1001%' OR code LIKE '1002%' OR code LIKE '1012%' OR code LIKE '1015%')")
    List<String> getCashBankCodes();
}