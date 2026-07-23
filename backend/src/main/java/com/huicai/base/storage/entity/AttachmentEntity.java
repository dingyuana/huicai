package com.huicai.base.storage.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_attachment")
public class AttachmentEntity extends BaseEntity {

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
