package com.huicai.base.audit.controller;

import com.huicai.common.response.R;
import com.huicai.base.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system/audit-log")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") long page,
                     @RequestParam(defaultValue = "10") long size,
                     @RequestParam(required = false) String module,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) String startDate,
                     @RequestParam(required = false) String endDate) {
        return R.ok(auditLogService.pageLog(page, size, module, status, startDate, endDate));
    }

    @GetMapping("/{id}")
    public R<?> get(@PathVariable Long id) {
        return R.ok(auditLogService.getById(id));
    }
}
