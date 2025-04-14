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

// Manages WebSocket connections and messages for a serverless chat app, storing connections in DynamoDB and sending messages via API Gateway.
@Service
public class MessageService {
    // Logger for debugging, warnings, and errors.
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    // DynamoDB table for storing connection-to-user mappings.
    private final DynamoDbTable<Connection> connectionTable;
    // JSON parser, unused here but included for potential future use.
    private final ObjectMapper objectMapper;
    // Client for sending messages to WebSocket connections via API Gateway.
    private final ApiGatewayManagementApiClient apiClient;

    // Initializes dependencies and configures DynamoDB and API Gateway clients using environment variables.
    @Autowired
    public MessageService(ObjectMapper objectMapper, DynamoDbEnhancedClient enhancedClient) {
        this.objectMapper = objectMapper;
        // Create DynamoDB table handle for the "Connections" table, mapped to the Connection class.
        this.connectionTable = enhancedClient.table("Connections", TableSchema.fromBean(Connection.class));
        // Retrieve environment variables for API Gateway endpoint configuration.
        String apiId = System.getenv("API_ID");
        String stage = System.getenv("STAGE");
        String region = System.getenv("AWS_REGION");
        // Fallback to defaults if environment variables are missing to ensure local testing works.
        if (apiId == null || stage == null || region == null) {
            apiId = "local-api-id";
            stage = "prod";
            region = "us-east-1";
            logger.warn("Environment variables API_ID, STAGE, or AWS_REGION not set, using defaults: apiId={}, stage={}, region={}",
                    apiId, stage, region);
        }
        // Construct API Gateway WebSocket endpoint URL.
        String endpoint = String.format("https://%s.execute-api.%s.amazonaws.com/%s", apiId, region, stage);
        // Initialize API Gateway client with the custom endpoint.
        this.apiClient = ApiGatewayManagementApiClient.builder()
                .endpointOverride(URI.create(endpoint))
                .build();
        logger.info("Initialized ApiGatewayManagementApiClient with endpoint: {}", endpoint);
    }

    // Stores a WebSocket connection in DynamoDB, mapping a connectionId to a userId.
    public void storeConnection(String connectionId, String userId) {
        // Create a new Connection object to represent the WebSocket session.
        Connection connection = new Connection();
        connection.setConnectionId(connectionId);
        connection.setUserId(userId);
        // Persist the connection to the "Connections" table.
        connectionTable.putItem(connection);
        logger.info("Stored connection: {} for user: {}", connectionId, userId);
    }

    // Removes a WebSocket connection from DynamoDB using its connectionId.
    public void removeConnection(String connectionId) {
        // Delete the connection by its partition key (connectionId).
        connectionTable.deleteItem(r -> r.key(k -> k.partitionValue(connectionId)));
        logger.info("Removed connection: {}", connectionId);
    }

    // Sends a message from a sender to a recipient's active WebSocket connections.
    public void sendMessage(String message, String senderConnectionId, String recipientUserId) {
        logger.info("Sending message from {} to user {}", senderConnectionId, recipientUserId);
        // Build a query to find all connections for the recipient using the UserIdIndex.
        QueryEnhancedRequest query = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(recipientUserId).build()))
                .build();
        // Execute the query on the UserIdIndex and stream the results.
        connectionTable.index("UserIdIndex").query(query).stream()
                // Flatten paginated results into a stream of Connection objects.
                .flatMap(page -> page.items().stream())
                // Process each connection for the recipient.
                .forEach(connection -> {
                    String connectionId = connection.getConnectionId();
                    try {
                        // Send the message to the connection via API Gateway.
                        apiClient.postToConnection(PostToConnectionRequest.builder()
                                .connectionId(connectionId)
                                .data(SdkBytes.fromByteArray(message.getBytes()))
                                .build());
                        logger.info("Sent message to {}", connectionId);
                    } catch (Exception e) {
                        // Log and remove stale connections if sending fails (e.g., connection closed).
                        logger.error("Failed to send message to {}", connectionId, e);
                        removeConnection(connectionId);
                    }
                });
    }
}