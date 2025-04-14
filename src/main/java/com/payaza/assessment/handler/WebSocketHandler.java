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

// Handles WebSocket events for a serverless chat app, processing connect, disconnect, and message actions via AWS API Gateway.
@Component
public class WebSocketHandler implements RequestHandler<APIGatewayV2WebSocketEvent, Object> {
    // Logger for debugging and error tracking.
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    // Service for managing connections and messages.
    private final MessageService messageService;
    // JSON parser for message payloads.
    private final ObjectMapper objectMapper;
    // Utility for JWT validation.
    private final JwtUtil jwtUtil;

    // Injects dependencies via Spring DI.
    @Autowired
    public WebSocketHandler(MessageService messageService, ObjectMapper objectMapper, JwtUtil jwtUtil) {
        this.messageService = messageService;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    // Processes WebSocket events, routing to connect, disconnect, or sendMessage based on routeKey.
    // Returns Object for success, null for errors.
    @Override
    public Object handleRequest(APIGatewayV2WebSocketEvent input, Context context) {
        // Extract connection ID from event.
        String connectionId = input.getRequestContext().getConnectionId();
        // Get route key ($connect, $disconnect, sendMessage).
        String routeKey = input.getRequestContext().getRouteKey();

        // Wrap processing in try-catch to handle errors gracefully.
        try {
            switch (routeKey) {
                case "$connect":
                    // Handle new WebSocket connection with JWT authentication.
                    Map<String, String> queryParams = input.getQueryStringParameters();
                    String token = queryParams != null ? queryParams.get("token") : null;
                    if (token == null) {
                        logger.error("No JWT token provided for connection: {}", connectionId);
                        return null; // Reject connection without token.
                    }
                    // Validate JWT to get user ID.
                    String userId = jwtUtil.validateToken(token);
                    // Store connection in DynamoDB with user ID.
                    messageService.storeConnection(connectionId, userId);
                    logger.info("Connected: {} as user: {}", connectionId, userId);
                    break;
                case "$disconnect":
                    // Handle WebSocket disconnection, removing connection from DynamoDB.
                    messageService.removeConnection(connectionId);
                    logger.info("Disconnected: {}", connectionId);
                    break;
                case "sendMessage":
                    // Handle sending a message to another user.
                    // Parse JSON body for message and recipient.
                    ObjectNode json = (ObjectNode) objectMapper.readTree(input.getBody());
                    String message = json.get("message").asText();
                    String recipientUserId = json.get("recipient").asText();
                    // Send message to recipient via MessageService.
                    messageService.sendMessage(message, connectionId, recipientUserId);
                    logger.info("Message from {} to user: {}", connectionId, recipientUserId);
                    break;
                default:
                    // Log unknown routes for debugging.
                    logger.warn("Unknown route: {}", routeKey);
            }
            // Return empty object to indicate success.
            return new Object();
        } catch (Exception e) {
            // Log and handle any errors during processing.
            logger.error("Error handling WebSocket request for connection: {}", connectionId, e);
            return null; // Indicate failure.
        }
    }
}