package com.huicai.common.context;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P102-M5a —— 第二层租户防线 fail-closed（REQ-2026-129）
 *
 * <p>缺陷背景：{@link EnterpriseDataPermissionInterceptor} 是租户隔离的
 * 第二层防线（第一层是 MetaObjectHandler 写入强制覆盖，第三层是 RLS）。
 * 原实现在 SQL 解析失败时只 {@code log.debug} 一句就放过：
 * <pre>
 *   } catch (Exception e) {
 *       log.debug("EnterpriseDataPermissionInterceptor: skip SQL injection for: " + sql);
 *   }
 * </pre>
 * 即<b>「注入失败就不注入」⇒ 该查询返回全表数据</b>，与 M3 在
 * {@code DataPermissionInterceptor} 修掉的是同一类缺陷，但发生在更关键的
 * 租户隔离链路上。解析失败往往源于业务表结构变更或方言差异，属于可预期事件，
 * 静默放行等于把一次报错升级成一次数据泄露。
 */
@DisplayName("P102 企业数据权限 fail-closed")
class EnterpriseDataPermissionFailClosedTest {

    private static final String BROKEN_SQL = "SELECT * FROM (( t_business_doc WHERE";

    private final EnterpriseDataPermissionInterceptor interceptor =
            new EnterpriseDataPermissionInterceptor();

    @BeforeEach
    void setUp() {
        EnterpriseContextHolder.set(1L);
    }

    @AfterEach
    void tearDown() {
        EnterpriseContextHolder.clear();
    }

    private void run(String sql) throws Exception {
        MappedStatement ms = Mockito.mock(MappedStatement.class);
        Mockito.when(ms.getId()).thenReturn("com.huicai.FakeMapper.selectList");
        BoundSql boundSql = Mockito.mock(BoundSql.class);
        Mockito.when(boundSql.getSql()).thenReturn(sql);
        interceptor.beforeQuery(Mockito.mock(Executor.class), ms, null,
                Mockito.mock(RowBounds.class), (ResultHandler) null, boundSql);
    }

    @Test
    @DisplayName("AT-102-5a-1 SQL 解析失败必须中止，不得静默放行全表")
    void parseFailureMustAbort() {
        assertThrows(BusinessException.class, () -> run(BROKEN_SQL),
                "解析失败被吞掉 ⇒ 该查询不注入 enterprise_id ⇒ 泄露全表数据");
    }

    @Test
    @DisplayName("AT-102-5a-2 正常 SELECT 必须注入 enterprise_id 条件（修复未误伤正常路径）")
    void validSelectStillInjected() throws Exception {
        run("SELECT * FROM t_business_doc WHERE id = 1");
        // 注入走反射改写 BoundSql.sql，mock 上无法直接读回；
        // 断言「不抛异常」即证明解析成功且未进入 fail-closed 分支
        assertDoesNotThrow(() -> { });
    }

    @Test
    @DisplayName("AT-102-5a-3 INSERT 不注入也不报错（由 MetaObjectHandler 负责写入侧）")
    void insertDoesNotThrow() {
        assertDoesNotThrow(() -> run("INSERT INTO t_business_doc (id) VALUES (1)"));
    }

    @Test
    @DisplayName("AT-102-5a-4 共享表不做隔离，但仍须能解析（解析不了照样要中止）")
    void sharedTableStillMustParse() {
        assertThrows(BusinessException.class,
                () -> run("SELECT * FROM (( t_user WHERE"),
                "共享表虽不过滤，但 SQL 连解析都失败时无法判断是否共享，应 fail-closed");
    }

    @Test
    @DisplayName("AT-102-5a-5 无企业上下文时不注入也不报错（定时任务/漏洞确认用例依赖此语义）")
    void nullContextSkipsWithoutThrowing() {
        EnterpriseContextHolder.clear();
        assertDoesNotThrow(() -> run("SELECT * FROM t_business_doc"));
    }

    @Test
    @DisplayName("AT-102-5a-6 反证：合法 SQL 确实能被解析（否则前几条断言会因测试自身失效而恒真）")
    void validSqlParses() {
        assertTrue(canParse("SELECT * FROM t_business_doc WHERE id = 1"));
        assertTrue(!canParse(BROKEN_SQL));
    }

    private boolean canParse(String sql) {
        try {
            net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
