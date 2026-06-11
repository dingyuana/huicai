package com.huicai.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.system.entity.RoleMenuEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface RoleMenuMapper extends BaseMapper<RoleMenuEntity> {

    @Select("SELECT menu_id FROM t_role_menu WHERE role_id = #{roleId}")
    List<Long> getMenuIdsByRoleId(@Param("roleId") Long roleId);
}
