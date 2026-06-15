package com.huicai.module.storage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.storage.entity.AttachmentEntity;
import com.huicai.module.storage.mapper.AttachmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentMapper mapper;
    private final MinioService minioService;

    @Transactional(rollbackFor = Exception.class)
    public AttachmentEntity upload(MultipartFile file, String bizType, Long bizId, Long uploaderId) {
        try {
            String path = minioService.upload(file, bizType);
            int slashIndex = path.indexOf('/');
            String bucket = path.substring(0, slashIndex);
            String objectName = path.substring(slashIndex + 1);

            AttachmentEntity entity = new AttachmentEntity();
            entity.setBizType(bizType);
            entity.setBizId(bizId);
            entity.setFileName(objectName);
            entity.setOriginalName(file.getOriginalFilename());
            entity.setFilePath(path);
            entity.setBucketName(bucket);
            entity.setFileSize(file.getSize());
            entity.setContentType(file.getContentType());
            entity.setFileHash(sha256(file.getBytes()));
            entity.setUploadedBy(uploaderId);
            mapper.insert(entity);
            return entity;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    public List<AttachmentEntity> listByBiz(String bizType, Long bizId) {
        return mapper.findByBiz(bizType, bizId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AttachmentEntity entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException("附件不存在");
        try {
            minioService.delete(entity.getBucketName(), entity.getFileName());
        } catch (Exception e) {
            log.warn("删除文件失败: {}", e.getMessage());
        }
        mapper.deleteById(id);
    }

    public String presignedUrl(Long id) {
        AttachmentEntity entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException("附件不存在");
        try {
            return minioService.presignedUrl(entity.getBucketName(), entity.getFileName());
        } catch (Exception e) {
            throw new BusinessException("生成下载链接失败: " + e.getMessage());
        }
    }

    /**
     * P15-1: 模拟 OCR — 从 hint + bizType 提取结构化字段, 持久化到 ocrData.
     *
     * <p>不接外部 OCR, 用 HashMap 模拟字段填充. 实际生产可换 Python OCR 微服务.
     *
     * <p>支持 bizType: bank_statement / sales_invoice / input_invoice / expense_reimbursement
     */
    @Transactional(rollbackFor = Exception.class)
    public String runOcr(Long id, java.util.Map<String, String> hint) {
        AttachmentEntity entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException("附件不存在: " + id);
        String bizType = hint != null && hint.containsKey("bizType")
                ? hint.get("bizType") : entity.getBizType();
        String json = mockExtract(bizType, hint == null ? java.util.Collections.emptyMap() : hint);
        entity.setOcrData(json);
        mapper.updateById(entity);
        log.info("P15-1 OCR 提取完成: id={}, bizType={}, json={}", id, bizType, json);
        return json;
    }

    private String mockExtract(String bizType, java.util.Map<String, String> hint) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        switch (bizType == null ? "" : bizType) {
            case "bank_statement":
                map.put("txDate", hint.getOrDefault("txDate", "2026-06-15"));
                map.put("amount", hint.getOrDefault("amount", "0.00"));
                map.put("summary", hint.getOrDefault("summary", ""));
                map.put("counterAccount", hint.getOrDefault("counterAccount", ""));
                break;
            case "sales_invoice":
                map.put("invoiceNo", hint.getOrDefault("invoiceNo", ""));
                map.put("invoiceDate", hint.getOrDefault("invoiceDate", "2026-06-15"));
                map.put("amount", hint.getOrDefault("amount", "0.00"));
                map.put("taxAmount", hint.getOrDefault("taxAmount", "0.00"));
                map.put("customerName", hint.getOrDefault("customerName", ""));
                break;
            case "input_invoice":
                map.put("invoiceNo", hint.getOrDefault("invoiceNo", ""));
                map.put("invoiceDate", hint.getOrDefault("invoiceDate", "2026-06-15"));
                map.put("amount", hint.getOrDefault("amount", "0.00"));
                map.put("taxAmount", hint.getOrDefault("taxAmount", "0.00"));
                map.put("vendorName", hint.getOrDefault("vendorName", ""));
                break;
            case "expense_reimbursement":
                map.put("reimbNo", hint.getOrDefault("reimbNo", ""));
                map.put("employeeName", hint.getOrDefault("employeeName", ""));
                map.put("amount", hint.getOrDefault("amount", "0.00"));
                map.put("expenseType", hint.getOrDefault("expenseType", "OTHER"));
                break;
            default:
                map.put("bizType", bizType == null ? "" : bizType);
                map.put("rawText", hint.getOrDefault("rawText", ""));
        }
        // 简化 JSON 序列化
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            return null;
        }
    }
}
