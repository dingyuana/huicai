package com.huicai.agency.batch.service.impl;

import com.huicai.agency.batch.dto.BatchResultAccumulator;
import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.agency.batch.service.BatchImportService;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.tax.service.impl.InputInvoiceImportService;
import com.huicai.sme.tax.service.impl.SalesInvoiceImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchImportServiceImpl implements BatchImportService {

    private final InputInvoiceImportService inputInvoiceImportService;
    private final SalesInvoiceImportService salesInvoiceImportService;
    private final AgencyBatchTransactionRunner transactionRunner;

    @Override
    public BatchResultVO importInvoices(List<MultipartFile> files, Long enterpriseId, String invoiceType) {
        String type = BatchRequestValidator.requireInvoiceType(invoiceType);
        if (enterpriseId == null || enterpriseId <= 0) {
            throw BusinessException.badRequest("目标企业 ID 无效");
        }
        if (files == null || files.isEmpty()) {
            throw BusinessException.badRequest("请选择至少一个发票文件");
        }
        if (files.size() > BatchRequestValidator.MAX_ITEMS) {
            throw BusinessException.badRequest("单次最多上传 " + BatchRequestValidator.MAX_ITEMS + " 个文件");
        }

        BatchResultAccumulator result = new BatchResultAccumulator();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            long itemId = index + 1L;
            String filename = file == null || file.getOriginalFilename() == null
                    ? "文件 #" + itemId
                    : file.getOriginalFilename();
            try {
                if (file == null) {
                    throw BusinessException.badRequest("文件缺失");
                }
                if (!isExcelFile(filename)) {
                    throw BusinessException.badRequest("仅支持 XLS/XLSX 文件");
                }
                if (file.isEmpty()) {
                    throw BusinessException.badRequest("文件内容为空");
                }
                ImportOutcome outcome = transactionRunner.run(enterpriseId, () -> importFile(file, type));
                String message = formatOutcome(filename, outcome);
                if (outcome.complete()) {
                    result.success(itemId, message);
                } else {
                    result.failure(itemId, message);
                }
            } catch (BusinessException e) {
                log.warn("代理批量发票导入失败 enterpriseId={} file={}: {}",
                        enterpriseId, filename, e.getMessage());
                result.failure(itemId, filename + ": " + e.getMessage());
            } catch (RuntimeException e) {
                log.error("代理批量发票导入发生系统异常 enterpriseId={} file={}",
                        enterpriseId, filename, e);
                result.failure(itemId, filename + ": 系统处理失败，请联系管理员查看服务日志");
            }
        }
        return result.build();
    }

    private ImportOutcome importFile(MultipartFile file, String invoiceType) {
        Map<String, Object> preview = "input".equals(invoiceType)
                ? inputInvoiceImportService.previewInvoices(file)
                : salesInvoiceImportService.previewInvoices(file);
        Object batchIdValue = preview.get("batchId");
        if (!(batchIdValue instanceof String batchId) || batchId.isBlank()) {
            throw new IllegalStateException("发票导入预览未返回批次编号");
        }

        Map<String, Object> imported = "input".equals(invoiceType)
                ? inputInvoiceImportService.confirmImport(batchId)
                : salesInvoiceImportService.confirmImport(batchId);
        int parsedRows = requiredNumber(preview.get("total"), "导入预览未返回行数");
        int importedRows = requiredNumber(imported.get("success"), "导入结果未返回成功行数");
        int duplicateSkipped = requiredNumber(
                imported.get("duplicateSkipped"), "导入结果未返回重复跳过行数");
        List<?> parseErrors = requiredList(preview.get("errors"), "导入预览未返回错误明细");
        List<?> importErrors = requiredList(imported.get("errors"), "导入结果未返回错误明细");
        List<String> messages = new ArrayList<>();
        parseErrors.stream().map(BatchImportServiceImpl::formatError).forEach(messages::add);
        importErrors.stream().map(BatchImportServiceImpl::formatError).forEach(messages::add);

        int totalRows = parsedRows + parseErrors.size();
        boolean complete = totalRows > 0
                && importedRows + duplicateSkipped == totalRows
                && parseErrors.isEmpty()
                && importErrors.isEmpty();
        return new ImportOutcome(totalRows, importedRows, duplicateSkipped, messages, complete);
    }

    private static String formatOutcome(String filename, ImportOutcome outcome) {
        String summary = filename + ": 导入 " + outcome.importedRows() + " 行，重复跳过 "
                + outcome.duplicateSkipped() + " 行，共 " + outcome.totalRows() + " 行";
        if (outcome.errors().isEmpty()) {
            return summary;
        }
        return summary + "；" + String.join("；", outcome.errors());
    }

    private static boolean isExcelFile(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xls") || lower.endsWith(".xlsx");
    }

    private static int requiredNumber(Object value, String message) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException(message);
        }
        return number.intValue();
    }

    private static List<?> requiredList(Object value, String message) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException(message);
        }
        return list;
    }

    private static String formatError(Object error) {
        if (error instanceof Map<?, ?> map) {
            Object row = map.get("row");
            Object message = map.get("message");
            if (row != null && message != null) {
                return "第 " + row + " 行：" + message;
            }
            if (message != null) {
                return message.toString();
            }
        }
        return String.valueOf(error);
    }

    private record ImportOutcome(
            int totalRows, int importedRows, int duplicateSkipped, List<String> errors, boolean complete) {
    }
}
