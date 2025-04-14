

package com.payaza.assessment.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.payaza.assessment.security.JwtUtil;
import com.payaza.assessment.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WebSocketHandler implements RequestHandler<APIGatewayV2WebSocketEvent, Object> {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    @Autowired
    public WebSocketHandler(MessageService messageService, ObjectMapper objectMapper, JwtUtil jwtUtil) {
        this.messageService = messageService;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Object handleRequest(APIGatewayV2WebSocketEvent input, Context context) {
        String connectionId = input.getRequestContext().getConnectionId();
        String routeKey = input.getRequestContext().getRouteKey();

        try {
            switch (routeKey) {
                case "$connect":
                    Map<String, String> queryParams = input.getQueryStringParameters();
                    String token = queryParams != null ? queryParams.get("token") : null;
                    if (token == null) {
                        logger.error("No JWT token provided for connection: {}", connectionId);
                        return null;
                    }
                    String userId = jwtUtil.validateToken(token);
                    messageService.storeConnection(connectionId, userId);
                    logger.info("Connected: {} as user: {}", connectionId, userId);
                    break;
                case "$disconnect":
                    messageService.removeConnection(connectionId);
                    logger.info("Disconnected: {}", connectionId);
                    break;
                case "sendMessage":
                    ObjectNode json = (ObjectNode) objectMapper.readTree(input.getBody());
                    String message = json.get("message").asText();
                    String recipientUserId = json.get("recipient").asText();
                    messageService.sendMessage(message, connectionId, recipientUserId);
                    logger.info("Message from {} to user: {}", connectionId, recipientUserId);
                    break;
                default:
                    logger.warn("Unknown route: {}", routeKey);
            }
            return new Object();
        } catch (Exception e) {
            logger.error("Error handling WebSocket request for connection: {}", connectionId, e);
            return null;
        }
    }
}