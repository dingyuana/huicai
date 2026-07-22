package com.huicai.base.masterdata.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.system.model.dto.ImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface CustomerService {
    IPage<CustomerEntity> pageQuery(String keyword, Boolean isActive, Integer current, Integer size);
    List<CustomerEntity> listAll();
    CustomerEntity getById(Long id);
    CustomerEntity create(CustomerEntity entity);
    CustomerEntity update(CustomerEntity entity);
    void delete(Long id);
    List<Map<String, Object>> unsettledSummary();
    ImportResult importFromExcel(MultipartFile file);
    InputStream createExportTemplate();
}
