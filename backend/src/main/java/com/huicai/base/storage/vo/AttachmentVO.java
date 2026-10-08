package com.huicai.base.storage.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.storage.entity.AttachmentEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * AttachmentVO —— 由 t_attachment 的真实列生成（P102 批次 7）。
 *
 * <p>字段集 = 前端 attachment.ts#Attachment 已声明的 9 个字段，逐个核对：
 * ① DB 有该列；② Entity 有该字段 —— 缺一即停，不会漏前端字段也不会凭空暴露。
 *
 * <p>⚠️ 前端此前全是 <code>Promise&lt;any&gt;</code>（无任何契约），本轮补类型后由
 * MasterDataVoContractTest 逐字段锁死 VO↔interface。
 */
@Data
public class AttachmentVO {

    private Long id;
    private String bizType;
    private Long bizId;
    private String fileName;
    private String originalName;
    private Long fileSize;
    private String contentType;
    private Long uploadedBy;
    private LocalDateTime createdAt;

    public static AttachmentVO from(AttachmentEntity e) {
        if (e == null) {
            return null;
        }
        AttachmentVO vo = new AttachmentVO();
        vo.setId(e.getId());
        if (e.getId() != null) vo.setId(e.getId());
        if (e.getBizType() != null) vo.setBizType(e.getBizType());
        if (e.getBizId() != null) vo.setBizId(e.getBizId());
        if (e.getFileName() != null) vo.setFileName(e.getFileName());
        if (e.getOriginalName() != null) vo.setOriginalName(e.getOriginalName());
        if (e.getFileSize() != null) vo.setFileSize(e.getFileSize());
        if (e.getContentType() != null) vo.setContentType(e.getContentType());
        if (e.getUploadedBy() != null) vo.setUploadedBy(e.getUploadedBy());
        if (e.getCreatedAt() != null) vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<AttachmentVO> from(List<AttachmentEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(AttachmentVO::from).collect(Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<AttachmentVO> from(IPage<AttachmentEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AttachmentVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}
