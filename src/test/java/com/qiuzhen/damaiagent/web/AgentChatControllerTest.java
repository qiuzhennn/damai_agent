package com.qiuzhen.damaiagent.web;

import com.qiuzhen.damaiagent.agent.TicketingAgent;
import com.qiuzhen.damaiagent.agent.ChatReply;
import com.qiuzhen.damaiagent.ticketing.HttpTicketingGateway;
import com.qiuzhen.damaiagent.ticketing.OrderSummary;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentChatControllerTest {
    private static final String CONVERSATION = "9f724f00-561d-460e-8fb4-c984593c6880";

    private final HttpTicketingGateway gateway = mock(HttpTicketingGateway.class);
    private final TicketingAgent agent = mock(TicketingAgent.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AgentChatController(gateway, agent)).build();

    @Test
    void shouldUseAuthenticatedBackendIdentityAndReturnFrontendContract() throws Exception {
        when(gateway.currentUserId("session-a")).thenReturn(1001L);
        var order = new OrderSummary(23L, "北京音乐会", "北京", null,
                0, new BigDecimal("580.00"));
        when(agent.chatWithCards(1001L, CONVERSATION, "查询待付款订单", "session-a"))
                .thenReturn(new ChatReply("找到1笔待支付订单", List.of(order)));

        mvc.perform(post("/agent/chat")
                        .cookie(new Cookie("JSESSIONID", "session-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"查询待付款订单","conversationId":"%s"}
                                """.formatted(CONVERSATION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.conversationId").value(CONVERSATION))
                .andExpect(jsonPath("$.data.reply").value("找到1笔待支付订单"))
                .andExpect(jsonPath("$.data.orders[0].orderId").value(23))
                .andExpect(jsonPath("$.data.orders[0].eventTitle").value("北京音乐会"));
        verify(gateway).currentUserId("session-a");
        verify(agent).chatWithCards(1001L, CONVERSATION, "查询待付款订单", "session-a");
    }

    @Test
    void shouldRejectMissingSessionWithoutCallingModel() throws Exception {
        mvc.perform(post("/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"查订单","conversationId":"%s"}
                                """.formatted(CONVERSATION)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(gateway, agent);
    }

    @Test
    void shouldRejectInvalidConversationId() throws Exception {
        mvc.perform(post("/agent/chat")
                        .cookie(new Cookie("JSESSIONID", "session-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"查订单","conversationId":"someone-elses-conversation"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(gateway, agent);
    }
}
