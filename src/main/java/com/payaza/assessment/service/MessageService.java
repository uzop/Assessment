package com.payaza.assessment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payaza.assessment.model.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;

import java.net.URI;

@Service
public class MessageService {
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    private final DynamoDbTable<Connection> connectionTable;
    private final ObjectMapper objectMapper;
    private final ApiGatewayManagementApiClient apiClient;

    @Autowired
    public MessageService(ObjectMapper objectMapper, DynamoDbEnhancedClient enhancedClient) {
        this.objectMapper = objectMapper;
        this.connectionTable = enhancedClient.table("Connections", TableSchema.fromBean(Connection.class));
        String apiId = System.getenv("API_ID");
        String stage = System.getenv("STAGE");
        String region = System.getenv("AWS_REGION");
        if (apiId == null || stage == null || region == null) {
            apiId = "local-api-id";
            stage = "prod";
            region = "us-east-1";
            logger.warn("Environment variables API_ID, STAGE, or AWS_REGION not set, using defaults: apiId={}, stage={}, region={}",
                    apiId, stage, region);
        }
        String endpoint = String.format("https://%s.execute-api.%s.amazonaws.com/%s", apiId, region, stage);
        this.apiClient = ApiGatewayManagementApiClient.builder()
                .endpointOverride(URI.create(endpoint))
                .build();
        logger.info("Initialized ApiGatewayManagementApiClient with endpoint: {}", endpoint);
    }

    public void storeConnection(String connectionId, String userId) {
        Connection connection = new Connection();
        connection.setConnectionId(connectionId);
        connection.setUserId(userId);
        connectionTable.putItem(connection);
        logger.info("Stored connection: {} for user: {}", connectionId, userId);
    }

    public void removeConnection(String connectionId) {
        connectionTable.deleteItem(r -> r.key(k -> k.partitionValue(connectionId)));
        logger.info("Removed connection: {}", connectionId);
    }

    public void sendMessage(String message, String senderConnectionId, String recipientUserId) {
        logger.info("Sending message from {} to user {}", senderConnectionId, recipientUserId);
        QueryEnhancedRequest query = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(recipientUserId).build()))
                .build();
        connectionTable.index("UserIdIndex").query(query).stream()
                .flatMap(page -> page.items().stream())
                .forEach(connection -> {
                    String connectionId = connection.getConnectionId();
                    try {
                        apiClient.postToConnection(PostToConnectionRequest.builder()
                                .connectionId(connectionId)
                                .data(SdkBytes.fromByteArray(message.getBytes()))
                                .build());
                        logger.info("Sent message to {}", connectionId);
                    } catch (Exception e) {
                        logger.error("Failed to send message to {}", connectionId, e);
                        removeConnection(connectionId);
                    }
                });
    }
}