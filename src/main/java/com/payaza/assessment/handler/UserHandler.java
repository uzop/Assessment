package com.payaza.assessment.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.payaza.assessment.model.User;
import com.payaza.assessment.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Lambda handler for user registration and authentication.
 * Processes REST API requests for /register and /login endpoints.
 */
@Component
public class UserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger logger = LoggerFactory.getLogger(UserHandler.class);
    private final UserService userService;
    private final ObjectMapper objectMapper;

    /**
     * Constructor with dependency injection.
     * @param userService Handles user registration and authentication logic.
     * @param objectMapper Converts JSON request bodies to Java objects.
     */
    @Autowired
    public UserHandler(UserService userService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles API Gateway requests for user operations.
     * Supports POST /register and POST /login.
     * @param input The API Gateway request event.
     * @param context The Lambda execution context.
     * @return A response event with status code and body.
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String httpMethod = input.getHttpMethod();
            String path = input.getPath();
            logger.info("Handling {} request on {}", httpMethod, path);

            // Register a new user
            if ("/register".equals(path) && "POST".equals(httpMethod)) {
                User user = objectMapper.readValue(input.getBody(), User.class);
                userService.registerUser(user);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withBody("User registered successfully");
            }
            // Authenticate a user and return JWT
            else if ("/login".equals(path) && "POST".equals(httpMethod)) {
                User user = objectMapper.readValue(input.getBody(), User.class);
                String token = userService.authenticateUser(user);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withBody(token);
            }

            // Handle invalid requests
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("Invalid request");
        } catch (Exception e) {
            logger.error("Error processing request", e);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Internal server error");
        }
    }
}