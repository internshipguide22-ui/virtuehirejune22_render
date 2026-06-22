package com.virtuehire.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register interceptor but exclude public endpoints and auth endpoints
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/public/**",
                        "/api/hrs/login",
                        "/api/hrs/register",
                        "/api/hrs/verify-email",
                        "/api/candidates/login",
                        "/api/candidates/register",
                        "/api/candidates/verify-otp",
                        "/api/candidates/verify-email",
                        "/api/candidates/resend-otp",
                        "/api/candidates/forgot-password",
                        "/api/candidates/reset-password",
                        "/api/candidates/resumes/draft/pdf",
                        "/api/candidates/file/**",

// ── BEFORE ────────────────────────────────────────
// "/api/candidates/me/resume" was NOT excluded here,
// so the JwtInterceptor blocked the request before it
// reached CandidateRestController, causing 401/404.
// Same problem for profile-pic and hrs/file endpoints.
// ── AFTER ─────────────────────────────────────────
// These session-based endpoints use HttpSession auth,
// NOT JWT — so they must bypass the JWT interceptor.
"/api/candidates/me/resume",
"/api/candidates/me/profile-pic",
"/api/hrs/file/**"
);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "https://admin.virtuehire.in",
                        "https://virtuehire.in",
                        "https://www.virtuehire.in",
                        "https://backend.virtuehire.in",
                        "http://localhost:3000",
                        "http://localhost:3001"
                )
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
