package com.shipyard.backend.config;

import com.shipyard.backend.auth.AuthInterceptor;
import com.shipyard.backend.observability.ApiTraceInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ApiTraceInterceptor apiTraceInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, ApiTraceInterceptor apiTraceInterceptor) {
        this.authInterceptor = authInterceptor;
        this.apiTraceInterceptor = apiTraceInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiTraceInterceptor);
        registry.addInterceptor(authInterceptor);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*");
    }
}
