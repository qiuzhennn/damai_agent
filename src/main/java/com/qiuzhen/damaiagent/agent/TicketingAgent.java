package com.qiuzhen.damaiagent.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 组合系统提示词、会话记忆和业务工具。
 * 用户身份、登录会话由调用方传入，不从用户消息中解析。
 */
public class TicketingAgent {

    public static final String CURRENT_USER_ID = "currentUserId";
    public static final String SESSION_ID = "ticketingSessionId";

    private static final ZoneId PLATFORM_ZONE =
            ZoneId.of("Asia/Shanghai");

    private final String systemPrompt;
    private final ChatClient chatClient;

    public TicketingAgent(
            ChatModel chatModel,
            ToolCallback... tools
    ) throws IOException {
        this(
                chatModel,
                MessageWindowChatMemory.builder()
                        .chatMemoryRepository(
                                new InMemoryChatMemoryRepository())
                        .maxMessages(30)
                        .build(),
                tools
        );
    }

    public TicketingAgent(
            ChatModel chatModel,
            ChatMemory chatMemory,
            ToolCallback... tools
    ) throws IOException {
        this.systemPrompt =
                new ClassPathResource("prompts/system-prompt.md")
                        .getContentAsString(StandardCharsets.UTF_8);

        if (systemPrompt.isBlank()) {
            throw new IllegalStateException("客服系统提示词不能为空");
        }

        var manager = ToolCallingManager.builder()
                .maxCallsPerTool(4)
                .maxTotalToolCalls(8)
                .build();

        // 记忆放在工具循环内，保存工具调用及返回结果。
        var memoryAdvisor =
                MessageChatMemoryAdvisor.builder(chatMemory)
                        .order(ToolCallingAdvisor.DEFAULT_ORDER + 100)
                        .build();

        // 由记忆组件统一维护历史，避免重复拼接消息。
        var toolAdvisor = ToolCallingAdvisor.builder()
                .toolCallingManager(manager)
                .disableInternalConversationHistory()
                .build();

        this.chatClient = ChatClient.builder(chatModel)
                .defaultTools((Object[]) tools)
                .defaultAdvisors(toolAdvisor, memoryAdvisor)
                .build();
    }

    /**
     * 保留不依赖 HTTP 会话的调用方式，供内存网关等场景使用。
     */
    public String chat(
            long currentUserId,
            String conversationId,
            String message
    ) {
        return chat(currentUserId, conversationId, message, null);
    }

    public String chat(
            long currentUserId,
            String conversationId,
            String message,
            String sessionId
    ) {
        if (currentUserId <= 0) {
            throw new IllegalArgumentException("用户身份无效");
        }

        if (conversationId == null
                || conversationId.isBlank()
                || conversationId.length() > 100) {
            throw new IllegalArgumentException("会话标识无效");
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }

        String memoryId = currentUserId + ":" + conversationId;

        String requestSystemPrompt = systemPrompt
                + "\n\n# 本次请求的可信上下文\n"
                + "平台当前时间："
                + ZonedDateTime.now(PLATFORM_ZONE)
                + "\n平台时区："
                + PLATFORM_ZONE.getId();

        // 每次请求独立传递身份和会话。
        // ToolContext 直接交给工具，不写入提示词或会话消息。
        Map<String, Object> context = new HashMap<>();
        context.put(CURRENT_USER_ID, currentUserId);

        if (sessionId != null && !sessionId.isBlank()) {
            context.put(SESSION_ID, sessionId);
        }

        return chatClient.prompt()
                .system(requestSystemPrompt)
                .user(message)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        memoryId
                ))
                .toolContext(context)
                .call()
                .content();
    }
}