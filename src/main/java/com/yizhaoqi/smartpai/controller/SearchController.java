package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.HybridSearchService;
import com.yizhaoqi.smartpai.utils.LogUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.yizhaoqi.smartpai.entity.SearchResult;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

// 提供混合检索接口
@Tag(name = "搜索管理", description = "搜索相关接口，提供基于向量与关键词的混合检索功能")
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    @Autowired
    private HybridSearchService hybridSearchService;

    /**
     * 混合检索接口
     * 
     * URL: /api/v1/search/hybrid
     * Method: GET
     * Parameters:
     *   - query: 搜索查询字符串（必需）
     *   - topK: 返回结果数量（可选，默认10）
     * 
     * 示例: /api/v1/search/hybrid?query=人工智能的发展&topK=10
     * 
     * Response:
     * [
     *   {
     *     "fileMd5": "abc123...",
     *     "chunkId": 1,
     *     "textContent": "人工智能是未来科技发展的核心方向。",
     *     "score": 0.92,
     *     "userId": "user123",
     *     "orgTag": "TECH_DEPT",
     *     "isPublic": true
     *   }
     * ]
     */
    @Operation(summary = "混合检索", description = "基于向量相似度和关键词的混合检索，返回最相关的文档片段")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "检索成功"),
        @ApiResponse(responseCode = "500", description = "检索失败，服务器内部错误")
    })
    @GetMapping("/hybrid")
    public Map<String, Object> hybridSearch(@RequestParam String query,
                                            @RequestParam(defaultValue = "10") int topK,
                                            @RequestAttribute(value = "userId", required = false) String userId) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("HYBRID_SEARCH");
        try {
            LogUtils.logBusiness("HYBRID_SEARCH", userId != null ? userId : "anonymous", 
                    "开始混合检索: query=%s, topK=%d", query, topK);
            
            List<SearchResult> results;
            if (userId != null) {
                // 如果有用户ID，使用带权限的搜索
                results = hybridSearchService.searchWithPermission(query, userId, topK);
            } else {
                // 如果没有用户ID，使用普通搜索（仅公开内容）
                results = hybridSearchService.search(query, topK);
            }
            
            LogUtils.logUserOperation(userId != null ? userId : "anonymous", "HYBRID_SEARCH", 
                    "search_query", "SUCCESS");
            LogUtils.logBusiness("HYBRID_SEARCH", userId != null ? userId : "anonymous", 
                    "混合检索完成: 返回结果数量=%d", results.size());
            monitor.end("混合检索成功");
            
            // 构造统一响应结构
            Map<String, Object> responseBody = new HashMap<>(4);
            responseBody.put("code", 200);
            responseBody.put("message", "success");
            responseBody.put("data", results);
            
            return responseBody;
        } catch (Exception e) {
            LogUtils.logBusinessError("HYBRID_SEARCH", userId != null ? userId : "anonymous", 
                    "混合检索失败: query=%s", e, query);
            monitor.end("混合检索失败: " + e.getMessage());
            
            // 构造错误响应结构，保持与前端解析一致
            Map<String, Object> errorBody = new HashMap<>(4);
            errorBody.put("code", 500);
            errorBody.put("message", e.getMessage());
            errorBody.put("data", Collections.emptyList());
            return errorBody;
        }
    }
}
