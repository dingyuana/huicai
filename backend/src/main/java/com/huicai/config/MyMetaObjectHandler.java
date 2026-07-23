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
        // S-26: 自动填充 enterprise_id（INSERT 时从上下文获取）
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId != null) {
            this.strictInsertFill(metaObject, "enterpriseId", Long.class, enterpriseId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}