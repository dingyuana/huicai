package com.huicai.module.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.finance.entity.VoucherTemplateLineEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VoucherTemplateLineMapper extends BaseMapper<VoucherTemplateLineEntity> {

    /**
     * 根据模板 ID 查询所有分录行 (按 line_order 排序).
     */
    List<VoucherTemplateLineEntity> selectByTemplateId(@Param("templateId") Long templateId);

    /**
     * 删除模板的所有分录行.
     */
    int deleteByTemplateId(@Param("templateId") Long templateId);
}
