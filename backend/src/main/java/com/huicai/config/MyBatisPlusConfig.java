package com.huicai.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.huicai.base.system.interceptor.DataPermissionInterceptor;
import com.huicai.common.context.EnterpriseDataPermissionInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
@MapperScan("com.huicai.**.mapper")
public class MyBatisPlusConfig {

    /**
     * 拦截器注册顺序<b>直接决定分页 total 是否跨租户</b>（DIR-004）。
     *
     * <p><b>机理</b>（MP 3.5.7 反编译确认）：{@code MybatisPlusInterceptor.intercept}
     * 按注册顺序对每个拦截器依次调用 {@code willDoQuery} → {@code beforeQuery}。
     * 而 {@code PaginationInnerInterceptor.willDoQuery} 会<b>自行拼 COUNT 并直接用
     * executor 执行</b>，那条执行路径<b>不再回到拦截器链</b>。
     * 于是：若分页插件排在企业隔离插件<b>之前</b>，COUNT 生成的那一刻
     * 企业条件尚未注入本次查询的 {@code boundSql} ⇒ <b>COUNT 拿不到
     * {@code enterprise_id}</b>，而 SELECT 拿得到
     * ⇒ {@code records} 是本企业的、{@code total} 是全表的。
     *
     * <p><b>实测（修复前）</b>：上下文企业 990002（无科目）时
     * {@code total=43 / records=0}；两企业夹具（企业 1 两行 + 企业 990003 三行）下
     * 切到 990003 得到 {@code total=5 / records=3}。
     *
     * <p><b>因此顺序必须是：先注入条件，后分页。</b>
     * 守卫见 {@code PaginationTotalTenantIsolationRealDBTest}。
     *
     * <p><b>为什么这个缺陷长期没被发现</b>：多数 Service 在应用层已自己带
     * {@code enterprise_id}，应用层条件把 COUNT 也带对了 ⇒ 缺陷被掩盖；
     * 只有「不加应用层条件」的调用才会暴露。⇒ <b>凡用 MyBatis-Plus 分页，
     * 必须同时断言 total 与 records.size()，只断言 records 等于没断言一半</b>
     * （AGENTS §4.5 第 40 条）。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataPermissionInterceptor dataPermissionInterceptor) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // ── 顺序要求（DIR-004）：条件注入类必须在分页之前 ──
        // S-26: 企业级数据权限（enterprise_id 隔离）
        interceptor.addInnerInterceptor(new EnterpriseDataPermissionInterceptor());
        // 部门级数据权限（dept_id/created_by 隔离）
        interceptor.addInnerInterceptor(dataPermissionInterceptor);
        // 分页（必须在条件注入之后，否则 COUNT 拿不到 enterprise_id）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        // 乐观锁（只作用于 UPDATE，放最后不影响）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}