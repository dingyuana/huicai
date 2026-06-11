package com.huicai.module.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.finance.entity.BankStatementEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BankStatementMapper extends BaseMapper<BankStatementEntity> {
    List<BankStatementEntity> selectByAccountAndStatus(@Param("accountId") Long accountId, @Param("status") String status);
    int updateMatch(@Param("id") Long id, @Param("journalId") Long journalId, @Param("status") String status);
}
