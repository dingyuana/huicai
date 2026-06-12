package com.huicai.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Jackson 全局配置
 * 将 Long 类型序列化为字符串，避免前端 JavaScript 处理 19 位 Snowflake ID 时精度丢失
 * 反序列化时兼容数字和字符串两种格式
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule longToStringModule() {
        SimpleModule module = new SimpleModule("LongToStringModule");
        // 序列化: Long → String
        module.addSerializer(Long.class, new JsonSerializer<>() {
            @Override
            public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                if (value == null) {
                    gen.writeNull();
                } else {
                    gen.writeString(value.toString());
                }
            }
        });
        // 反序列化: 兼容 String 和 Number → Long
        module.addDeserializer(Long.class, new JsonDeserializer<>() {
            @Override
            public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                if (p.currentToken() == JsonToken.VALUE_STRING) {
                    return Long.parseLong(p.getText());
                } else if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                    return p.getLongValue();
                }
                return (Long) ctxt.handleUnexpectedToken(Long.class, p);
            }
        });
        return module;
    }
}
