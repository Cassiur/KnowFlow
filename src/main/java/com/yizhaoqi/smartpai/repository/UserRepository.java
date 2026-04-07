package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * 根据用户名查询用户信息（带缓存）
     * 缓存名称: user
     * 缓存键: username
     */
    @Cacheable(value = "user", key = "#username")
    Optional<User> findByUsername(String username);
}
