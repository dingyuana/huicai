package com.huicai.agency.tenant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.agency.tenant.dto.AgencyCreateDTO;
import com.huicai.agency.tenant.dto.AgencyUpdateDTO;
import com.huicai.agency.tenant.dto.AgencyVO;
import com.huicai.agency.tenant.service.AgencyService;
import com.huicai.common.response.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agency/agencies")
@RequiredArgsConstructor
public class AgencyController {

    private final AgencyService agencyService;

    @PostMapping
    public R<AgencyVO> create(@Valid @RequestBody AgencyCreateDTO dto) {
        return R.ok(agencyService.create(dto));
    }

    @PutMapping("/{id}")
    public R<AgencyVO> update(@PathVariable Long id, @RequestBody AgencyUpdateDTO dto) {
        return R.ok(agencyService.update(id, dto));
    }

    @GetMapping("/{id}")
    public R<AgencyVO> getById(@PathVariable Long id) {
        return R.ok(agencyService.getById(id));
    }

    @GetMapping("/page")
    public R<IPage<AgencyVO>> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size) {
        return R.ok(agencyService.page(page, size));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        agencyService.delete(id);
        return R.ok();
    }
}
