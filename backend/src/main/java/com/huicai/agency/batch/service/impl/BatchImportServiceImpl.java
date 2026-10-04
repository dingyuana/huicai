package com.huicai.agency.batch.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.agency.batch.service.BatchImportService;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
public class BatchImportServiceImpl implements BatchImportService {

    /** D3：原实现对每个文件无条件回报「导入成功」但未解析任何文件（假成功）。 */
    private static final String NOT_IMPLEMENTED =
            "批量发票导入功能尚未实现：该端点原会回报「导入成功」但未执行任何导入，"
                    + "已改为显式报错以避免假成功（REQ-2026-134 / P107 D3）。"
                    + "单文件导入请使用发票导入端点";

    @Override
    public BatchResultVO importInvoices(List<MultipartFile> files, Long enterpriseId) {
        throw new BusinessException(501, NOT_IMPLEMENTED);
    }
}
