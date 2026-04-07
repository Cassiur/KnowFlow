package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * DocumentService 单元测试
 * 使用 Mockito mock 所有外部依赖（Repository、MinIO、ES、Redis）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService 单元测试")
class DocumentServiceTest {

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private DocumentVectorRepository documentVectorRepository;

    @Mock
    private MinioClient minioClient;

    @Mock
    private ElasticsearchService elasticsearchService;

    @Mock
    private OrgTagCacheService orgTagCacheService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DocumentService documentService;

    private FileUpload mockFileUpload;
    private User mockUser;

    @BeforeEach
    void setUp() {
        mockFileUpload = new FileUpload();
        mockFileUpload.setFileMd5("abc123");
        mockFileUpload.setFileName("test.pdf");
        mockFileUpload.setUserId("user1");
        mockFileUpload.setTotalSize(1024L);

        mockUser = new User();
        mockUser.setUsername("user1");
        mockUser.setOrgTags("default");
    }

    // ======================== deleteDocument 测试 ========================

    @Test
    @DisplayName("deleteDocument - 正常删除文档，依次清理 ES、MinIO、向量、DB 记录")
    void deleteDocument_Success() throws Exception {
        given(fileUploadRepository.findByFileMd5AndUserId("abc123", "user1"))
                .willReturn(Optional.of(mockFileUpload));
        willDoNothing().given(elasticsearchService).deleteByFileMd5(anyString());
        willDoNothing().given(minioClient).removeObject(any(RemoveObjectArgs.class));
        willDoNothing().given(documentVectorRepository).deleteByFileMd5(anyString());
        willDoNothing().given(fileUploadRepository).deleteByFileMd5(anyString());

        assertThatCode(() -> documentService.deleteDocument("abc123", "user1"))
                .doesNotThrowAnyException();

        then(elasticsearchService).should().deleteByFileMd5("abc123");
        then(documentVectorRepository).should().deleteByFileMd5("abc123");
        then(fileUploadRepository).should().deleteByFileMd5("abc123");
    }

    @Test
    @DisplayName("deleteDocument - 文件不存在时抛出 RuntimeException")
    void deleteDocument_FileNotFound_ThrowsException() {
        given(fileUploadRepository.findByFileMd5AndUserId("notExist", "user1"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.deleteDocument("notExist", "user1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("删除文档失败");
    }

    @Test
    @DisplayName("deleteDocument - ES 删除异常时仍继续删除其他数据")
    void deleteDocument_EsThrows_StillDeletesOthers() throws Exception {
        given(fileUploadRepository.findByFileMd5AndUserId("abc123", "user1"))
                .willReturn(Optional.of(mockFileUpload));
        willThrow(new RuntimeException("ES error")).given(elasticsearchService).deleteByFileMd5(anyString());
        willDoNothing().given(minioClient).removeObject(any(RemoveObjectArgs.class));
        willDoNothing().given(documentVectorRepository).deleteByFileMd5(anyString());
        willDoNothing().given(fileUploadRepository).deleteByFileMd5(anyString());

        assertThatCode(() -> documentService.deleteDocument("abc123", "user1"))
                .doesNotThrowAnyException();

        // 即使 ES 失败，DB 记录仍应被删除
        then(fileUploadRepository).should().deleteByFileMd5("abc123");
    }

    // ======================== getUserUploadedFiles 测试 ========================

    @Test
    @DisplayName("getUserUploadedFiles - 返回指定用户的文件列表")
    void getUserUploadedFiles_ReturnsUserFiles() {
        FileUpload f1 = new FileUpload();
        f1.setFileMd5("md5-1");
        f1.setUserId("user1");

        FileUpload f2 = new FileUpload();
        f2.setFileMd5("md5-2");
        f2.setUserId("user1");

        given(fileUploadRepository.findByUserId("user1")).willReturn(Arrays.asList(f1, f2));

        List<FileUpload> result = documentService.getUserUploadedFiles("user1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FileUpload::getUserId).containsOnly("user1");
    }

    @Test
    @DisplayName("getUserUploadedFiles - 用户无文件时返回空列表")
    void getUserUploadedFiles_NoFiles_ReturnsEmptyList() {
        given(fileUploadRepository.findByUserId("user1")).willReturn(Collections.emptyList());

        List<FileUpload> result = documentService.getUserUploadedFiles("user1");

        assertThat(result).isEmpty();
    }

    // ======================== getAccessibleFiles 测试 ========================

    @Test
    @DisplayName("getAccessibleFiles - 用户有组织标签时使用层级标签查询")
    void getAccessibleFiles_WithOrgTags_UsesTagQuery() {
        given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser));
        given(orgTagCacheService.getUserEffectiveOrgTags("user1"))
                .willReturn(Arrays.asList("default", "team-a"));
        given(fileUploadRepository.findAccessibleFilesWithTags(eq("user1"), anyList()))
                .willReturn(Arrays.asList(mockFileUpload));

        List<FileUpload> result = documentService.getAccessibleFiles("user1", "default");

        assertThat(result).hasSize(1);
        then(fileUploadRepository).should().findAccessibleFilesWithTags(eq("user1"), anyList());
    }

    @Test
    @DisplayName("getAccessibleFiles - 用户无组织标签时仅返回个人和公开文件")
    void getAccessibleFiles_NoOrgTags_UsesPublicQuery() {
        given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser));
        given(orgTagCacheService.getUserEffectiveOrgTags("user1"))
                .willReturn(Collections.emptyList());
        given(fileUploadRepository.findByUserIdOrIsPublicTrue("user1"))
                .willReturn(Arrays.asList(mockFileUpload));

        List<FileUpload> result = documentService.getAccessibleFiles("user1", "");

        assertThat(result).hasSize(1);
        then(fileUploadRepository).should().findByUserIdOrIsPublicTrue("user1");
    }

    @Test
    @DisplayName("getAccessibleFiles - 用户不存在时抛出 RuntimeException")
    void getAccessibleFiles_UserNotFound_ThrowsException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getAccessibleFiles("ghost", ""))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("获取可访问文件列表失败");
    }

    // ======================== generateDownloadUrl 测试 ========================

    @Test
    @DisplayName("generateDownloadUrl - 文件不存在时返回 null")
    void generateDownloadUrl_FileNotFound_ReturnsNull() {
        given(fileUploadRepository.findByFileMd5("notExist")).willReturn(Optional.empty());

        String url = documentService.generateDownloadUrl("notExist");

        assertThat(url).isNull();
    }
}
