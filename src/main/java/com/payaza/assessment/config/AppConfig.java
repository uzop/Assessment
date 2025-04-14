package com.payaza.assessment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Spring configuration for beans.
 */
@Configuration
public class AppConfig {

    /**
     * Provides an ObjectMapper bean for JSON serialization.
     * @return ObjectMapper instance.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Provides a DynamoDbEnhancedClient for table operations.
     * @return DynamoDbEnhancedClient instance.
     */
    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient() {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(DynamoDbClient.builder().build())
                .build();
    }
}
