package com.company.procurement.modules.procurement.labor.controller;

import com.company.procurement.common.result.Result;
import com.company.procurement.modules.procurement.labor.dto.PersonalLaborRequestDTO;
import com.company.procurement.modules.procurement.labor.service.PersonalLaborService;
import com.company.procurement.modules.procurement.labor.vo.PersonalLaborItemStatusVO;
import com.company.procurement.modules.procurement.labor.vo.PersonalLaborSubmitResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/procurement/labor")
@RequiredArgsConstructor
public class PersonalLaborController {

    private final PersonalLaborService personalLaborService;

    @GetMapping("/items")
    public Result<List<PersonalLaborItemStatusVO>> listMyItems() {
        return Result.ok(personalLaborService.listMyItems());
    }

    @PostMapping("/requests")
    public Result<PersonalLaborSubmitResultVO> submitRequest(@RequestBody @Valid PersonalLaborRequestDTO dto) {
        return Result.ok(personalLaborService.submitRequest(dto));
    }

    @PostMapping("/records/{id:\\d+}/confirm-received")
    public Result<PersonalLaborItemStatusVO> confirmReceived(@PathVariable Long id) {
        return Result.ok(personalLaborService.confirmReceived(id));
    }
}
