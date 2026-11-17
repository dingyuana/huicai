package com.huicai.agency.batch.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchResultAccumulator {

    private final List<BatchResultVO.BatchItemResult> details = new ArrayList<>();

    public void success(Long id, String message) {
        add(id, true, message);
    }

    public void failure(Long id, String message) {
        add(id, false, message);
    }

    public BatchResultVO build() {
        BatchResultVO result = new BatchResultVO();
        int success = (int) details.stream().filter(BatchResultVO.BatchItemResult::isSuccess).count();
        result.setTotal(details.size());
        result.setSuccess(success);
        result.setFailed(details.size() - success);
        result.setDetails(List.copyOf(details));
        return result;
    }

    private void add(Long id, boolean success, String message) {
        BatchResultVO.BatchItemResult item = new BatchResultVO.BatchItemResult();
        item.setId(id);
        item.setSuccess(success);
        item.setMessage(message);
        details.add(item);
    }
}
