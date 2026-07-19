package com.huicai.base.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * NVIDIA API 配置属性
 * 支持 minimaxai/minimax-m3 多模态模型
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.nvidia")
public class NvidiaAiProperties {

    /**
     * 是否启用 NVIDIA AI 服务
     */
    private boolean enabled = false;

    /**
     * API Key
     */
    private String apiKey;

    /**
     * API 基础地址
     */
    private String baseUrl = "https://integrate.api.nvidia.com/v1";

    /**
     * 使用的模型名称
     */
    private String model = "minimaxai/minimax-m3";

    /**
     * 最大 token 数
     */
    private Integer maxTokens = 8192;

    /**
     * 温度参数
     */
    private Double temperature = 1.00;

    /**
     * Top-p 参数
     */
    private Double topP = 0.95;

    /**
     * 连接超时时间（秒）
     */
    private Integer connectTimeout = 30;

    /**
     * 读取超时时间（秒）
     */
    private Integer readTimeout = 120;
}
