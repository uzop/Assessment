package com.payaza.assessment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payaza.assessment.model.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SystemStubsExtension.class)
class MessageServiceTest {
    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<Connection> connectionTable;

    @Mock
    private ApiGatewayManagementApiClient apiClient;

    @Mock
    private ConnectionQueryProvider queryProvider;

    private MessageService messageService;

    // Test-specific interface to mock query results
    interface ConnectionQueryProvider {
        List<Connection> findConnectionsByUserId(String userId);
    }

    @BeforeEach
    void setUp(EnvironmentVariables environmentVariables) {
        MockitoAnnotations.openMocks(this);
        when(enhancedClient.table(eq("Connections"), any(TableSchema.class))).thenReturn(connectionTable);

        // Set environment variables
        environmentVariables.set("API_ID", "test-api-id");
        environmentVariables.set("STAGE", "test");
        environmentVariables.set("AWS_REGION", "us-east-1");

        // Use reflection or direct instantiation for test
        messageService = new MessageService(new ObjectMapper(), enhancedClient) {
            @Override
            public void sendMessage(String message, String senderConnectionId, String recipientUserId) {
                List<Connection> connections = queryProvider.findConnectionsByUserId(recipientUserId);
                connections.forEach(connection -> {
                    String connectionId = connection.getConnectionId();
                    try {
                        apiClient.postToConnection(PostToConnectionRequest.builder()
                                .connectionId(connectionId)
                                .data(SdkBytes.fromByteArray(message.getBytes()))
                                .build());
                    } catch (Exception e) {
                        removeConnection(connectionId);
                    }
                });
            }
        };
    }

    @Test
    void testSendAndReceiveMessages() {
        // Setup userA and userB connections
        Connection userAConn = new Connection();
        userAConn.setConnectionId("conn1");
        userAConn.setUserId("userA");

        Connection userBConn = new Connection();
        userBConn.setConnectionId("conn2");
        userBConn.setUserId("userB");

        // Mock query provider
        when(queryProvider.findConnectionsByUserId("userB"))
                .thenReturn(Collections.singletonList(userBConn));
        when(queryProvider.findConnectionsByUserId("userA"))
                .thenReturn(Collections.singletonList(userAConn));

        // Test userA sending to userB
        messageService.sendMessage("Hi userB!", "conn1", "userB");

        // Verify userA to userB
        ArgumentCaptor<PostToConnectionRequest> captorB =
                ArgumentCaptor.forClass(PostToConnectionRequest.class);
        verify(apiClient, times(1)).postToConnection(captorB.capture());
        assertEquals("conn2", captorB.getValue().connectionId());
        assertEquals("Hi userB!", captorB.getValue().data().asUtf8String());

        // Reset apiClient mock
        reset(apiClient);

        // Test userB sending to userA
        messageService.sendMessage("Hi userA!", "conn2", "userA");

        // Verify userB to userA
        ArgumentCaptor<PostToConnectionRequest> captorA =
                ArgumentCaptor.forClass(PostToConnectionRequest.class);
        verify(apiClient, times(1)).postToConnection(captorA.capture());
        assertEquals("conn1", captorA.getValue().connectionId());
        assertEquals("Hi userA!", captorA.getValue().data().asUtf8String());
    }
}