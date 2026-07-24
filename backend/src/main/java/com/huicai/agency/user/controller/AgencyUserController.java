package com.huicai.agency.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.agency.user.dto.AgencyUserCreateDTO;
import com.huicai.agency.user.dto.AgencyUserVO;
import com.huicai.agency.user.service.AgencyUserService;
import com.huicai.common.response.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agency/users")
@RequiredArgsConstructor
public class AgencyUserController {

    private final AgencyUserService agencyUserService;

    @GetMapping("/page")
    public R<IPage<AgencyUserVO>> page(@RequestParam(required = false) Long agencyId,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return R.ok(agencyUserService.page(page, size, agencyId));
    }

    @GetMapping
    public R<List<AgencyUserVO>> list(@RequestParam(required = false) Long agencyId) {
        return R.ok(agencyUserService.list(agencyId));
    }

    @PostMapping
    public R<AgencyUserVO> create(@Valid @RequestBody AgencyUserCreateDTO dto) {
        return R.ok(agencyUserService.create(dto));
    }

    @GetMapping("/{id}")
    public R<AgencyUserVO> getById(@PathVariable Long id) {
        return R.ok(agencyUserService.getById(id));
    }

    @PostMapping("/{id}/suspend")
    public R<Void> suspend(@PathVariable Long id) {
        agencyUserService.suspend(id);
        return R.ok();
    }

    @PostMapping("/{id}/reactivate")
    public R<Void> reactivate(@PathVariable Long id) {
        agencyUserService.reactivate(id);
        return R.ok();
    }

    @PostMapping("/{id}/terminate")
    public R<Void> terminate(@PathVariable Long id) {
        agencyUserService.terminate(id);
        return R.ok();
    }
}
