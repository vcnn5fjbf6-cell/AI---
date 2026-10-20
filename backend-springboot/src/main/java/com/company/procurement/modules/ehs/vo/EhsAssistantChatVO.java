package com.company.procurement.modules.ehs.vo;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class EhsAssistantChatVO {
    private Long sessionId;
    private String answer;
    @Builder.Default
    private List<String> sources = new ArrayList<>();
}
