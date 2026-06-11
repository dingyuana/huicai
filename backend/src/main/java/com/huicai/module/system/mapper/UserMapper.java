package com.huicai.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.system.entity.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT * FROM t_user WHERE username = #{username} AND deleted = 0")
    UserEntity selectByUsername(@Param("username") String username);
}
