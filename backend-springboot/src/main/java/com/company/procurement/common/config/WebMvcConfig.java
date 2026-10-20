package com.company.procurement.common.config;

import com.company.procurement.common.security.AuthInterceptor;
import com.company.procurement.common.security.DataScopeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final DataScopeInterceptor dataScopeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/auth/login",
                "/api/public/ehs/**",
                "/api/procurement/ai/images/**",
                "/api/integrations/feishu-bot/**",
                "/api/integrations/shopping/**",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/error"
            );

        registry.addInterceptor(dataScopeInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/auth/login",
                "/api/public/ehs/**",
                "/api/procurement/ai/images/**",
                "/api/integrations/feishu-bot/**",
                "/api/integrations/shopping/**",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/error"
            );
    }
}
