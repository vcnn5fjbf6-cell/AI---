package com.company.procurement.modules.procurement.assistant.controller;

import com.company.procurement.modules.procurement.assistant.service.FeishuBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/integrations/feishu-bot")
@RequiredArgsConstructor
public class FeishuBotController {

    private final FeishuBotService feishuBotService;

    @PostMapping("/events")
    public Object events(@RequestBody Map<String, Object> payload) {
        return feishuBotService.handleEvent(payload);
    }
}
