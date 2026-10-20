package com.company.procurement.common.controller;

import com.company.procurement.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SystemController {

    @GetMapping("/")
    public Result<Map<String, String>> index() {
        return Result.ok(Map.of(
                "service", "enterprise-procurement-platform",
                "status", "UP"
        ));
    }

    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.ok(Map.of("status", "UP"));
    }
}
