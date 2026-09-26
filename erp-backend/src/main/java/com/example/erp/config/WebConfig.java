package com.example.erp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import com.example.erp.users.ApiAccess;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ApiAccess access;

    public WebConfig(ApiAccess access) { this.access = access; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(access).addPathPatterns("/api/v1/accounting/**", "/api/v1/hr/**",
            "/api/v1/planning/**", "/api/v1/website/**", "/api/v1/marketing/**", "/api/v1/dashboard/**",
            "/api/v1/users/**", "/api/v1/roles/**", "/api/v1/auth/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("http://localhost:4200", "http://localhost:4201").allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }
}
