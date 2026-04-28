package com.agrimarket.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcInterceptorsConfig implements WebMvcConfigurer {

    private final ProviderSubscriptionGateInterceptor providerSubscriptionGateInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(providerSubscriptionGateInterceptor);
    }
}

