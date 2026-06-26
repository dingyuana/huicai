package com.huicai.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局配置
 * 1. 将 Long 类型序列化为字符串，避免前端 JavaScript 处理 19 位 Snowflake ID 时精度丢失
 * 2. LocalDateTime 统一格式化为 yyyy-MM-dd'T'HH:mm:ss（秒级精度，去除微秒）
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

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        return builder -> {
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(fmt));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(fmt));
        };
    }
}
