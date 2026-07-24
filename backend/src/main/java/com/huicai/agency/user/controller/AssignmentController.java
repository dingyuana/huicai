package com.huicai.agency.user.controller;

import com.huicai.agency.user.dto.AssignmentCreateDTO;
import com.huicai.agency.user.dto.AssignmentVO;
import com.huicai.agency.user.service.AgencyUserEnterpriseService;
import com.huicai.common.response.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agency/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AgencyUserEnterpriseService agencyUserEnterpriseService;

    @PostMapping
    public R<Void> assign(@Valid @RequestBody AssignmentCreateDTO dto) {
        agencyUserEnterpriseService.assign(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> unassign(@PathVariable Long id) {
        agencyUserEnterpriseService.unassign(id);
        return R.ok();
    }

    @GetMapping
    public R<List<AssignmentVO>> listByAgencyUserId(@RequestParam Long agencyUserId) {
        return R.ok(agencyUserEnterpriseService.listByAgencyUserId(agencyUserId));
    }
}
