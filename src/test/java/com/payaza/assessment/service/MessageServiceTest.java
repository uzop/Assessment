package com.payaza.assessment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payaza.assessment.model.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.util.Collections;
import java.util.stream.Stream;

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
    private DynamoDbIndex<Connection> userIdIndex;

    @Mock
    private ApiGatewayManagementApiClient apiClient;

    private MessageService messageService;

    @BeforeEach
    void setUp(EnvironmentVariables environmentVariables) {
        MockitoAnnotations.openMocks(this);
        when(enhancedClient.table(eq("Connections"), any(TableSchema.class))).thenReturn(connectionTable);
        when(connectionTable.index("UserIdIndex")).thenReturn(userIdIndex);

        // Set environment variables
        environmentVariables.set("API_ID", "test-api-id");
        environmentVariables.set("STAGE", "test");
        environmentVariables.set("AWS_REGION", "us-east-1");

        messageService = new MessageService(new ObjectMapper(), enhancedClient);
    }

    @Test
    void testBidirectionalMessaging() {
        // Setup userA and userB connections
        Connection userAConn = new Connection();
        userAConn.setConnectionId("conn1");
        userAConn.setUserId("userA");

        Connection userBConn = new Connection();
        userBConn.setConnectionId("conn2");
        userBConn.setUserId("userB");

        // Mock Pages
        Page<Connection> userBPage = mock(Page.class);
        when(userBPage.items()).thenReturn(Collections.singletonList(userBConn));

        Page<Connection> userAPage = mock(Page.class);
        when(userAPage.items()).thenReturn(Collections.singletonList(userAConn));

        // Mock SdkIterables
        @SuppressWarnings("unchecked")
        SdkIterable<Page<Connection>> userBIterable = mock(SdkIterable.class);
        when(userBIterable.stream()).thenReturn(Stream.of(userBPage));

        @SuppressWarnings("unchecked")
        SdkIterable<Page<Connection>> userAIterable = mock(SdkIterable.class);
        when(userAIterable.stream()).thenReturn(Stream.of(userAPage));

        // Mock Publishers
        @SuppressWarnings("unchecked")
        Publisher<SdkIterable<Page<Connection>>> userBPublisher = mock(Publisher.class);
        @SuppressWarnings("unchecked")
        Publisher<SdkIterable<Page<Connection>>> userAPublisher = mock(Publisher.class);

        // Mock query to return Publishers
        when(userIdIndex.query(any(QueryEnhancedRequest.class)))
                .thenReturn(userBPublisher)
                .thenReturn(userAPublisher);

        // Mock Publisher behavior
        doAnswer(invocation -> {
            Subscriber<SdkIterable<Page<Connection>>> subscriber = invocation.getArgument(0);
            subscriber.onNext(userBIterable);
            subscriber.onComplete();
            return null;
        }).when(userBPublisher).subscribe(any(Subscriber.class));

        doAnswer(invocation -> {
            Subscriber<SdkIterable<Page<Connection>>> subscriber = invocation.getArgument(0);
            subscriber.onNext(userAIterable);
            subscriber.onComplete();
            return null;
        }).when(userAPublisher).subscribe(any(Subscriber.class));

        // Test userA sending to userB
        messageService.sendMessage("Hi userB!", "conn1", "userB");

        // Verify userA to userB
        ArgumentCaptor<PostToConnectionRequest> captorB =
                ArgumentCaptor.forClass(PostToConnectionRequest.class);
        verify(apiClient, times(1)).postToConnection(captorB.capture());
        assertEquals("conn2", captorB.getValue().connectionId());
        assertEquals("Hi userB!", captorB.getValue().data().asUtf8String());

        // Reset-Y apiClient mock
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