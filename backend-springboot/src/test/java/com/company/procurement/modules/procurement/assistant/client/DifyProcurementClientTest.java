package com.company.procurement.modules.procurement.assistant.client;

import com.company.procurement.modules.procurement.assistant.dto.ProcurementAiStartDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DifyProcurementClientTest {

    @Test
    void parsesChatflowStreamingOutputsFromWorkflowFinishedEvent() throws Exception {
        DifyProcurementClient client = new DifyProcurementClient(null, new ObjectMapper());
        ProcurementAiStartDTO dto = new ProcurementAiStartDTO();
        dto.setRawText("你好");
        String sse = """
            data: {"event":"workflow_started","workflow_run_id":"run1"}
            data: {"event":"workflow_finished","data":{"status":"succeeded","outputs":{"text":"{\\"messageIntent\\":\\"CHAT\\",\\"reply\\":\\"你好！有什么可以帮你的吗？\\",\\"summary\\":\\"用户打招呼，非采购需求\\",\\"candidates\\":[]}"}}}
            data: {"event":"message_end","conversation_id":"conv1","message_id":"msg1"}
            """;

        DifyProcurementClient.FeishuMessageIntentResult result = parseIntent(client, sse, dto);

        assertThat(result.getIntent()).isEqualTo("CHAT");
        assertThat(result.getReply()).isEqualTo("你好！有什么可以帮你的吗？");
        assertThat(result.getAiError()).isNull();
        assertThat(result.isProcurementDemand()).isFalse();
    }

    @Test
    void returnsNoCandidateWhenChatflowHasTitleButNoPositivePrice() throws Exception {
        DifyProcurementClient client = new DifyProcurementClient(null, new ObjectMapper());
        ProcurementAiStartDTO dto = new ProcurementAiStartDTO();
        dto.setRawText("采购2个罗技无线鼠标，用于机房值班电脑备用");
        String sse = """
            data: {"event":"workflow_finished","data":{"status":"succeeded","outputs":{"text":"{\\"reqTitle\\":\\"罗技无线鼠标采购\\",\\"reqType\\":\\"其他\\",\\"summary\\":\\"用户需求明确，但未接入商品搜索结果。\\",\\"candidates\\":[]}"}}}
            data: {"event":"message_end","conversation_id":"conv1","message_id":"msg1"}
            """;

        DifyProcurementClient.ProcurementAiAnalysisResult result = parseAnalysis(client, sse, dto);

        assertThat(result.getReqTitle()).isEqualTo("罗技无线鼠标采购");
        assertThat(result.getCandidates()).isEmpty();
        assertThat(result.getAiError()).contains("unit price greater than 0");
    }

    private DifyProcurementClient.FeishuMessageIntentResult parseIntent(
        DifyProcurementClient client,
        String responseText,
        ProcurementAiStartDTO dto
    ) throws Exception {
        Method method = DifyProcurementClient.class.getDeclaredMethod(
            "parseIntentAgentResponseText",
            String.class,
            ProcurementAiStartDTO.class
        );
        method.setAccessible(true);
        return (DifyProcurementClient.FeishuMessageIntentResult) method.invoke(client, responseText, dto);
    }

    private DifyProcurementClient.ProcurementAiAnalysisResult parseAnalysis(
        DifyProcurementClient client,
        String responseText,
        ProcurementAiStartDTO dto
    ) throws Exception {
        Method method = DifyProcurementClient.class.getDeclaredMethod(
            "parseAgentResponseText",
            String.class,
            ProcurementAiStartDTO.class
        );
        method.setAccessible(true);
        return (DifyProcurementClient.ProcurementAiAnalysisResult) method.invoke(client, responseText, dto);
    }
}
