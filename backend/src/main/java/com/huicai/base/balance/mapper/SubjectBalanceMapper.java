package com.huicai.base.balance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.balance.entity.SubjectBalanceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 科目余额 Mapper
 */
@Mapper
public interface SubjectBalanceMapper extends BaseMapper<SubjectBalanceEntity> {

    /**
     * 查询科目在某期间及之后期间的余额记录
     */
    List<SubjectBalanceEntity> selectFromPeriod(@Param("subjectId") Long subjectId,
                                                @Param("period") String period);

    /**
     * 批量更新余额
     */
    int batchUpsert(@Param("list") List<SubjectBalanceEntity> balances);
}
