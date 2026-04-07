package com.yizhaoqi.smartpai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) 配置类
 * 配置API文档元数据、JWT Bearer认证方案和全局安全要求
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Bean
    public OpenAPI knowFlowOpenAPI() {
        return new OpenAPI()
                // API 基本信息
                .info(new Info()
                        .title("KnowFlow API")
                        .version("1.0")
                        .description("企业级AI知识库管理系统API文档")
                        .contact(new Contact()
                                .name("KnowFlow Team")
                                .email("support@knowflow.com")
                                .url("https://knowflow.com")))
                // 配置 JWT Bearer 认证方案
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, 
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("请输入JWT令牌进行认证")))
                // 全局安全要求 - 所有接口默认需要认证
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
