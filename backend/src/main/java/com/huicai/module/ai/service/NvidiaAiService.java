package com.huicai.module.ai.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.ai.config.NvidiaAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NVIDIA AI 服务
 * 支持 minimaxai/minimax-m3 多模态模型
 * 功能：纯文本聊天、图片分析、视频分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NvidiaAiService {

    private final NvidiaAiProperties properties;

    /**
     * 纯文本聊天
     *
     * @param userMessage 用户消息
     * @return AI 回复内容
     */
    public String chat(String userMessage) {
        checkEnabled();

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", userMessage);
        messages.add(message);

        return doChatCompletion(messages);
    }

    /**
     * 带系统提示词的聊天
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return AI 回复内容
     */
    public String chatWithSystem(String systemPrompt, String userMessage) {
        checkEnabled();

        List<Map<String, Object>> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);
        }
        Map<String, Object> userMessageMap = new HashMap<>();
        userMessageMap.put("role", "user");
        userMessageMap.put("content", userMessage);
        messages.add(userMessageMap);

        return doChatCompletion(messages);
    }

    /**
     * 图片分析（多模态）
     *
     * @param imageUrl 图片 URL（公开 HTTP URL 或 base64 data URI）
     * @param prompt   分析提示词
     * @return 分析结果
     */
    public String analyzeImage(String imageUrl, String prompt) {
        checkEnabled();

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");

        List<Map<String, Object>> contentParts = new ArrayList<>();

        // 文本部分
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", StrUtil.isBlank(prompt) ? "请描述这张图片" : prompt);
        contentParts.add(textPart);

        // 图片部分
        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        Map<String, String> imageUrlMap = new HashMap<>();
        imageUrlMap.put("url", imageUrl);
        imagePart.put("image_url", imageUrlMap);
        contentParts.add(imagePart);

        message.put("content", contentParts);
        messages.add(message);

        return doChatCompletion(messages);
    }

    /**
     * 视频分析（多模态）
     *
     * @param videoUrl 视频 URL（公开 HTTP URL）
     * @param prompt   分析提示词
     * @return 分析结果
     */
    public String analyzeVideo(String videoUrl, String prompt) {
        checkEnabled();

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");

        List<Map<String, Object>> contentParts = new ArrayList<>();

        // 文本部分
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", StrUtil.isBlank(prompt) ? "请描述这个视频内容" : prompt);
        contentParts.add(textPart);

        // 视频部分
        Map<String, Object> videoPart = new HashMap<>();
        videoPart.put("type", "video_url");
        Map<String, String> videoUrlMap = new HashMap<>();
        videoUrlMap.put("url", videoUrl);
        videoPart.put("video_url", videoUrlMap);
        contentParts.add(videoPart);

        message.put("content", contentParts);
        messages.add(message);

        return doChatCompletion(messages);
    }

    /**
     * 批量图片分析
     *
     * @param imageUrls 图片 URL 列表
     * @param prompt    分析提示词
     * @return 分析结果
     */
    public String analyzeMultipleImages(List<String> imageUrls, String prompt) {
        checkEnabled();

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");

        List<Map<String, Object>> contentParts = new ArrayList<>();

        // 文本部分
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", StrUtil.isBlank(prompt) ? "请描述这些图片" : prompt);
        contentParts.add(textPart);

        // 多张图片
        for (String imageUrl : imageUrls) {
            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            Map<String, String> imageUrlMap = new HashMap<>();
            imageUrlMap.put("url", imageUrl);
            imagePart.put("image_url", imageUrlMap);
            contentParts.add(imagePart);
        }

        message.put("content", contentParts);
        messages.add(message);

        return doChatCompletion(messages);
    }

    /**
     * 执行聊天补全请求
     */
    private String doChatCompletion(List<Map<String, Object>> messages) {
        String url = properties.getBaseUrl() + "/chat/completions";

        // 构建请求体
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", properties.getModel());
        payload.put("messages", messages);
        payload.put("max_tokens", properties.getMaxTokens());
        payload.put("temperature", properties.getTemperature());
        payload.put("top_p", properties.getTopP());
        payload.put("stream", false);

        String requestBody = JSONUtil.toJsonStr(payload);
        log.debug("NVIDIA AI 请求: {}", requestBody);

        try {
            HttpResponse response = HttpRequest.post(url)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .body(requestBody)
                    .timeout(properties.getReadTimeout() * 1000)
                    .execute();

            String responseBody = response.body();
            log.debug("NVIDIA AI 响应: {}", responseBody);

            if (!response.isOk()) {
                log.error("NVIDIA AI 请求失败: status={}, body={}", response.getStatus(), responseBody);
                throw new BusinessException("AI 服务调用失败: HTTP " + response.getStatus());
            }

            JSONObject json = JSONUtil.parseObj(responseBody);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException("AI 服务返回结果为空");
            }

            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject messageObj = firstChoice.getJSONObject("message");
            return messageObj.getStr("content");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 NVIDIA AI 服务异常", e);
            throw new BusinessException("调用 AI 服务异常: " + e.getMessage());
        }
    }

    /**
     * 检查服务是否启用
     */
    private void checkEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException("NVIDIA AI 服务未启用");
        }
        if (StrUtil.isBlank(properties.getApiKey())) {
            throw new BusinessException("NVIDIA API Key 未配置");
        }
    }

    /**
     * 测试连接
     *
     * @return 测试结果消息
     */
    public String testConnection() {
        try {
            checkEnabled();
            String result = chat("你好，请用一句话回复");
            return "连接成功，AI 回复: " + result;
        } catch (Exception e) {
            return "连接失败: " + e.getMessage();
        }
    }
}
