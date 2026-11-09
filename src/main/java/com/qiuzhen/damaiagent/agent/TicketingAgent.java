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
import java.util.Map;

public class TicketingAgent {

    public static final String CURRENT_USER_ID = "currentUserId";

    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Shanghai");

    private final String systemPrompt;
    private final ChatClient chatClient;

    public TicketingAgent(ChatModel chatModel, ToolCallback... tools) throws IOException {

        this.systemPrompt = new ClassPathResource("prompts/system-prompt.md").getContentAsString(StandardCharsets.UTF_8);

        if (systemPrompt.isBlank()) {
            throw new IllegalStateException("客服系统提示词不能为空");
        }

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(30)
                .build();

        var toolCallingManager = ToolCallingManager.builder()
                .maxCallsPerTool(4)
                .maxTotalToolCalls(8)
                .build();

        var toolCallingAdvisor = ToolCallingAdvisor
                .builder()
                .toolCallingManager(toolCallingManager)
                .build();

        this.chatClient = ChatClient.builder(chatModel)
                .defaultTools(tools)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor
                                .builder(chatMemory)
                                .build(),
                        toolCallingAdvisor
                )
                .build();
    }

    public String chat(
            long currentUserId,
            String conversationId,
            String message
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

        return chatClient.prompt()
                .system(requestSystemPrompt)
                .user(message)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        memoryId
                ))
                .toolContext(Map.of(CURRENT_USER_ID, currentUserId))
                .call()
                .content();
    }
}