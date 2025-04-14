package com.payaza.assessment.service;


import com.payaza.assessment.model.User;
import com.payaza.assessment.repository.UserRepository;
import com.payaza.assessment.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRegisterUser() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");

        userService.registerUser(user);

        verify(userRepository, times(1)).save(user);
    }

    @Test
    void testAuthenticateUser_Success() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");

        when(userRepository.findByUsername("testuser")).thenReturn(user);
        when(jwtUtil.generateToken("testuser")).thenReturn("token");

        String token = userService.authenticateUser(user);

        assertEquals("token", token);
    }

    @Test
    void testAuthenticateUser_Failure() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("wrong");

        when(userRepository.findByUsername("testuser")).thenReturn(null);

        assertThrows(RuntimeException.class, () -> userService.authenticateUser(user));
    }
}
