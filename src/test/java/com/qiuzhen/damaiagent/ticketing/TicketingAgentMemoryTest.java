package com.qiuzhen.damaiagent.ticketing;

import com.qiuzhen.damaiagent.agent.TicketingAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TicketingAgentMemoryTest {

    @Test
    void shouldPreserveToolResultsAcrossTurns(
            TestReporter reporter
    ) throws Exception {

        var memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(
                        new InMemoryChatMemoryRepository()
                )
                .maxMessages(30)
                .build();

        var toolRequest = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(
                        new AssistantMessage.ToolCall(
                                "call-order-1",
                                "function",
                                "searchMyOrders",
                                """
                                {"city":"上海","status":0}
                                """
                        )
                ))
                .build();

        List<Prompt> modelRequests = new ArrayList<>();

        // 脚本模型：验证程序如何执行工具、保存和传递历史。
        ChatModel scriptedModel = new ChatModel() {

            @Override
            public ToolCallingChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }

            @Override
            public ChatResponse call(Prompt prompt) {
                modelRequests.add(prompt);

                AssistantMessage output =
                        switch (modelRequests.size()) {
                            case 1 -> toolRequest;
                            case 2 -> new AssistantMessage(
                                    "已找到两笔订单。"
                            );
                            case 3 -> new AssistantMessage(
                                    "已收到后续问题。"
                            );
                            case 4 -> new AssistantMessage("你好。");
                            default -> throw new AssertionError(
                                    "发生了非预期的模型调用"
                            );
                        };

                return new ChatResponse(
                        List.of(new Generation(output))
                );
            }
        };

        var agent = new TicketingAgent(
                scriptedModel,
                memory,
                ToolCallbacks.from(
                        new OrderTools(new MockTicketingGateway())
                )
        );

        var firstReply = agent.chatWithCards(
                1001L,
                "tool-memory",
                "查询上海待支付订单",
                null
        );

        assertEquals(List.of(102L, 101L), firstReply.orders().stream()
                .map(OrderSummary::orderId).toList());

        // 第一次模型请求工具，第二次根据工具结果回答。
        assertEquals(2, modelRequests.size());

        var history = memory.get("1001:tool-memory");

        assertEquals(
                1L,
                history.stream()
                        .filter(UserMessage.class::isInstance)
                        .count(),
                "用户消息不应被重复保存"
        );

        assertTrue(
                history.stream()
                        .filter(AssistantMessage.class::isInstance)
                        .map(AssistantMessage.class::cast)
                        .anyMatch(AssistantMessage::hasToolCalls),
                "应保存模型发出的工具调用"
        );

        var toolResult = history.stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .filter(response ->
                        response.name().equals("searchMyOrders")
                )
                .findFirst()
                .orElseThrow();

        assertEquals("call-order-1", toolResult.id());

        assertTrue(
                toolResult.responseData().contains("上海周末音乐会")
        );

        assertTrue(
                toolResult.responseData().contains("totalAmount")
        );

        reporter.publishEntry(
                "保存的消息类型",
                history.stream()
                        .map(message -> message.getMessageType().name())
                        .toList()
                        .toString()
        );

        reporter.publishEntry(
                "工具原始结果",
                toolResult.responseData()
        );

        var followUpReply = agent.chatWithCards(
                1001L,
                "tool-memory",
                "订单101的金额是多少？",
                null
        );

        assertTrue(followUpReply.orders().isEmpty(),
                "本轮没有新查询，不应把历史订单再次当作本轮查询结果返回");

        assertEquals(3, modelRequests.size());

        assertTrue(
                modelRequests.get(2).getInstructions().stream()
                        .anyMatch(
                                ToolResponseMessage.class::isInstance
                        ),
                "下一轮模型请求应携带上一轮工具结果"
        );

        var otherUserReply = agent.chatWithCards(1002L, "tool-memory", "你好", null);
        assertTrue(otherUserReply.orders().isEmpty(), "卡片不能跨请求、跨用户共用");
        assertTrue(modelRequests.getLast().getInstructions().stream()
                .noneMatch(ToolResponseMessage.class::isInstance),
                "相同会话标识下，不同用户的模型历史也必须隔离");
    }
}
