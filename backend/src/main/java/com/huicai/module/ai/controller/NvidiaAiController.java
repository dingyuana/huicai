package com.huicai.module.ai.controller;

import com.huicai.common.response.R;
import com.huicai.module.ai.service.NvidiaAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * NVIDIA AI 服务控制器
 * 提供 minimaxai/minimax-m3 多模态模型接口
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/nvidia")
@RequiredArgsConstructor
@Tag(name = "NVIDIA AI 服务", description = "minimaxai/minimax-m3 多模态模型接口")
public class NvidiaAiController {

    private final NvidiaAiService nvidiaAiService;

    /**
     * 纯文本聊天
     */
    @PostMapping("/chat")
    @Operation(summary = "纯文本聊天")
    public R<String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.isBlank()) {
            return R.fail("消息内容不能为空");
        }
        String response = nvidiaAiService.chat(message);
        return R.ok(response);
    }

    /**
     * 带系统提示词的聊天
     */
    @PostMapping("/chat-with-system")
    @Operation(summary = "带系统提示词的聊天")
    public R<String> chatWithSystem(@RequestBody Map<String, String> request) {
        String systemPrompt = request.get("systemPrompt");
        String userMessage = request.get("userMessage");
        if (userMessage == null || userMessage.isBlank()) {
            return R.fail("用户消息不能为空");
        }
        String response = nvidiaAiService.chatWithSystem(systemPrompt, userMessage);
        return R.ok(response);
    }

    /**
     * 图片分析
     */
    @PostMapping("/analyze-image")
    @Operation(summary = "图片分析（多模态）")
    public R<String> analyzeImage(@RequestBody Map<String, String> request) {
        String imageUrl = request.get("imageUrl");
        String prompt = request.get("prompt");
        if (imageUrl == null || imageUrl.isBlank()) {
            return R.fail("图片 URL 不能为空");
        }
        String response = nvidiaAiService.analyzeImage(imageUrl, prompt);
        return R.ok(response);
    }

    /**
     * 视频分析
     */
    @PostMapping("/analyze-video")
    @Operation(summary = "视频分析（多模态）")
    public R<String> analyzeVideo(@RequestBody Map<String, String> request) {
        String videoUrl = request.get("videoUrl");
        String prompt = request.get("prompt");
        if (videoUrl == null || videoUrl.isBlank()) {
            return R.fail("视频 URL 不能为空");
        }
        String response = nvidiaAiService.analyzeVideo(videoUrl, prompt);
        return R.ok(response);
    }

    /**
     * 批量图片分析
     */
    @PostMapping("/analyze-multiple-images")
    @Operation(summary = "批量图片分析")
    public R<String> analyzeMultipleImages(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> imageUrls = (List<String>) request.get("imageUrls");
        String prompt = (String) request.get("prompt");

        if (imageUrls == null || imageUrls.isEmpty()) {
            return R.fail("图片 URL 列表不能为空");
        }
        String response = nvidiaAiService.analyzeMultipleImages(imageUrls, prompt);
        return R.ok(response);
    }

    /**
     * 测试连接
     */
    @GetMapping("/test")
    @Operation(summary = "测试 AI 服务连接")
    public R<String> testConnection() {
        String result = nvidiaAiService.testConnection();
        return R.ok(result);
    }
}
