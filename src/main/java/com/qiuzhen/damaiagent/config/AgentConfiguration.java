package com.qiuzhen.damaiagent.config;

import com.qiuzhen.damaiagent.agent.TicketingAgent;
import com.qiuzhen.damaiagent.ticketing.HttpTicketingGateway;
import com.qiuzhen.damaiagent.ticketing.EventTools;
import com.qiuzhen.damaiagent.ticketing.HttpEventGateway;
import com.qiuzhen.damaiagent.ticketing.OrderTools;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * 仅在明确启用 Agent 时装配模型和工具。
 * 这样本地未配置模型密钥时，原有 SpringBootTest 仍能正常加载上下文。
 */
@Configuration
@ConditionalOnProperty(prefix = "agent", name = "enabled", havingValue = "true")
public class AgentConfiguration {

    @Bean
    HttpTicketingGateway httpTicketingGateway(
            @Value("${DAMAI_BASE_URL:http://127.0.0.1:8080}") String baseUrl) {
        return new HttpTicketingGateway(baseUrl);
    }

    @Bean
    HttpEventGateway httpEventGateway(
            @Value("${DAMAI_BASE_URL:http://127.0.0.1:8080}") String baseUrl) {
        return new HttpEventGateway(baseUrl);
    }

    @Bean
    ChatModel agentChatModel(
            @Value("${DASHSCOPE_API_KEY:}") String apiKey,
            @Value("${AGENT_MODEL_NAME:deepseek-v4.1-flash}") String modelName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "已启用 Agent，但尚未设置 DASHSCOPE_API_KEY 环境变量");
        }

        var options = OpenAiChatOptions.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey(apiKey)
                .model(modelName)
                .extraBody(Map.of("enable_thinking", false))
                .timeout(Duration.ofSeconds(50))
                .maxRetries(0)
                .build();
        return OpenAiChatModel.builder().options(options).build();
    }

    @Bean
    TicketingAgent ticketingAgent(ChatModel model,
                                 HttpTicketingGateway gateway,
                                 HttpEventGateway eventGateway) throws IOException {
        return new TicketingAgent(model,
                ToolCallbacks.from(new OrderTools(gateway), new EventTools(eventGateway)));
    }
}
