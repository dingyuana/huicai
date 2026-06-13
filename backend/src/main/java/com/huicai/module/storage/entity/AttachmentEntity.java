package com.huicai.module.storage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_attachment")
public class AttachmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizType;
    private Long bizId;
    private String fileName;
    private String originalName;
    private String filePath;
    private String bucketName;
    private Long fileSize;
    private String contentType;
    private String fileHash;
    private String ocrData;
    private String vector;
    private Long uploadedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
