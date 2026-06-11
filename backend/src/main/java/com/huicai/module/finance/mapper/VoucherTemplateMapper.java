package com.huicai.module.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.finance.entity.VoucherTemplateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VoucherTemplateMapper extends BaseMapper<VoucherTemplateEntity> {
    VoucherTemplateEntity selectByDocType(@Param("docType") String docType);
    List<VoucherTemplateEntity> selectAllActive();
}
