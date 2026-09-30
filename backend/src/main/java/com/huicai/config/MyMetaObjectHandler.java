package com.huicai.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.huicai.common.context.EnterpriseContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "importedAt", LocalDateTime.class, LocalDateTime.now());
        // P102 / AT-102-7：enterprise_id 必须**无条件覆盖**为上下文值。
        //
        // 原实现用 strictInsertFill —— 它只在字段为 null 时才填，而
        // BaseEntity.enterpriseId 是 FieldFill.INSERT 的可写真实字段，
        // 于是 @RequestBody XxxEntity（48 处 / 25 个 Controller）传
        // {"enterpriseId": 999} 即可绕过上下文直写他人租户，且不依赖任何 header。
        //
        // 上下文为 null 时（定时任务 / 系统初始化等无登录态路径）保持原值不动：
        // 这些路径由调用方显式指定企业，属正常业务用法；而生产中任何 HTTP
        // 写入路径都必经 JwtAuthenticationFilter 设置上下文（SecurityConfig 为
        // anyRequest().authenticated()，白名单仅含登录/健康检查/文档端点），
        // 故该分支不可被外部请求触达。
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId != null && metaObject.hasSetter("enterpriseId")) {
            metaObject.setValue("enterpriseId", enterpriseId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}