package com.payaza.assessment.repository;

import com.payaza.assessment.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserRepositoryTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<User> userTable;

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(enhancedClient.table(eq("Users"), any(TableSchema.class))).thenReturn(userTable);
        userRepository = new UserRepository(enhancedClient);
    }

    @Test
    void testSave() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");

        userRepository.save(user);

        verify(userTable, times(1)).putItem(user);
    }

    @Test
    void testFindByUsername_Found() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");

        when(userTable.getItem(any(Consumer.class))).thenReturn(user);

        User foundUser = userRepository.findByUsername("testuser");

        assertEquals("testuser", foundUser.getUsername());
        assertEquals("password", foundUser.getPassword());
    }

    @Test
    void testFindByUsername_NotFound() {
        when(userTable.getItem(any(Consumer.class))).thenReturn(null);

        User foundUser = userRepository.findByUsername("testuser");

        assertNull(foundUser);
    }
}