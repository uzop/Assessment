package com.payaza.assessment.repository;

import com.payaza.assessment.model.User;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * Repository for user data in DynamoDB.
 */
@Repository
public class UserRepository {
    private final DynamoDbTable<User> userTable;

    /**
     * Constructor with DynamoDB client.
     * @param enhancedClient The DynamoDB enhanced client.
     */
    public UserRepository(DynamoDbEnhancedClient enhancedClient) {
        this.userTable = enhancedClient.table("Users", TableSchema.fromBean(User.class));
    }

    /**
     * Saves a user to DynamoDB.
     * @param user The user to save.
     */
    public void save(User user) {
        userTable.putItem(user);
    }

    /**
     * Finds a user by username.
     * @param username The username to search for.
     * @return The user, or null if not found.
     */
    public User findByUsername(String username) {
        return userTable.getItem(r -> r.key(k -> k.partitionValue(username)));
    }
}