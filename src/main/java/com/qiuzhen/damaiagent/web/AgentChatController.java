package com.qiuzhen.damaiagent.web;

import com.qiuzhen.damaiagent.agent.TicketingAgent;
import com.qiuzhen.damaiagent.ticketing.HttpTicketingGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * 前端通过同源代理访问 /api/agent/chat，Agent 服务接收 /agent/chat。
 * 身份只来自票务后端对 JSESSIONID 的校验，不从 JSON 或模型输出取得。
 */
@RestController
@RequestMapping("/agent")
@ConditionalOnProperty(prefix = "agent", name = "enabled", havingValue = "true")
public class AgentChatController {

    private final HttpTicketingGateway gateway;
    private final TicketingAgent agent;

    public AgentChatController(HttpTicketingGateway gateway, TicketingAgent agent) {
        this.gateway = gateway;
        this.agent = agent;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResult> chat(
            @RequestBody ChatRequest request,
            @CookieValue(name = "JSESSIONID", required = false) String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录票务平台");
        }
        if (request == null || request.message() == null
                || request.message().isBlank() || request.message().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "消息长度必须在1到1000字之间");
        }
        if (request.conversationId() == null
                || request.conversationId().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话标识无效");
        }
        try {
            UUID.fromString(request.conversationId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话标识必须是UUID");
        }

        long currentUserId = gateway.currentUserId(sessionId);
        String reply = agent.chat(currentUserId,
                request.conversationId(), request.message().strip(), sessionId);
        if (reply == null || reply.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "智能助手未返回有效内容");
        }
        return new ApiResponse<>("SUCCESS", "操作成功",
                new ChatResult(request.conversationId(), reply));
    }

    public record ChatRequest(String message, String conversationId) {}

    public record ChatResult(String conversationId, String reply) {}

    public record ApiResponse<T>(String code, String message, T data) {}
}
