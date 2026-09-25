package com.hermanli.careerpilot.config;

import com.hermanli.careerpilot.identity.AuthenticationInterceptor;
import com.hermanli.careerpilot.identity.CurrentUserIdArgumentResolver;
import com.hermanli.careerpilot.identity.LegacyAuthenticationDisabledInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class AuthenticationWebConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;
    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;
    private final LegacyAuthenticationDisabledInterceptor legacyAuthenticationDisabledInterceptor;
    private final boolean clerkApplicationAuthenticationEnabled;

    public AuthenticationWebConfig(
            AuthenticationInterceptor authenticationInterceptor,
            CurrentUserIdArgumentResolver currentUserIdArgumentResolver,
            LegacyAuthenticationDisabledInterceptor legacyAuthenticationDisabledInterceptor,
            @Value("${careerpilot.auth.clerk-application-enabled:false}")
            boolean clerkApplicationAuthenticationEnabled
    ) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.currentUserIdArgumentResolver = currentUserIdArgumentResolver;
        this.legacyAuthenticationDisabledInterceptor = legacyAuthenticationDisabledInterceptor;
        this.clerkApplicationAuthenticationEnabled = clerkApplicationAuthenticationEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (clerkApplicationAuthenticationEnabled) {
            registry.addInterceptor(legacyAuthenticationDisabledInterceptor)
                    .addPathPatterns("/auth/register", "/auth/login", "/auth/demo-login", "/auth/logout");
            return;
        }
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns("/users/**", "/resumes/**", "/job-descriptions/**", "/analyses/**", "/plans/**",
                        "/interview-sessions/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdArgumentResolver);
    }
}
