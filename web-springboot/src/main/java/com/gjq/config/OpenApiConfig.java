package com.gjq.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("法律咨询智能问答系统API")
                        .version("1.0.0")
                        .description("基于RAG的法律咨询智能问答系统后端接口文档")
                        .contact(new Contact()
                                .name("开发团队")
                                .email("support@example.com")));
    }
}
