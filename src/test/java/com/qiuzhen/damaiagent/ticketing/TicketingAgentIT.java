package com.qiuzhen.damaiagent.ticketing;

import com.qiuzhen.damaiagent.agent.TicketingAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 真实联调：模型选择工具，工具通过 HTTP 查询仿大麦。
 * 需要先启动仿大麦后端，并在运行配置中提供已有登录会话。
 */
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class TicketingAgentIT {

    @Test
    void shouldQueryRealOrdersAndRememberConversation(TestReporter reporter)
            throws Exception {
        String sessionId = System.getenv("DAMAI_USER_SESSION");
        assumeTrue(sessionId != null && !sessionId.isBlank(),
                "真实联调需要配置 DAMAI_USER_SESSION");

        String baseUrl = System.getenv().getOrDefault(
                "DAMAI_BASE_URL", "http://127.0.0.1:8080");
        var httpGateway = new HttpTicketingGateway(baseUrl);

        // 身份来自后端已有会话，不使用写死的用户 ID。
        // 登录失效会在这里结束，尚未调用模型。
        long currentUserId = httpGateway.currentUserId(sessionId);

        List<OrderQuery> queries = new ArrayList<>();
        List<TicketingGateway.OrderPage> matchingPages = new ArrayList<>();

        // 记录真实调用用于断言；数据仍然来自 httpGateway。
        TicketingGateway gateway = (userId, query, sid) -> {
            assertEquals(currentUserId, userId);
            assertTrue(sessionId.equals(sid), "登录会话没有正确传入工具");
            queries.add(query);

            var page = httpGateway.searchMyOrders(userId, query, sid);
            if ("北京".equals(query.city())
                    && Integer.valueOf(1).equals(query.status())
                    && query.keyword() == null
                    && query.page() == 1) {
                matchingPages.add(page);
            }
            return page;
        };

        var options = OpenAiChatOptions.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .model("deepseek-v4.1-flash")
                .extraBody(Map.of("enable_thinking", false))
                .timeout(Duration.ofSeconds(60))
                .maxRetries(0)
                .build();
        var chatModel = OpenAiChatModel.builder().options(options).build();

        var memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(30)
                .build();
        var callbacks = ToolCallbacks.from(new OrderTools(gateway));
        String schema = callbacks[0].getToolDefinition().inputSchema();

        assertFalse(schema.contains(TicketingAgent.CURRENT_USER_ID));
        assertFalse(schema.contains(TicketingAgent.SESSION_ID));

        var agent = new TicketingAgent(chatModel, memory, callbacks);
        String conversationId = "real-order-help-001";
        String question = "请列出我在北京的已支付订单，包含订单编号和完整演出名称。";
        String answer = agent.chat(
                currentUserId, conversationId, question, sessionId);

        assertNotNull(answer);
        assertFalse(answer.isBlank());
        assertFalse(matchingPages.isEmpty(), "模型没有使用预期的订单查询条件");

        var page = matchingPages.getLast();
        for (var order : page.records()) {
            assertEquals("北京", order.city(), "真实接口未按城市筛选");
            assertEquals(1, order.status(), "真实接口未按状态筛选");
            assertTrue(answer.contains(Long.toString(order.orderId())));
            if (order.eventTitle() != null) {
                assertTrue(answer.contains(order.eventTitle()));
            }
        }
        assertTrue(memory.get(currentUserId + ":" + conversationId).stream()
                .anyMatch(message -> message instanceof ToolResponseMessage));

        String followUpQuestion = "我刚才要求查询哪个城市、什么状态的订单？";
        String followUp = agent.chat(
                currentUserId, conversationId, followUpQuestion, sessionId);

        assertNotNull(followUp);
        assertTrue(followUp.contains("北京"));
        assertTrue(followUp.matches("(?s).*(待支付|待付款|未支付|未付款|已支付).*"));

        reporter.publishEntry("工具参数结构", schema);
        reporter.publishEntry("实际查询条件", queries.toString());
        reporter.publishEntry("真实查询结果", page.toString());
        reporter.publishEntry("第一轮问题", question);
        reporter.publishEntry("第一轮回答", answer);
        reporter.publishEntry("第二轮问题", followUpQuestion);
        reporter.publishEntry("第二轮回答", followUp);
    }
}

