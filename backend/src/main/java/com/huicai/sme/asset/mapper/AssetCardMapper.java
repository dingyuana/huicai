package com.huicai.sme.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.sme.asset.entity.AssetCardEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface AssetCardMapper extends BaseMapper<AssetCardEntity> {

    @Select("""
        SELECT a.id, a.asset_code, a.asset_name, c.name AS category_name,
               a.original_value, a.accumulated_depreciation, a.net_value, a.status
        FROM t_asset_card a
        LEFT JOIN t_asset_category c ON c.id = a.category_id
        WHERE a.deleted = 0
        ORDER BY a.created_at DESC
        LIMIT #{limit}
    """)
    List<Map<String, Object>> selectRecent(@Param("limit") int limit);

    @Update("""
        UPDATE t_asset_card
        SET accumulated_depreciation = accumulated_depreciation + #{amount},
            net_value = original_value - (accumulated_depreciation + #{amount}),
            last_depreciation_period = #{period}
        WHERE id = #{assetId} AND deleted = 0
    """)
    int accumulateDepreciation(@Param("assetId") Long assetId,
                                @Param("amount") BigDecimal amount,
                                @Param("period") String period);

    @Select("""
        SELECT * FROM t_asset_card
        WHERE category_id = #{categoryId}
          AND deleted = 0
          AND status = 'IN_USE'
          AND (last_depreciation_period IS NULL OR last_depreciation_period < #{period})
    """)
    List<AssetCardEntity> selectToDepreciate(@Param("categoryId") Long categoryId,
                                              @Param("period") String period);
}
