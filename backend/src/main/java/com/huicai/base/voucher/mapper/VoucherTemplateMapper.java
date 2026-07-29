package com.huicai.base.voucher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    /**
     * 按 businessType 查激活模板.
     * DB 列 doc_type 对应 entity 的 businessType, source/direction/match_priority 在 DB 中不存在.
     * TemplateMatcher 有多级降级策略（source+businessType → classification），此处仅按 doc_type 匹配.
     */
    @Select("""
        SELECT * FROM t_voucher_template
        WHERE is_active = true
          AND doc_type = #{businessType}
        ORDER BY id ASC
        LIMIT 1
    """)
    VoucherTemplateEntity matchByDimensions(@Param("source") String source,
                                             @Param("businessType") String businessType,
                                             @Param("direction") String direction);
}
