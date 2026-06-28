package com.huicai.module.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface BusinessDocMapper extends BaseMapper<BusinessDocEntity> {
    @Delete("DELETE FROM t_business_doc WHERE source = #{source}")
    int deleteBySource(@Param("source") String source);

    @Update("UPDATE t_business_doc SET voucher_id = NULL, status = 'DRAFT' WHERE voucher_id IS NOT NULL")
    int nullOutVoucherIds();

    @Delete("DELETE FROM t_business_doc")
    int physicalDeleteAll();

    @Select("""
        SELECT id, doc_no, status, customer_id, supplier_id
        FROM t_business_doc
        WHERE deleted = 0 AND status = 'VOUCHERED' AND voucher_id IS NULL
        LIMIT 100
    """)
    List<Map<String, Object>> findStatusVoucherIdMismatch();
}
