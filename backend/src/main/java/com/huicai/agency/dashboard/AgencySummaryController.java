package com.huicai.agency.dashboard;

import com.huicai.common.response.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/agency/dashboard")
@RequiredArgsConstructor
public class AgencySummaryController {

    private final AgencySummaryService summaryService;

    @GetMapping
    public R<Map<String, Object>> dashboard() {
        return R.ok(summaryService.getDashboard());
    }

    @GetMapping("/accountant/{id}")
    public R<Map<String, Object>> accountantDetail(@PathVariable Long id) {
        return R.ok(summaryService.getAccountantDetail(id));
    }
}