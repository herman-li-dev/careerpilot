package com.hermanli.careerpilot.publicrag;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "careerpilot.public-rag.auth.enabled", havingValue = "true")
public class PublicRagAuthenticationWebConfig implements WebMvcConfigurer {

    private final PublicRagAuthenticationInterceptor authenticationInterceptor;
    private final PublicRagSecurityAuditInterceptor securityAuditInterceptor;
    private final boolean clerkApplicationAuthenticationEnabled;

    public PublicRagAuthenticationWebConfig(
            @Qualifier("publicRagAuthenticationInterceptor")
            PublicRagAuthenticationInterceptor authenticationInterceptor,
            @Qualifier("publicRagSecurityAuditInterceptor")
            PublicRagSecurityAuditInterceptor securityAuditInterceptor,
            @Value("${careerpilot.auth.clerk-application-enabled:false}")
            boolean clerkApplicationAuthenticationEnabled
    ) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.securityAuditInterceptor = securityAuditInterceptor;
        this.clerkApplicationAuthenticationEnabled = clerkApplicationAuthenticationEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(securityAuditInterceptor)
                .addPathPatterns("/rag/**")
                .order(0);
        var authenticationRegistration = registry.addInterceptor(authenticationInterceptor).order(1);
        if (clerkApplicationAuthenticationEnabled) {
            authenticationRegistration.addPathPatterns(
                    "/users/**",
                    "/resumes/**",
                    "/job-descriptions/**",
                    "/analyses/**",
                    "/plans/**",
                    "/interview-sessions/**",
                    "/rag/**"
            );
        } else {
            authenticationRegistration.addPathPatterns("/rag/**");
        }
    }
}
