package com.personal.assistant.common.config;

import com.personal.assistant.common.security.JwtProperties;
import com.personal.assistant.module.tradingreview.provider.IfindProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用级通用配置：开启配置属性绑定、OpenAPI 文档信息。
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, IfindProperties.class})
public class AppConfig {

    @Bean
    public OpenAPI personalAssistantOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("个人辅助系统 API")
                .description("个人生活、工作、学习、提醒、股票关注综合辅助系统接口文档")
                .version("v0.0.1"));
    }
}
