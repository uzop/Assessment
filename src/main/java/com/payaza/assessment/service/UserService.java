package com.payaza.assessment.service;


import com.payaza.assessment.model.User;
import com.payaza.assessment.repository.UserRepository;
import com.payaza.assessment.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for handling user registration and authentication.
 */
@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    /**
     * Constructor with dependency injection.
     * @param userRepository For DynamoDB operations.
     * @param jwtUtil For JWT generation.
     */
    @Autowired
    public UserService(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Registers a new user in DynamoDB.
     * @param user The user to register.
     */
    public void registerUser(User user) {
        logger.info("Registering user: {}", user.getUsername());
        userRepository.save(user);
    }

    /**
     * Authenticates a user and generates a JWT token.
     * @param user The user credentials.
     * @return A JWT token.
     * @throws RuntimeException If authentication fails.
     */
    public String authenticateUser(User user) {
        logger.info("Authenticating user: {}", user.getUsername());
        User storedUser = userRepository.findByUsername(user.getUsername());
        if (storedUser != null && storedUser.getPassword().equals(user.getPassword())) {
            return jwtUtil.generateToken(user.getUsername());
        }
        throw new RuntimeException("Invalid credentials");
    }
}
