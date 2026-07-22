package com.huicai.base.masterdata.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.system.model.dto.ImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface VendorService {
    IPage<VendorEntity> pageQuery(String keyword, Boolean isActive, Integer current, Integer size);
    List<VendorEntity> listAll();
    VendorEntity getById(Long id);
    VendorEntity create(VendorEntity entity);
    VendorEntity update(VendorEntity entity);
    void delete(Long id);
    List<Map<String, Object>> unsettledSummary();
    ImportResult importFromExcel(MultipartFile file);
    InputStream createExportTemplate();
}
