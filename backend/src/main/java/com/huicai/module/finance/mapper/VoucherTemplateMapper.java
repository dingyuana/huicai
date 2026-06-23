package com.huicai.module.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.finance.entity.VoucherTemplateEntity;
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
     * 按 source + businessType + direction 查激活模板（无视方向时传 null）.
     */
    @Select("""
        SELECT * FROM t_voucher_template
        WHERE is_active = true
          AND source = #{source}
          AND business_type = #{businessType}
          AND (#{direction} IS NULL OR direction IS NULL OR direction = '' OR direction = #{direction})
        ORDER BY match_priority ASC, id ASC
        LIMIT 1
    """)
    VoucherTemplateEntity matchByDimensions(@Param("source") String source,
                                             @Param("businessType") String businessType,
                                             @Param("direction") String direction);
}
