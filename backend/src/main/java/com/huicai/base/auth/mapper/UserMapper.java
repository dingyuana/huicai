package com.huicai.base.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT * FROM t_user WHERE username = #{username} AND deleted = 0")
    UserEntity selectByUsername(@Param("username") String username);
}
