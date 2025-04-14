package com.payaza.assessment.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payaza.assessment.security.JwtUtil;
import com.payaza.assessment.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketHandlerTest {
    @Mock
    private MessageService messageService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private WebSocketHandler webSocketHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testConnect() {
        String token = "valid-token";
        when(jwtUtil.validateToken(token)).thenReturn("userA");

        APIGatewayV2WebSocketEvent event = new APIGatewayV2WebSocketEvent();
        event.setRequestContext(new APIGatewayV2WebSocketEvent.RequestContext());
        event.getRequestContext().setRouteKey("$connect");
        event.getRequestContext().setConnectionId("conn1");
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("token", token);
        event.setQueryStringParameters(queryParams);

        webSocketHandler.handleRequest(event, null);

        verify(messageService).storeConnection("conn1", "userA");
    }

    @Test
    void testSendMessage() throws Exception {
        String body = "{\"action\": \"sendMessage\", \"message\": \"Hi userB!\", \"recipient\": \"userB\"}";
        ObjectMapper realMapper = new ObjectMapper();
        when(objectMapper.readTree(body)).thenReturn(realMapper.readTree(body));

        APIGatewayV2WebSocketEvent event = new APIGatewayV2WebSocketEvent();
        event.setRequestContext(new APIGatewayV2WebSocketEvent.RequestContext());
        event.getRequestContext().setRouteKey("sendMessage");
        event.getRequestContext().setConnectionId("conn1");
        event.setBody(body);

        webSocketHandler.handleRequest(event, null);

        verify(messageService).sendMessage("Hi userB!", "conn1", "userB");
    }
}