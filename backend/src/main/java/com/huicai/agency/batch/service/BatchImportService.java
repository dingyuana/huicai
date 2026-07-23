package com.huicai.agency.batch.service;

import com.huicai.agency.batch.dto.BatchResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BatchImportService {
    BatchResultVO importInvoices(List<MultipartFile> files, Long enterpriseId);
}
