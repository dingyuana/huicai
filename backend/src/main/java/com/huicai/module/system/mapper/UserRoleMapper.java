package com.huicai.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.system.entity.UserRoleEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {

    @Select("SELECT role_id FROM t_user_role WHERE user_id = #{userId}")
    List<Long> getRoleIdsByUserId(@Param("userId") Long userId);
}
