package com.hermanli.careerpilot.config;

import com.hermanli.careerpilot.identity.AuthenticationInterceptor;
import com.hermanli.careerpilot.identity.CurrentUserIdArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class AuthenticationWebConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;
    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;

    public AuthenticationWebConfig(
            AuthenticationInterceptor authenticationInterceptor,
            CurrentUserIdArgumentResolver currentUserIdArgumentResolver
    ) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.currentUserIdArgumentResolver = currentUserIdArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns("/users/**", "/resumes/**", "/job-descriptions/**", "/analyses/**", "/plans/**",
                        "/interview-sessions/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdArgumentResolver);
    }
}
