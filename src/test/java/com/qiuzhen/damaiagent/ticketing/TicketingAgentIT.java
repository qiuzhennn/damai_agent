package com.qiuzhen.damaiagent.ticketing;

import com.qiuzhen.damaiagent.agent.TicketingAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(
        named = "DASHSCOPE_API_KEY",
        matches = ".+"
)
class TicketingAgentIT {

    @Test
    void shouldUseToolsAndRememberConversation(
            TestReporter reporter
    ) throws Exception {

        var options = OpenAiChatOptions.builder()
                .baseUrl(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1"
                )
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .model("deepseek-v4.1-flash")
                .extraBody(Map.of("enable_thinking", false))
                .timeout(Duration.ofSeconds(60))
                .maxRetries(0)
                .build();

        var chatModel = OpenAiChatModel.builder()
                .options(options)
                .build();

        var gateway = spy(new MockTicketingGateway());

        var tools = ToolCallbacks.from(
                new OrderTools(gateway)
        );

        String schema =
                tools[0].getToolDefinition().inputSchema();

        // 用户身份不应成为模型填写的工具参数。
        assertFalse(schema.contains("currentUserId"));

        var agent = new TicketingAgent(chatModel, tools);

        String answer = agent.chat(
                1001L,
                "order-help-001",
                "请列出我在上海的待支付订单，包含订单编号和完整演出名称。"
        );

        var queries = ArgumentCaptor.forClass(OrderQuery.class);

        verify(gateway, atLeastOnce()).searchMyOrders(
                eq(1001L),
                queries.capture()
        );

        assertTrue(
                queries.getAllValues().stream().anyMatch(query ->
                        "上海".equals(query.city())
                                && Integer.valueOf(0).equals(query.status())
                                && query.keyword() == null
                                && query.page() == 1
                )
        );

        assertNotNull(answer);
        assertTrue(answer.contains("上海周末音乐会"));
        assertTrue(answer.contains("上海城市话剧"));
        assertFalse(answer.contains("上海独立音乐会"));

        String followUp = agent.chat(
                1001L,
                "order-help-001",
                "我刚才要求查询哪个城市、什么状态的订单？"
        );

        assertNotNull(followUp);
        assertTrue(followUp.contains("上海"));
        assertTrue(
                followUp.matches(
                        "(?s).*(待支付|待付款|未支付|未付款).*"
                )
        );

        reporter.publishEntry("工具参数结构", schema);
        reporter.publishEntry(
                "实际查询条件",
                queries.getAllValues().toString()
        );
        reporter.publishEntry("第一轮回答", answer);
        reporter.publishEntry("第二轮回答", followUp);
    }
}