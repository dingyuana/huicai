package com.huicai.module.storage.controller;

import com.huicai.common.response.R;
import com.huicai.module.storage.entity.AttachmentEntity;
import com.huicai.module.storage.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "附件管理")
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService service;

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public R<AttachmentEntity> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bizType") String bizType,
            @RequestParam(value = "bizId", required = false) Long bizId,
            @RequestParam(value = "uploaderId", required = false) Long uploaderId) {
        return R.ok(service.upload(file, bizType, bizId, uploaderId));
    }

    @Operation(summary = "查询业务附件")
    @GetMapping("/list")
    public R<List<AttachmentEntity>> list(
            @RequestParam String bizType,
            @RequestParam Long bizId) {
        return R.ok(service.listByBiz(bizType, bizId));
    }

    @Operation(summary = "获取下载链接")
    @GetMapping("/{id}/url")
    public R<Map<String, String>> getUrl(@PathVariable Long id) {
        String url = service.presignedUrl(id);
        return R.ok(Map.of("url", url));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "P15-1: 模拟 OCR — 提取附件结构化字段, 持久化到 ocrData")
    @PostMapping("/{id}/ocr")
    public R<Map<String, String>> runOcr(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> hint) {
        String json = service.runOcr(id, hint == null ? Map.of() : hint);
        return R.ok(Map.of("ocrData", json));
    }
}
