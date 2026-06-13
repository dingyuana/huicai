package com.huicai.module.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.finance.entity.VoucherTemplateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VoucherTemplateMapper extends BaseMapper<VoucherTemplateEntity> {

    /**
     * 根据分类查找激活的模板 (每个分类最多 1 个激活模板).
     */
    VoucherTemplateEntity selectActiveByClassification(@Param("classification") String classification);

    /**
     * 查询所有有效模板.
     */
    List<VoucherTemplateEntity> selectAllActive();
}
