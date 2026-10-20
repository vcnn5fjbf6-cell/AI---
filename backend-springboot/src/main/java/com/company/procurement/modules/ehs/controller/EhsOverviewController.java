package com.company.procurement.modules.ehs.controller;

import com.company.procurement.common.result.Result;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.modules.ehs.service.EhsOverviewService;
import com.company.procurement.modules.ehs.vo.EhsOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ehs")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ehs", name = "enabled", havingValue = "true")
public class EhsOverviewController {

    private final EhsOverviewService overviewService;

    @GetMapping("/overview")
    public Result<EhsOverviewVO> overview() {
        return Result.ok(overviewService.overview(AuthContextHolder.requireUser().getUserId()));
    }
}
