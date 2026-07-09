package com.huicai.module.arap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.module.arap.dto.vo.ArapSettlementVO;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArapSettlementMapper extends BaseMapper<ArapSettlementEntity> {

    @Delete("DELETE FROM t_arap_settlement")
    int physicalDeleteAll();

    /**
     * 分页查询核销单（含客户/供应商名称）.
     */
    @Select("""
            SELECT s.*, c.name AS customer_name, v.name AS vendor_name
            FROM t_arap_settlement s
            LEFT JOIN t_customer c ON s.party_id = c.id AND s.party_type = 'CUSTOMER'
            LEFT JOIN t_supplier v ON s.party_id = v.id AND s.party_type = 'VENDOR'
            WHERE s.deleted = 0
            ORDER BY s.created_at DESC
            """)
    IPage<ArapSettlementVO> pageWithPartyName(Page<?> page);
}