package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * FileUploadRepository 数据库集成测试
 * 使用 @DataJpaTest + H2 内存数据库，测试 JPA 查询方法
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@DisplayName("FileUploadRepository 数据库测试")
class FileUploadRepositoryTest {

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private UserRepository userRepository;

    private FileUpload publicFile;
    private FileUpload privateFile;
    private FileUpload orgFile;

    @BeforeEach
    void setUp() {
        fileUploadRepository.deleteAll();

        publicFile = new FileUpload();
        publicFile.setFileMd5("md5-public");
        publicFile.setFileName("public.pdf");
        publicFile.setUserId("user1");
        publicFile.setPublic(true);
        publicFile.setOrgTag("default");
        publicFile.setStatus(1);
        publicFile.setTotalSize(1024L);

        privateFile = new FileUpload();
        privateFile.setFileMd5("md5-private");
        privateFile.setFileName("private.pdf");
        privateFile.setUserId("user1");
        privateFile.setPublic(false);
        privateFile.setOrgTag("default");
        privateFile.setStatus(1);
        privateFile.setTotalSize(2048L);

        orgFile = new FileUpload();
        orgFile.setFileMd5("md5-org");
        orgFile.setFileName("org.pdf");
        orgFile.setUserId("user2");
        orgFile.setPublic(false);
        orgFile.setOrgTag("team-a");
        orgFile.setStatus(1);
        orgFile.setTotalSize(4096L);

        fileUploadRepository.saveAll(Arrays.asList(publicFile, privateFile, orgFile));
    }

    @Test
    @DisplayName("findByFileMd5 - 存在的 MD5 返回文件")
    void findByFileMd5_Exists_ReturnsFile() {
        Optional<FileUpload> result = fileUploadRepository.findByFileMd5("md5-public");

        assertThat(result).isPresent();
        assertThat(result.get().getFileName()).isEqualTo("public.pdf");
    }

    @Test
    @DisplayName("findByFileMd5 - 不存在的 MD5 返回 empty")
    void findByFileMd5_NotExists_ReturnsEmpty() {
        Optional<FileUpload> result = fileUploadRepository.findByFileMd5("not-exist");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByFileMd5AndUserId - MD5 + userId 匹配时返回文件")
    void findByFileMd5AndUserId_Match_ReturnsFile() {
        Optional<FileUpload> result = fileUploadRepository.findByFileMd5AndUserId("md5-private", "user1");

        assertThat(result).isPresent();
        assertThat(result.get().getFileName()).isEqualTo("private.pdf");
    }

    @Test
    @DisplayName("findByFileMd5AndUserId - userId 不匹配时返回 empty")
    void findByFileMd5AndUserId_WrongUser_ReturnsEmpty() {
        Optional<FileUpload> result = fileUploadRepository.findByFileMd5AndUserId("md5-private", "user2");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByFileNameAndIsPublicTrue - 返回公开文件")
    void findByFileNameAndIsPublicTrue_PublicFile_ReturnsFile() {
        Optional<FileUpload> result = fileUploadRepository.findByFileNameAndIsPublicTrue("public.pdf");

        assertThat(result).isPresent();
        assertThat(result.get().isPublic()).isTrue();
    }

    @Test
    @DisplayName("findByFileNameAndIsPublicTrue - 私有文件返回 empty")
    void findByFileNameAndIsPublicTrue_PrivateFile_ReturnsEmpty() {
        Optional<FileUpload> result = fileUploadRepository.findByFileNameAndIsPublicTrue("private.pdf");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("countByFileMd5 - 返回对应 MD5 的文件数量")
    void countByFileMd5_ReturnsCount() {
        long count = fileUploadRepository.countByFileMd5("md5-public");

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("findByUserId - 返回指定用户的所有文件")
    void findByUserId_ReturnsUserFiles() {
        List<FileUpload> result = fileUploadRepository.findByUserId("user1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FileUpload::getUserId).containsOnly("user1");
    }

    @Test
    @DisplayName("findByUserIdOrIsPublicTrue - 返回用户文件 + 所有公开文件")
    void findByUserIdOrIsPublicTrue_ReturnsUserAndPublic() {
        // user2 的文件：private file(user1) + org file(user2) + public file(user1)
        List<FileUpload> result = fileUploadRepository.findByUserIdOrIsPublicTrue("user2");

        // 应包含 orgFile(user2自己的) + publicFile(公开)
        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result).anyMatch(f -> f.getFileMd5().equals("md5-public"));
        assertThat(result).anyMatch(f -> f.getFileMd5().equals("md5-org"));
    }

    @Test
    @DisplayName("deleteByFileMd5 - 删除指定 MD5 的文件记录")
    void deleteByFileMd5_DeletesRecord() {
        fileUploadRepository.deleteByFileMd5("md5-public");

        Optional<FileUpload> result = fileUploadRepository.findByFileMd5("md5-public");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deleteByFileMd5AndUserId - 只删除指定用户的文件")
    void deleteByFileMd5AndUserId_DeletesOnlyMatchingRecord() {
        fileUploadRepository.deleteByFileMd5AndUserId("md5-private", "user1");

        assertThat(fileUploadRepository.findByFileMd5("md5-private")).isEmpty();
        // 其他文件不受影响
        assertThat(fileUploadRepository.findByFileMd5("md5-public")).isPresent();
    }

    @Test
    @DisplayName("findAccessibleFilesWithTags - 组织标签匹配时返回文件")
    void findAccessibleFilesWithTags_MatchingTag_ReturnsFiles() {
        List<FileUpload> result = fileUploadRepository.findAccessibleFilesWithTags(
                "user2", Arrays.asList("team-a", "default"));

        assertThat(result).isNotEmpty();
        assertThat(result).anyMatch(f -> f.getOrgTag().equals("team-a"));
    }
}
