package com.huicai.agency.client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.agency.client.entity.ContractEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ContractMapper extends BaseMapper<ContractEntity> {

    @Select("SELECT * FROM t_contract WHERE end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + 30 " +
            "AND status = 'ACTIVE' AND renewal_notice_sent = FALSE AND deleted = 0 " +
            "ORDER BY end_date ASC")
    List<ContractEntity> findRenewalReminders();
}
