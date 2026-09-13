package com.storemanager.catalog.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Without this, the browser blocks every request from the React dev server
 * (localhost:5173) to this API (localhost:8080) as cross-origin — {@code
 * curl} doesn't hit this at all, since CORS is enforced by the browser, not
 * the server, which is exactly why this was missed until the PR5 E2E check.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                .addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("*");
    }
}
