package com.portfolio.jobcrawler.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String jwtScheme = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("이끼잡 (Job Crawler) API")
                        .description("채용 공고 크롤링, AI 자소서/포트폴리오 생성, 자동 지원 플랫폼 API")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(jwtScheme))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes(jwtScheme,
                                new SecurityScheme()
                                        .name(jwtScheme)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT 토큰을 입력하세요 (Bearer 접두사 불필요)")));
    }
}
