package com.yizhaoqi.smartpai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.config.JwtAuthenticationFilter;
import com.yizhaoqi.smartpai.config.OrgTagAuthorizationFilter;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.service.CustomUserDetailsService;
import com.yizhaoqi.smartpai.service.OrgTagCacheService;
import com.yizhaoqi.smartpai.service.UserService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController API 集成测试
 * 使用 @WebMvcTest + MockMvc 测试 HTTP 层
 */
@WebMvcTest(UserController.class)
@DisplayName("UserController API 测试")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private OrgTagAuthorizationFilter orgTagAuthorizationFilter;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private OrgTagCacheService orgTagCacheService;

    // ======================== POST /api/v1/users/register ========================

    @Test
    @DisplayName("register - 正常注册返回 200 和成功消息")
    void register_Success_Returns200() throws Exception {
        willDoNothing().given(userService).registerUser("newuser", "password123");

        mockMvc.perform(post("/api/v1/users/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "newuser", "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
    @DisplayName("register - 用户名为空时返回 400")
    void register_EmptyUsername_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "", "password", "pass"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("register - 密码为空时返回 400")
    void register_EmptyPassword_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "user1", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("register - 用户名已存在时返回 400")
    void register_UsernameExists_Returns400() throws Exception {
        willThrow(new CustomException("Username already exists", HttpStatus.BAD_REQUEST))
                .given(userService).registerUser("existing", "pass");

        mockMvc.perform(post("/api/v1/users/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "existing", "password", "pass"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    // ======================== POST /api/v1/users/login ========================

    @Test
    @DisplayName("login - 凭证正确时返回 200 和 token")
    void login_ValidCredentials_Returns200WithToken() throws Exception {
        given(userService.authenticateUser("admin", "admin123")).willReturn("admin");
        given(jwtUtils.generateToken("admin")).willReturn("mock-access-token");
        given(jwtUtils.generateRefreshToken("admin")).willReturn("mock-refresh-token");

        mockMvc.perform(post("/api/v1/users/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("mock-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("mock-refresh-token"));
    }

    @Test
    @DisplayName("login - 用户名为空时返回 400")
    void login_EmptyUsername_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "", "password", "pass"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("login - authenticateUser 返回 null 时返回 401")
    void login_InvalidCredentials_Returns401() throws Exception {
        given(userService.authenticateUser("user1", "wrongpass")).willReturn(null);

        mockMvc.perform(post("/api/v1/users/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "user1", "password", "wrongpass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("login - Service 抛出 CustomException(UNAUTHORIZED) 时返回 401")
    void login_ServiceThrowsUnauthorized_Returns401() throws Exception {
        willThrow(new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED))
                .given(userService).authenticateUser("user1", "badpass");

        mockMvc.perform(post("/api/v1/users/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "user1", "password", "badpass"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login - 服务器内部异常时返回 500")
    void login_UnexpectedException_Returns500() throws Exception {
        given(userService.authenticateUser("user1", "pass"))
                .willThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(post("/api/v1/users/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "user1", "password", "pass"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }
}
