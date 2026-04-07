package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.OrganizationTag;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

/**
 * OrgTagCacheService 单元测试
 * mock RedisTemplate 和 OrganizationTagRepository
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrgTagCacheService 单元测试")
class OrgTagCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private OrganizationTagRepository organizationTagRepository;

    @Mock
    private ListOperations<String, Object> listOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private OrgTagCacheService orgTagCacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ======================== cacheUserOrgTags 测试 ========================

    @Test
    @DisplayName("cacheUserOrgTags - 正常缓存组织标签列表")
    void cacheUserOrgTags_Success() {
        List<String> tags = Arrays.asList("default", "team-a");
        given(listOperations.rightPushAll(anyString(), any(Object[].class))).willReturn(2L);
        given(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).willReturn(true);

        assertThatCode(() -> orgTagCacheService.cacheUserOrgTags("user1", tags))
                .doesNotThrowAnyException();

        then(listOperations).should().rightPushAll(eq("user:org_tags:user1"), any(Object[].class));
        then(redisTemplate).should().expire(eq("user:org_tags:user1"), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("cacheUserOrgTags - Redis 异常时不抛出，静默处理")
    void cacheUserOrgTags_RedisException_SilentFail() {
        given(listOperations.rightPushAll(anyString(), any(Object[].class)))
                .willThrow(new RuntimeException("Redis connection failed"));

        assertThatCode(() -> orgTagCacheService.cacheUserOrgTags("user1", Arrays.asList("default")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cacheUserPrimaryOrg - Redis 异常时不抛出，静默处理")
    void cacheUserPrimaryOrg_RedisException_SilentFail() {
        // ① Arrange：让 valueOperations.set() 抛出异常
        willThrow(new RuntimeException("Redis connection failed"))
                .given(valueOperations).set(anyString(), any());

        // ② Act + Assert：调用方法，验证没有异常往外抛
        assertThatCode(() -> orgTagCacheService.cacheUserPrimaryOrg("user1", "default"))
                .doesNotThrowAnyException();
    }

    // ======================== getUserOrgTags 测试 ========================

    @Test
    @DisplayName("getUserOrgTags - 缓存命中时返回标签列表")
    void getUserOrgTags_CacheHit_ReturnsTags() {
        given(listOperations.range(eq("user:org_tags:user1"), eq(0L), eq(-1L)))
                .willReturn(Arrays.asList("default", "team-a"));

        List<String> result = orgTagCacheService.getUserOrgTags("user1");

        assertThat(result).containsExactlyInAnyOrder("default", "team-a");
    }

    @Test
    @DisplayName("getUserOrgTags - 缓存未命中时返回 null")
    void getUserOrgTags_CacheMiss_ReturnsNull() {
        given(listOperations.range(eq("user:org_tags:user1"), eq(0L), eq(-1L)))
                .willReturn(Collections.emptyList());

        List<String> result = orgTagCacheService.getUserOrgTags("user1");

        assertThat(result).isNull();
    }

    // ======================== cacheUserPrimaryOrg 测试 ========================

    @Test
    @DisplayName("cacheUserPrimaryOrg - 正常缓存主组织标签")
    void cacheUserPrimaryOrg_Success() {
        willDoNothing().given(valueOperations).set(anyString(), any());
        given(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).willReturn(true);

        assertThatCode(() -> orgTagCacheService.cacheUserPrimaryOrg("user1", "default"))
                .doesNotThrowAnyException();

        then(valueOperations).should().set("user:primary_org:user1", "default");
    }

    @Test
    @DisplayName("getUserPrimaryOrg - 返回缓存的主组织")
    void getUserPrimaryOrg_ReturnsValue() {
        given(valueOperations.get("user:primary_org:user1")).willReturn("default");

        String result = orgTagCacheService.getUserPrimaryOrg("user1");

        assertThat(result).isEqualTo("default");
    }

    @Test
    @DisplayName("getUserPrimaryOrg - 缓存未命中时返回 null")
    void getUserPrimaryOrg_CacheMiss_ReturnsNull() {
        given(valueOperations.get("user:primary_org:user1")).willReturn(null);

        String result = orgTagCacheService.getUserPrimaryOrg("user1");

        assertThat(result).isNull();
    }

    // ======================== deleteUserOrgTagsCache 测试 ========================

    @Test
    @DisplayName("deleteUserOrgTagsCache - 删除用户的 org_tags 和 primary_org 两个 key")
    void deleteUserOrgTagsCache_DeletesBothKeys() {
        given(redisTemplate.delete(anyString())).willReturn(true);

        orgTagCacheService.deleteUserOrgTagsCache("user1");

        then(redisTemplate).should().delete("user:org_tags:user1");
        then(redisTemplate).should().delete("user:primary_org:user1");
    }

    // ======================== getUserEffectiveOrgTags 测试 ========================

    @Test
    @DisplayName("getUserEffectiveOrgTags - 缓存命中时直接返回，并补充 DEFAULT 标签")
    void getUserEffectiveOrgTags_CacheHit_ReturnsWithDefault() {
        given(listOperations.range(eq("user:effective_org_tags:user1"), eq(0L), eq(-1L)))
                .willReturn(Arrays.asList("team-a", "DEFAULT"));

        List<String> result = orgTagCacheService.getUserEffectiveOrgTags("user1");

        assertThat(result).contains("DEFAULT", "team-a");
    }

    @Test
    @DisplayName("getUserEffectiveOrgTags - 缓存命中但缺少 DEFAULT 时自动添加")
    void getUserEffectiveOrgTags_CacheHit_MissingDefault_AddsDefault() {
        given(listOperations.range(eq("user:effective_org_tags:user1"), eq(0L), eq(-1L)))
                .willReturn(Arrays.asList("team-a"));

        List<String> result = orgTagCacheService.getUserEffectiveOrgTags("user1");

        assertThat(result).contains("DEFAULT");
    }

    @Test
    @DisplayName("getUserEffectiveOrgTags - 缓存未命中时计算并包含 DEFAULT")
    void getUserEffectiveOrgTags_CacheMiss_ComputesAndIncludesDefault() {
        // effective_tags 缓存未命中
        given(listOperations.range(eq("user:effective_org_tags:user1"), eq(0L), eq(-1L)))
                .willReturn(Collections.emptyList());
        // org_tags 缓存未命中
        given(listOperations.range(eq("user:org_tags:user1"), eq(0L), eq(-1L)))
                .willReturn(Collections.emptyList());
        given(listOperations.rightPushAll(anyString(), any(Object[].class))).willReturn(1L);
        given(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).willReturn(true);

        List<String> result = orgTagCacheService.getUserEffectiveOrgTags("user1");

        assertThat(result).contains("DEFAULT");
    }

    @Test
    @DisplayName("getUserEffectiveOrgTags - 用户有标签时递归收集父标签")
    void getUserEffectiveOrgTags_WithParentTags_CollectsParents() {
        // effective_tags 缓存未命中
        given(listOperations.range(eq("user:effective_org_tags:user1"), eq(0L), eq(-1L)))
                .willReturn(Collections.emptyList());
        // org_tags: 用户有 child-tag
        given(listOperations.range(eq("user:org_tags:user1"), eq(0L), eq(-1L)))
                .willReturn(Collections.singletonList("child-tag"));

        OrganizationTag childTag = new OrganizationTag();
        childTag.setTagId("child-tag");
        childTag.setParentTag("parent-tag");

        OrganizationTag parentTag = new OrganizationTag();
        parentTag.setTagId("parent-tag");
        parentTag.setParentTag(null);

        given(organizationTagRepository.findByTagId("child-tag")).willReturn(Optional.of(childTag));
        given(organizationTagRepository.findByTagId("parent-tag")).willReturn(Optional.of(parentTag));
        given(listOperations.rightPushAll(anyString(), any(Object[].class))).willReturn(3L);
        given(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).willReturn(true);

        List<String> result = orgTagCacheService.getUserEffectiveOrgTags("user1");

        assertThat(result).contains("child-tag", "parent-tag", "DEFAULT");
    }

    @Test
    @DisplayName("getUserEffectiveOrgTags - Redis 异常时返回至少包含 DEFAULT 的列表")
    void getUserEffectiveOrgTags_Exception_ReturnsDefault() {
        given(listOperations.range(anyString(), anyLong(), anyLong()))
                .willThrow(new RuntimeException("Redis down"));

        List<String> result = orgTagCacheService.getUserEffectiveOrgTags("user1");

        assertThat(result).containsExactly("DEFAULT");
    }

    // ======================== deleteUserEffectiveTagsCache 测试 ========================

    @Test
    @DisplayName("deleteUserEffectiveTagsCache - 删除指定用户的有效标签缓存")
    void deleteUserEffectiveTagsCache_Success() {
        given(redisTemplate.delete(anyString())).willReturn(true);

        assertThatCode(() -> orgTagCacheService.deleteUserEffectiveTagsCache("user1"))
                .doesNotThrowAnyException();

        then(redisTemplate).should().delete("user:effective_org_tags:user1");
    }
}
