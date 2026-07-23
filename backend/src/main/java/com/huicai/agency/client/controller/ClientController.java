package com.huicai.agency.client.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.agency.client.dto.ContractCreateDTO;
import com.huicai.agency.client.dto.ContractVO;
import com.huicai.agency.client.dto.RenewalReminderVO;
import com.huicai.agency.client.service.ContractService;
import com.huicai.common.response.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agency/contracts")
@RequiredArgsConstructor
public class ClientController {

    private final ContractService contractService;

    @PostMapping
    public R<ContractVO> create(@Valid @RequestBody ContractCreateDTO dto) {
        return R.ok(contractService.create(dto));
    }

    @GetMapping("/{id}")
    public R<ContractVO> getById(@PathVariable Long id) {
        return R.ok(contractService.getById(id));
    }

    @GetMapping("/page")
    public R<IPage<ContractVO>> page(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return R.ok(contractService.page(page, size));
    }

    @GetMapping("/renewal-reminders")
    public R<List<RenewalReminderVO>> getRenewalReminders() {
        return R.ok(contractService.getRenewalReminders());
    }

    @PutMapping("/{id}/renew")
    public R<ContractVO> renew(@PathVariable Long id) {
        return R.ok(contractService.renew(id));
    }
}
