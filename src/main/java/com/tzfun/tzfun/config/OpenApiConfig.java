package com.tzfun.tzfun.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置：自动扫描所有 @RestController，
 * 生成 /v3/api-docs（OpenAPI 3.1 规范）并提供 /swagger-ui.html 交互页面。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tzFunOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TzFun 租车平台 API")
                        .description("TzFun 自营租车平台后端接口文档（Spring Boot 4 + SpringDoc OpenAPI 3）")
                        .version("v1.0.0"));
    }
}
