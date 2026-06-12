package com.huicai.module.storage.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket:huicai-files}")
    private String defaultBucket;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    /**
     * 上传文件
     * @return 文件访问 URL(相对路径)
     */
    public String upload(MultipartFile file, String bizType) throws Exception {
        return upload(file, defaultBucket, bizType);
    }

    /**
     * 上传文件到指定桶
     */
    public String upload(MultipartFile file, String bucket, String bizType) throws Exception {
        ensureBucket(bucket);
        String originalName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String suffix = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";
        String objectName = bizType + "/" + UUID.randomUUID().toString().replace("-", "") + suffix;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }
        log.info("文件上传成功: bucket={}, object={}, size={}", bucket, objectName, file.getSize());
        return bucket + "/" + objectName;
    }

    /**
     * 下载文件
     */
    public InputStream download(String bucket, String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
    }

    /**
     * 删除文件
     */
    public void delete(String bucket, String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
    }

    /**
     * 获取预签名下载 URL(有效期 1 小时)
     */
    public String presignedUrl(String bucket, String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .method(Method.GET)
                .expiry(1, TimeUnit.HOURS)
                .build());
    }

    /**
     * 确保桶存在
     */
    private void ensureBucket(String bucket) throws Exception {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("创建桶: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("检查桶失败, 尝试创建: {}", e.getMessage());
            try {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("创建桶: {}", bucket);
            } catch (Exception createErr) {
                log.error("创建桶失败: {}", createErr.getMessage());
                throw createErr;
            }
        }
    }

    /**
     * 列举对象
     */
    public Iterable<Result<Item>> listObjects(String bucket, String prefix) {
        return minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .recursive(true)
                .build());
    }
}
