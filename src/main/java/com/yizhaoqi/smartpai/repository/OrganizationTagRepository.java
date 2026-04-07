package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.OrganizationTag;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationTagRepository extends JpaRepository<OrganizationTag, String> {
    
    @EntityGraph(value = "OrganizationTag.withCreatedBy", type = EntityGraph.EntityGraphType.LOAD)
    Optional<OrganizationTag> findByTagId(String tagId);
    
    List<OrganizationTag> findByParentTag(String parentTag);
    
    boolean existsByTagId(String tagId);
} 