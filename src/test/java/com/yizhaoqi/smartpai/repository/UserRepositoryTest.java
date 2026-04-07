package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.utils.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * UserRepository 数据库集成测试
 * 使用 @DataJpaTest + H2 内存数据库
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@DisplayName("UserRepository 数据库测试")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(PasswordUtil.encode("admin123"));
        admin.setRole(User.Role.ADMIN);
        admin.setOrgTags("default,admin");
        admin.setPrimaryOrg("default");

        User normalUser = new User();
        normalUser.setUsername("testuser");
        normalUser.setPassword(PasswordUtil.encode("password123"));
        normalUser.setRole(User.Role.USER);
        normalUser.setOrgTags("default");
        normalUser.setPrimaryOrg("default");

        userRepository.save(admin);
        userRepository.save(normalUser);
    }

    @Test
    @DisplayName("findByUsername - 存在的用户名返回 Optional<User>")
    void findByUsername_ExistingUser_ReturnsUser() {
        Optional<User> result = userRepository.findByUsername("admin");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("admin");
        assertThat(result.get().getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    @DisplayName("findByUsername - 不存在的用户名返回 empty")
    void findByUsername_NotExists_ReturnsEmpty() {
        Optional<User> result = userRepository.findByUsername("ghost");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("save - 保存新用户并能通过 username 查询到")
    void save_NewUser_CanBeFoundByUsername() {
        User newUser = new User();
        newUser.setUsername("newuser");
        newUser.setPassword(PasswordUtil.encode("newpass"));
        newUser.setRole(User.Role.USER);
        newUser.setOrgTags("default");
        newUser.setPrimaryOrg("default");

        userRepository.save(newUser);

        Optional<User> found = userRepository.findByUsername("newuser");
        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(User.Role.USER);
    }

    @Test
    @DisplayName("save - 用户名重复时抛出异常（唯一约束）")
    void save_DuplicateUsername_ThrowsException() {
        User duplicate = new User();
        duplicate.setUsername("admin"); // 已存在
        duplicate.setPassword(PasswordUtil.encode("pass"));
        duplicate.setRole(User.Role.USER);
        duplicate.setOrgTags("default");
        duplicate.setPrimaryOrg("default");

        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(duplicate);
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("findById - 通过 id 查询用户")
    void findById_ExistingId_ReturnsUser() {
        User saved = userRepository.findByUsername("testuser").orElseThrow();

        Optional<User> result = userRepository.findById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("delete - 删除用户后无法查询到")
    void delete_User_CannotBeFoundAfterDeletion() {
        User user = userRepository.findByUsername("testuser").orElseThrow();
        userRepository.delete(user);

        Optional<User> result = userRepository.findByUsername("testuser");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("count - 返回正确的用户总数")
    void count_ReturnsCorrectTotal() {
        long count = userRepository.count();

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("findByUsername - 用户 orgTags 和 primaryOrg 字段正确持久化")
    void findByUsername_OrgTagsAndPrimaryOrg_PersistCorrectly() {
        Optional<User> result = userRepository.findByUsername("admin");

        assertThat(result).isPresent();
        assertThat(result.get().getOrgTags()).contains("default");
        assertThat(result.get().getPrimaryOrg()).isEqualTo("default");
    }
}
