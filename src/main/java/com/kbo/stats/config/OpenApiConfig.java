package com.kbo.stats.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("KBO 선수 기록 검색 시스템 API")
                        .description("KBO 선수 데이터 조회, 자동 크롤링, AI 챗봇 기능을 제공하는 백엔드 API. " +
                                "RAG 패턴과 Claude API를 활용한 자연어 질의 응답을 지원합니다.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("배하선")
                                .email("")));
    }
}
