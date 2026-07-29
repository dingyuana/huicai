package com.huicai.common.context;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 企业级数据权限拦截器 — 自动注入 enterprise_id 条件
 * <p>
 * 三层防线第二层：对 SELECT/UPDATE/DELETE 语句自动追加 enterprise_id 条件，
 * 确保不同企业的数据完全隔离。
 * </p>
 */
public class EnterpriseDataPermissionInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseDataPermissionInterceptor.class);

    private static final ThreadLocal<Boolean> RECURSIVE_GUARD = ThreadLocal.withInitial(() -> false);

    private static final Set<String> SHARED_TABLES = Set.of(
        "t_user", "t_role", "t_user_role", "t_menu", "t_role_menu",
        "t_agency", "t_enterprise", "t_agency_enterprise",
        "t_sys_config", "t_audit_log", "t_dept"
    );

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        injectEnterpriseIdCondition(boundSql);
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) throws SQLException {
        BoundSql boundSql = ms.getBoundSql(parameter);
        injectEnterpriseIdCondition(boundSql);
    }

    private void injectEnterpriseIdCondition(BoundSql boundSql) {
        if (Boolean.TRUE.equals(RECURSIVE_GUARD.get())) {
            return;
        }

        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId == null) {
            return; // 超级管理员，不拦截
        }

        String sql = boundSql.getSql();
        try {
            RECURSIVE_GUARD.set(true);
            String newSql = injectCondition(sql, enterpriseId);
            if (newSql != null) {
                java.lang.reflect.Field sqlField = BoundSql.class.getDeclaredField("sql");
                sqlField.setAccessible(true);
                sqlField.set(boundSql, newSql);
            }
        } catch (Exception e) {
            log.debug("EnterpriseDataPermissionInterceptor: skip SQL injection for: {}", sql);
        } finally {
            RECURSIVE_GUARD.remove();
        }
    }

    private String injectCondition(String sql, Long enterpriseId) throws JSQLParserException {
        Statement stmt = CCJSqlParserUtil.parse(sql);

        if (stmt instanceof Select select) {
            return injectSelect(select, enterpriseId);
        } else if (stmt instanceof Update update) {
            return injectUpdate(update, enterpriseId);
        } else if (stmt instanceof Delete delete) {
            return injectDelete(delete, enterpriseId);
        } else if (stmt instanceof Insert) {
            return null; // INSERT 由 MetaObjectHandler 自动填充
        }
        return null;
    }

    private String injectSelect(Select select, Long enterpriseId) throws JSQLParserException {
        if (!(select.getSelectBody() instanceof PlainSelect plainSelect)) {
            return null;
        }

        Set<String> tables = collectTableNames(plainSelect);
        if (tables.isEmpty() || containsOnlySharedTables(tables)) {
            return null;
        }

        // 使用表限定符避免 JOIN 查询中 enterprise_id 列歧义
        String qualifier = getMainTableQualifier(plainSelect);
        String condition = (qualifier != null ? qualifier + "." : "") + "enterprise_id = " + enterpriseId;
        if (plainSelect.getWhere() == null) {
            plainSelect.setWhere(CCJSqlParserUtil.parseCondExpression(condition));
        } else {
            String existingWhere = plainSelect.getWhere().toString();
            plainSelect.setWhere(CCJSqlParserUtil.parseCondExpression(existingWhere + " AND " + condition));
        }
        return plainSelect.toString();
    }

    /**
     * 获取主表限定符，优先使用 FROM 表别名，其次表名。
     * 若 FROM 表是子查询，则回退到 JOIN 中第一个非共享表的别名/表名。
     */
    private String getMainTableQualifier(PlainSelect plainSelect) {
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem instanceof Table table) {
            if (table.getAlias() != null) {
                return table.getAlias().getName();
            }
            String name = unquote(table.getName());
            if (name != null) {
                return name;
            }
        }
        // 回退：从 JOIN 中找第一个非共享表
        List<Join> joins = plainSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                if (join.getRightItem() instanceof Table table) {
                    String name = unquote(table.getName());
                    if (name != null && !SHARED_TABLES.contains(name)) {
                        return table.getAlias() != null ? table.getAlias().getName() : name;
                    }
                }
            }
        }
        return null;
    }

    private String injectUpdate(Update update, Long enterpriseId) throws JSQLParserException {
        String tableName = unquote(update.getTable().getName());
        if (tableName == null || SHARED_TABLES.contains(tableName)) {
            return null;
        }

        String condition = "enterprise_id = " + enterpriseId;
        if (update.getWhere() == null) {
            update.setWhere(CCJSqlParserUtil.parseCondExpression(condition));
        } else {
            String existingWhere = update.getWhere().toString();
            update.setWhere(CCJSqlParserUtil.parseCondExpression(existingWhere + " AND " + condition));
        }
        return update.toString();
    }

    private String injectDelete(Delete delete, Long enterpriseId) throws JSQLParserException {
        String tableName = unquote(delete.getTable().getName());
        if (tableName == null || SHARED_TABLES.contains(tableName)) {
            return null;
        }

        String condition = "enterprise_id = " + enterpriseId;
        if (delete.getWhere() == null) {
            delete.setWhere(CCJSqlParserUtil.parseCondExpression(condition));
        } else {
            String existingWhere = delete.getWhere().toString();
            delete.setWhere(CCJSqlParserUtil.parseCondExpression(existingWhere + " AND " + condition));
        }
        return delete.toString();
    }

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
            }
        }
        return tables;
    }

    private boolean containsOnlySharedTables(Set<String> tables) {
        return tables.stream().allMatch(SHARED_TABLES::contains);
    }

    private String unquote(String name) {
        if (name == null) return null;
        if (name.startsWith("\"") && name.endsWith("\"")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }
}
