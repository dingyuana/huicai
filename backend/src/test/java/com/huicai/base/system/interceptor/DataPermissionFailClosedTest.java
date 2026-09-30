package com.huicai.base.system.interceptor;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.system.entity.RoleEntity;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.RoleMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.config.security.LoginUser;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * AT-102-6 —— 数据权限 fail-closed（REQ-2026-129，P102 M3）
 *
 * <p>缺陷背景：{@link DataPermissionInterceptor} 有多处 {@code catch → log.warn → return}，
 * 任何异常都让<b>过滤条件不生效</b>，即「出问题时放行全表」—— 与安全组件
 * 应有的 fail-closed 方向完全相反。三处实测位置：
 * <ul>
 *   <li>{@code injectDataFilter} 抛异常（SQL 解析失败）</li>
 *   <li>{@code setFieldValue} 写回 BoundSql 失败</li>
 *   <li>{@code getPermission} 查库失败 → 返回 null → 权限为 null 时直接不过滤</li>
 * </ul>
 *
 * <p>本类用 Mockito 构造受控故障，无需 Docker。
 */
@DisplayName("P102 数据权限 fail-closed")
class DataPermissionFailClosedTest {

    private static final String BROKEN_SELECT = "SELECT * FROM (( t_business_doc WHERE"; // 语法不完整

    private UserMapper userMapper;
    private UserRoleMapper userRoleMapper;
    private RoleMapper roleMapper;
    private DataPermissionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        userMapper = Mockito.mock(UserMapper.class);
        userRoleMapper = Mockito.mock(UserRoleMapper.class);
        roleMapper = Mockito.mock(RoleMapper.class);

        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setDeptId(3L);
        // LoginUser 继承 Spring Security User，其构造要求 username/password 非空
        user.setUsername("dp-user");
        user.setPassword("dp-pass");
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setDataScope("DEPT"); // 非 ALL/CUSTOM ⇒ 会走注入逻辑

        when(userMapper.selectById(anyLong())).thenReturn(user);
        when(userRoleMapper.getRoleIdsByUserId(anyLong())).thenReturn(List.of(1L));
        when(roleMapper.selectById(anyLong())).thenReturn(role);

        interceptor = new DataPermissionInterceptor(userMapper, userRoleMapper, roleMapper);
        DataPermissionInterceptor.clearCache();

        LoginUser loginUser = new LoginUser(user,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        DataPermissionInterceptor.clearCache();
    }

    private void runInterceptor() throws Exception {
        MappedStatement ms = Mockito.mock(MappedStatement.class);
        when(ms.getId()).thenReturn("com.huicai.FakeMapper.selectList");
        BoundSql boundSql = Mockito.mock(BoundSql.class);
        when(boundSql.getSql()).thenReturn(BROKEN_SELECT);
        interceptor.beforeQuery(Mockito.mock(Executor.class), ms, null,
                Mockito.mock(RowBounds.class), (ResultHandler) null, boundSql);
    }

    @Test
    @DisplayName("AT-102-6a SQL 解析失败必须抛异常而非静默放行全表")
    void sqlParseFailureMustThrow() {
        assertThrows(BusinessException.class, this::runInterceptor,
                "数据权限 SQL 注入失败被吞掉 ⇒ 过滤条件不生效 ⇒ 泄露全表数据");
    }

    @Test
    @DisplayName("AT-102-6b 异常信息必须可定位（不得只留一句 error 摘要）")
    void thrownExceptionCarriesContext() {
        BusinessException e = assertThrows(BusinessException.class, this::runInterceptor);
        String msg = String.valueOf(e.getMessage());
        assertTrue(msg != null && !msg.isBlank(), "异常信息为空，排障时无从下手");
    }

    @Test
    @DisplayName("AT-102-6c 查询用户权限失败必须 fail-closed（返回 null 等于不过滤）")
    void permissionLookupFailureMustThrow() {
        when(userMapper.selectById(anyLong())).thenThrow(new RuntimeException("db down"));
        DataPermissionInterceptor.clearCache();

        assertThrows(BusinessException.class, this::runInterceptor,
                "getPermission 异常被吞并返回 null，而 null 权限在拦截器里等于「不加过滤」，"
                        + "属最隐蔽的 fail-open");
    }

    @Test
    @DisplayName("AT-102-6d 反证：dataScope=ALL 时不得抛异常（正常放行路径未被误伤）")
    void allScopeStillPassesThrough() throws Exception {
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setDataScope("ALL");
        when(roleMapper.selectById(anyLong())).thenReturn(role);
        DataPermissionInterceptor.clearCache();

        runInterceptor();
    }
}
