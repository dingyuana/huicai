package com.huicai.base.system.interceptor;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.huicai.common.exception.BusinessException;
import com.huicai.config.security.LoginUser;
import com.huicai.base.system.entity.RoleEntity;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.RoleMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis-Plus 数据权限拦截器.
 * 根据用户角色的 data_scope 自动注入 SQL 过滤条件:
 * <ul>
 *   <li>ALL — 不注入, 查看全部数据</li>
 *   <li>DEPT — 按 dept_id 过滤: WHERE dept_id = :currentUserDept</li>
 *   <li>DEPT_AND_CHILD — 按 dept_id 及下级部门过滤 (降级为 DEPT 行为)</li>
 *   <li>SELF — 按 created_by 过滤: WHERE created_by = :currentUserId</li>
 *   <li>CUSTOM — 不注入, 由业务代码自行处理</li>
 * </ul>
 */
@Slf4j
@Component
public class DataPermissionInterceptor implements InnerInterceptor {

    /**
     * 需要进行数据权限过滤的业务表及其对应的部门/用户字段.
     * 系统表 (t_user, t_role, t_user_role, t_dept, t_menu 等) 不在此列,
     * 避免递归查询导致栈溢出.
     */
    private static final Map<String, FilterConfig> FILTER_CONFIG = new LinkedHashMap<>();

    static {
        // 格式: tableName -> (deptColumn, creatorColumn)
        // 仅列出已确认存在 dept_id/created_by 列的表.
        // 其他表(凭证/发票/客户/供应商/应收应付等)的 schema 缺少这些列, 加入会触发 SQL 异常.
        FILTER_CONFIG.put("t_expense_reimbursement", new FilterConfig("dept_id", "created_by"));
        FILTER_CONFIG.put("t_business_doc",          new FilterConfig("dept_id", "created_by"));
        FILTER_CONFIG.put("t_asset_card",            new FilterConfig("dept_id", "created_by"));
        FILTER_CONFIG.put("t_budget_entry",          new FilterConfig("dept_id", "created_by"));
    }

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    /** 当前线程已加载的用户权限信息缓存, 避免同一请求中重复查询数据库 */
    private static final ThreadLocal<UserPermission> PERMISSION_CACHE = new ThreadLocal<>();

    public DataPermissionInterceptor(@org.springframework.context.annotation.Lazy UserMapper userMapper,
                                     @org.springframework.context.annotation.Lazy UserRoleMapper userRoleMapper,
                                     @org.springframework.context.annotation.Lazy RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        if (InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return;
        }
        LoginUser loginUser;
        try {
            loginUser = (LoginUser) authentication.getPrincipal();
        } catch (ClassCastException e) {
            return;
        }

        String originalSql = boundSql.getSql();
        if (originalSql == null || !originalSql.trim().toUpperCase().startsWith("SELECT")) {
            return;
        }

        UserPermission perm = getPermission(loginUser.getUserId());
        if (perm == null || "ALL".equals(perm.dataScope) || "CUSTOM".equals(perm.dataScope)) {
            return;
        }

        String modifiedSql;
        try {
            modifiedSql = injectDataFilter(originalSql, perm);
        } catch (Exception e) {
            log.warn("数据权限SQL注入失败, 跳过过滤: sql={}, error={}", originalSql, e.getMessage());
            return;
        }

        if (modifiedSql == null || modifiedSql.equals(originalSql)) {
            return;
        }

        try {
            ReflectionUtil.setFieldValue(boundSql, "sql", modifiedSql);
            log.debug("数据权限: userId={}, scope={}, deptId={}, rows filtered",
                    loginUser.getUserId(), perm.dataScope, perm.deptId);
        } catch (Exception e) {
            log.warn("无法修改BoundSql, 跳过数据权限过滤: {}", e.getMessage());
        }
    }

    // ===== SQL 注入 =====

    private String injectDataFilter(String sql, UserPermission perm) throws JSQLParserException {
        Statement statement = CCJSqlParserUtil.parse(sql);
        if (!(statement instanceof Select select)) {
            return null;
        }
        if (!(select.getSelectBody() instanceof PlainSelect plainSelect)) {
            return null;
        }

        Set<String> referencedTables = collectTableNames(plainSelect);
        if (referencedTables.isEmpty()) {
            return null;
        }

        // 检查是否有需要过滤的表被引用
        Map.Entry<String, FilterConfig> target = null;
        for (Map.Entry<String, FilterConfig> entry : FILTER_CONFIG.entrySet()) {
            if (referencedTables.contains(entry.getKey())) {
                target = entry;
                break;
            }
        }
        if (target == null) {
            return null; // 当前查询不涉及需要过滤的表
        }

        String condition = buildCondition(perm, target.getValue());
        if (condition == null) {
            return null;
        }

        if (plainSelect.getWhere() == null) {
            // 没有 WHERE, 新建一个
            plainSelect.setWhere(CCJSqlParserUtil.parseCondExpression(condition));
        } else {
            // 已有 WHERE, AND 追加
            String existingWhere = plainSelect.getWhere().toString();
            String newWhere = existingWhere + " AND " + condition;
            plainSelect.setWhere(CCJSqlParserUtil.parseCondExpression(newWhere));
        }

        return plainSelect.toString();
    }

