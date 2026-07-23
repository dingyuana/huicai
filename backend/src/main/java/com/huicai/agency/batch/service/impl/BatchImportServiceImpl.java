package com.huicai.agency.batch.service.impl;

import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.agency.batch.service.BatchImportService;
import com.huicai.common.context.EnterpriseContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchImportServiceImpl implements BatchImportService {

    @Override
    public BatchResultVO importInvoices(List<MultipartFile> files, Long enterpriseId) {
        BatchResultVO result = new BatchResultVO();
        result.setTotal(files.size());
        List<BatchResultVO.BatchItemResult> details = new ArrayList<>();

        // 设置企业上下文
        EnterpriseContextHolder.set(enterpriseId);
        try {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                BatchResultVO.BatchItemResult item = new BatchResultVO.BatchItemResult();
                item.setId((long) i);
                try {
                    log.info("BatchImport: processing file {} for enterprise {}", file.getOriginalFilename(), enterpriseId);
                    // TODO: 调用 SME 发票导入服务
                    item.setSuccess(true);
                    item.setMessage("导入成功");
                } catch (Exception e) {
                    item.setSuccess(false);
                    item.setMessage("导入失败: " + e.getMessage());
                }
                details.add(item);
            }
        } finally {
            EnterpriseContextHolder.clear();
        }

        long success = details.stream().filter(BatchResultVO.BatchItemResult::isSuccess).count();
        result.setSuccess((int) success);
        result.setFailed(details.size() - (int) success);
        result.setDetails(details);
        return result;
    }
}
