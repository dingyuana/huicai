package com.huicai.base.config.controller;

import com.huicai.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统健康检查")
@RestController
@RequestMapping("/api/v1/system")
public class HealthController {

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public R<String> health() {
        return R.ok("service is running");
    }
}