    /**
     * 收集 PlainSelect 中 FROM 和 JOIN 引用的表名 (去别名).
     */
    private Set<String> collectTableNames(PlainSelect ps) {
        Set<String> tables = new HashSet<>();
        FromItem fromItem = ps.getFromItem();
        if (fromItem instanceof Table table) {
            String name = unquote(table.getName());
            if (name != null) tables.add(name);
        }
        List<Join> joins = ps.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                if (join.getRightItem() instanceof Table table) {
                    String name = unquote(table.getName());
                    if (name != null) tables.add(name);
                }
                // 也检查 join.getFromItem() if present
            }
        }
        // 子查询中的表通过递归 fromItem 处理 — 简化处理: 忽略子查询中的表
        return tables;
    }

    private String unquote(String name) {
        if (name == null) return null;
        String raw = name.replace("\"", "").replace("`", "").toLowerCase();
        // PostgreSQL 可能带 schema, 如 public.t_voucher
        int dot = raw.indexOf('.');
        return dot > 0 ? raw.substring(dot + 1) : raw;
    }

    private String buildCondition(UserPermission perm, FilterConfig config) {
        return switch (perm.dataScope) {
            case "DEPT" -> {
                if (perm.deptId == null) yield null;
                yield config.deptColumn + " = " + perm.deptId;
            }
            case "DEPT_AND_CHILD" -> {
                // 简化: 暂不递归查子部门, 降级为 DEPT 行为
                if (perm.deptId == null) yield null;
                yield config.deptColumn + " = " + perm.deptId;
            }
            case "SELF" -> {
                if (perm.userId == null) yield null;
                yield config.creatorColumn + " = " + perm.userId;
            }
            default -> null;
        };
    }

    // ===== 用户权限查询 =====

    private UserPermission getPermission(Long userId) {
        UserPermission cached = PERMISSION_CACHE.get();
        if (cached != null && cached.userId.equals(userId)) {
            return cached;
        }
        // 预占 ThreadLocal 防止递归调用: 用户查询本身也走 MyBatis 拦截器链,
        // 若不在此预占, 递归的 beforeQuery 会再次调用 getPermission, 进而再次触发
        // userMapper.selectById, 导致无限递归直至 StackOverflowError.
        PERMISSION_CACHE.set(new UserPermission(userId, null, "SELF"));
        try {
            UserEntity user = userMapper.selectById(userId);
            if (user == null) return null;

            List<Long> roleIds = userRoleMapper.getRoleIdsByUserId(userId);
            if (roleIds.isEmpty()) {
                // 无角色 — 保守处理: 仅自己
                UserPermission p = new UserPermission(userId, user.getDeptId(), "SELF");
                PERMISSION_CACHE.set(p);
                return p;
            }

            // 取第一个角色的 dataScope (多角色时取最大范围)
            String scope = "SELF";
            for (Long rid : roleIds) {
                RoleEntity role = roleMapper.selectById(rid);
                if (role != null && role.getDataScope() != null) {
                    if ("ALL".equals(role.getDataScope())) {
                        scope = "ALL";
                        break; // ALL 是最高权限
                    }
                    if ("DEPT_AND_CHILD".equals(role.getDataScope())) {
                        scope = "DEPT_AND_CHILD";
                    } else if ("DEPT".equals(role.getDataScope()) && !"DEPT_AND_CHILD".equals(scope)) {
                        scope = "DEPT";
                    } else if ("CUSTOM".equals(role.getDataScope()) && !"DEPT_AND_CHILD".equals(scope) && !"DEPT".equals(scope)) {
                        scope = "CUSTOM";
                    }
                    // SELF 是默认最低, 除非上面都没匹配到
                }
            }

            UserPermission p = new UserPermission(userId, user.getDeptId(), scope);
            PERMISSION_CACHE.set(p);
            return p;
        } catch (Exception e) {
            log.warn("查询用户数据权限失败(可能因递归查询), userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 当前请求结束时调用此方法清理 ThreadLocal 缓存.
     */
    public static void clearCache() {
        PERMISSION_CACHE.remove();
    }

    // ===== 内部类 =====

    private record UserPermission(Long userId, Long deptId, String dataScope) {}

    private record FilterConfig(String deptColumn, String creatorColumn) {}

    /**
     * 通过反射设置 BoundSql 的 sql 字段 (MyBatis 未提供 setter).
     */
    private static class ReflectionUtil {
        static void setFieldValue(Object obj, String fieldName, Object value) throws Exception {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        }
    }
}
