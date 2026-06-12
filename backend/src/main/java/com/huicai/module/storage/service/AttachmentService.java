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

    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            return null;
        }
    }
}
