package com.yizhaoqi.smartpai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.config.JwtAuthenticationFilter;
import com.yizhaoqi.smartpai.config.OrgTagAuthorizationFilter;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;
import com.yizhaoqi.smartpai.service.CustomUserDetailsService;
import com.yizhaoqi.smartpai.service.DocumentService;
import com.yizhaoqi.smartpai.service.OrgTagCacheService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DocumentController API 集成测试
 * 使用 MockMvc 测试文档相关接口
 * 注意：Controller 方法依赖 @RequestAttribute 注入 userId/role，测试时通过 requestAttr 注入
 */
@WebMvcTest(DocumentController.class)
@DisplayName("DocumentController API 测试")
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private FileUploadRepository fileUploadRepository;

    @MockitoBean
    private OrganizationTagRepository organizationTagRepository;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private OrgTagAuthorizationFilter orgTagAuthorizationFilter;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private OrgTagCacheService orgTagCacheService;

    private FileUpload mockFile;

    @BeforeEach
    void setUp() {
        mockFile = new FileUpload();
        mockFile.setFileMd5("abc123");
        mockFile.setFileName("test.pdf");
        mockFile.setUserId("user1");
        mockFile.setPublic(false);
        mockFile.setTotalSize(1024L);
        mockFile.setOrgTag("default");
    }

    // ======================== DELETE /{fileMd5} ========================

    @Test
    @DisplayName("deleteDocument - 文件所有者删除成功返回 200")
    void deleteDocument_Owner_Returns200() throws Exception {
        given(fileUploadRepository.findByFileMd5AndUserId("abc123", "user1"))
                .willReturn(Optional.of(mockFile));
        willDoNothing().given(documentService).deleteDocument("abc123", "user1");

        mockMvc.perform(delete("/api/v1/documents/abc123")
                        .with(csrf())
                        .requestAttr("userId", "user1")
                        .requestAttr("role", "USER")
                        .requestAttr("orgTags", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文档删除成功"));
    }

    @Test
    @DisplayName("deleteDocument - 文件不存在时返回 404")
    void deleteDocument_FileNotFound_Returns404() throws Exception {
        given(fileUploadRepository.findByFileMd5AndUserId("notExist", "user1"))
                .willReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/documents/notExist")
                        .with(csrf())
                        .requestAttr("userId", "user1")
                        .requestAttr("role", "USER")
                        .requestAttr("orgTags", "default"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("deleteDocument - 非所有者非管理员返回 403")
    void deleteDocument_NotOwnerNotAdmin_Returns403() throws Exception {
        // 文件属于 user2，但当前用户是 user1（USER 角色）
        FileUpload otherFile = new FileUpload();
        otherFile.setFileMd5("abc123");
        otherFile.setFileName("other.pdf");
        otherFile.setUserId("user2");

        given(fileUploadRepository.findByFileMd5AndUserId("abc123", "user1"))
                .willReturn(Optional.of(otherFile));

        mockMvc.perform(delete("/api/v1/documents/abc123")
                        .with(csrf())
                        .requestAttr("userId", "user1")
                        .requestAttr("role", "USER")
                        .requestAttr("orgTags", "default"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("deleteDocument - 管理员可以删除他人文件")
    void deleteDocument_Admin_CanDeleteOthersFile_Returns200() throws Exception {
        FileUpload otherFile = new FileUpload();
        otherFile.setFileMd5("abc123");
        otherFile.setFileName("other.pdf");
        otherFile.setUserId("user2");

        given(fileUploadRepository.findByFileMd5AndUserId("abc123", "admin"))
                .willReturn(Optional.of(otherFile));
        willDoNothing().given(documentService).deleteDocument("abc123", "admin");

        mockMvc.perform(delete("/api/v1/documents/abc123")
                        .with(csrf())
                        .requestAttr("userId", "admin")
                        .requestAttr("role", "ADMIN")
                        .requestAttr("orgTags", "default,admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("deleteDocument - Service 抛出异常时返回 500")
    void deleteDocument_ServiceException_Returns500() throws Exception {
        given(fileUploadRepository.findByFileMd5AndUserId("abc123", "user1"))
                .willReturn(Optional.of(mockFile));
        willThrow(new RuntimeException("Delete failed")).given(documentService)
                .deleteDocument("abc123", "user1");

        mockMvc.perform(delete("/api/v1/documents/abc123")
                        .with(csrf())
                        .requestAttr("userId", "user1")
                        .requestAttr("role", "USER")
                        .requestAttr("orgTags", "default"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ======================== GET /accessible ========================

    @Test
    @DisplayName("getAccessibleFiles - 返回用户可访问的文件列表")
    void getAccessibleFiles_ReturnsFileList() throws Exception {
        given(documentService.getAccessibleFiles("user1", "default"))
                .willReturn(Arrays.asList(mockFile));

        mockMvc.perform(get("/api/v1/documents/accessible")
                        .requestAttr("userId", "user1")
                        .requestAttr("orgTags", "default")
                        .requestAttr("role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("getAccessibleFiles - 无文件时返回空列表")
    void getAccessibleFiles_NoFiles_ReturnsEmptyList() throws Exception {
        given(documentService.getAccessibleFiles("user1", "default"))
                .willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/documents/accessible")
                        .requestAttr("userId", "user1")
                        .requestAttr("orgTags", "default")
                        .requestAttr("role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ======================== GET /uploads ========================

    @Test
    @DisplayName("getUserUploadedFiles - 返回用户上传的文件列表")
    void getUserUploadedFiles_ReturnsUserFiles() throws Exception {
        given(documentService.getUserUploadedFiles("user1"))
                .willReturn(Arrays.asList(mockFile));
        given(organizationTagRepository.findByTagId(anyString())).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/documents/uploads")
                        .requestAttr("userId", "user1")
                        .requestAttr("orgTags", "default")
                        .requestAttr("role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
