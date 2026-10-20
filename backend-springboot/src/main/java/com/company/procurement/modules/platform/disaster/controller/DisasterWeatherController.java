package com.company.procurement.modules.platform.disaster.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.platform.disaster.dto.DisasterWeatherRecordQueryDTO;
import com.company.procurement.modules.platform.disaster.dto.DisasterWeatherSettingsDTO;
import com.company.procurement.modules.platform.disaster.dto.DisasterWeatherTriggerDTO;
import com.company.procurement.modules.platform.disaster.service.DisasterWeatherService;
import com.company.procurement.modules.platform.disaster.vo.DisasterWeatherOverviewVO;
import com.company.procurement.modules.platform.disaster.vo.DisasterWeatherRecordVO;
import com.company.procurement.modules.platform.disaster.vo.DisasterWeatherSettingsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/disaster-weather")
@RequiredArgsConstructor
public class DisasterWeatherController {

    private final DisasterWeatherService disasterWeatherService;

    @GetMapping("/overview")
    public Result<DisasterWeatherOverviewVO> overview() {
        return Result.ok(disasterWeatherService.overview());
    }

    @GetMapping("/settings")
    public Result<DisasterWeatherSettingsVO> settings() {
        return Result.ok(disasterWeatherService.getSettings());
    }

    @PutMapping("/settings")
    public Result<Void> updateSettings(@RequestBody DisasterWeatherSettingsDTO dto) {
        disasterWeatherService.updateSettings(dto);
        return Result.ok();
    }

    @GetMapping("/records/page")
    public Result<PageResult<DisasterWeatherRecordVO>> pageRecords(@Valid DisasterWeatherRecordQueryDTO queryDTO) {
        return Result.ok(disasterWeatherService.pageRecords(queryDTO));
    }

    @PostMapping("/trigger")
    public Result<DisasterWeatherRecordVO> trigger(@RequestBody(required = false) DisasterWeatherTriggerDTO dto) {
        return Result.ok(disasterWeatherService.trigger(dto == null ? new DisasterWeatherTriggerDTO() : dto));
    }
}
