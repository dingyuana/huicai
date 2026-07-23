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

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataPermissionInterceptor dataPermissionInterceptor) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        // 乐观锁
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // S-26: 企业级数据权限（enterprise_id 隔离）
        interceptor.addInnerInterceptor(new EnterpriseDataPermissionInterceptor());
        // 部门级数据权限（dept_id/created_by 隔离）
        interceptor.addInnerInterceptor(dataPermissionInterceptor);
        return interceptor;
    }
}